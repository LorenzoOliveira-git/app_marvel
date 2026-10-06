'use strict';
const {createHash} = require('node:crypto');
const {getFirestore, FieldValue} = require('firebase-admin/firestore');
const {HttpsError} = require('firebase-functions/v2/https');
let catalogCache;
function invalid(message) { throw new HttpsError('invalid-argument', message); }
function localIdentity(request) {
  if (process.env.FUNCTIONS_EMULATOR !== 'true') throw new HttpsError('failed-precondition', 'Rascunhos disponíveis somente no ambiente local nesta etapa.');
  if (!request.auth) throw new HttpsError('unauthenticated', 'Entre na sua conta para salvar o rascunho.');
  return request.auth.uid;
}
async function catalog(kind) {
  const key = process.env.COMICVINE_API_KEY;
  if (!key) throw new HttpsError('failed-precondition', 'Configure a ComicVine no ambiente privado do backend local.');
  const rows = []; let offset = 0, total;
  do {
    const url = new URL(`https://comicvine.gamespot.com/api/${kind}/`);
    url.search = new URLSearchParams({api_key: key, format: 'json', field_list: 'id,name', limit: '100', offset: String(offset), sort: 'id:asc'}).toString();
    let data;
    try {
      const response = await fetch(url, {headers: {'User-Agent': 'MarvelMobileLocalDrafts/1.0'}, signal: AbortSignal.timeout(20000)});
      if (!response.ok) throw new Error();
      data = await response.json();
    } catch (_) { throw new HttpsError('unavailable', 'Não foi possível validar o catálogo ComicVine. Tente novamente.', {reason: 'comicvine-unavailable'}); }
    if (data.status_code !== 1 || !Array.isArray(data.results) || !Number.isInteger(data.number_of_total_results) || data.number_of_total_results > 1000 || data.results.length === 0) {
      throw new HttpsError('unavailable', 'Catálogo ComicVine indisponível para validação.', {reason: 'comicvine-unavailable'});
    }
    for (const row of data.results) {
      if (!Number.isInteger(row.id) || row.id <= 0 || typeof row.name !== 'string' || !row.name.trim()) throw new HttpsError('unavailable', 'Resposta do catálogo inválida.', {reason: 'comicvine-unavailable'});
      rows.push({id: row.id, name: row.name.trim()});
    }
    offset += data.results.length; total = data.number_of_total_results;
  } while (offset < total);
  return rows;
}
async function catalogs() {
  if (catalogCache && catalogCache.expiresAt > Date.now()) return catalogCache.value;
  const [origins, powers] = await Promise.all([catalog('origins'), catalog('powers')]);
  const value = {origins, powers}; catalogCache = {value, expiresAt: Date.now() + 3600000}; return value;
}
function input(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) invalid('Dados do rascunho inválidos.');
  const allowed = ['draftId', 'heroName', 'realName', 'description', 'birthday', 'originId', 'powerIds'];
  if (Object.keys(data).some(key => !allowed.includes(key))) invalid('O rascunho contém campos não permitidos.');
  if (typeof data.draftId !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(data.draftId)) invalid('Identificador do rascunho inválido.');
  const normalized = {};
  for (const [name, limit] of [['heroName',100], ['realName',100], ['description',2000]]) {
    if (typeof data[name] !== 'string' || !data[name].trim() || data[name].trim().length > limit) invalid(`Confira ${name}: campo obrigatório, até ${limit} caracteres.`);
    normalized[name] = data[name].trim();
  }
  if (!Number.isInteger(data.originId) || data.originId <= 0) invalid('Escolha uma origem válida.');
  if (!Array.isArray(data.powerIds) || data.powerIds.length < 1 || data.powerIds.length > 128 || data.powerIds.some(id => !Number.isInteger(id) || id <= 0) || new Set(data.powerIds).size !== data.powerIds.length) invalid('Escolha de 1 a 128 poderes válidos, sem repetição.');
  const birthday = data.birthday == null || data.birthday === '' ? null : data.birthday;
  if (birthday !== null) {
    if (typeof birthday !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(birthday) || birthday.startsWith('0000')) invalid('Data de nascimento inválida.');
    const date = new Date(`${birthday}T00:00:00.000Z`);
    if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0,10) !== birthday) invalid('Data de nascimento inválida.');
  }
  return {draftId: data.draftId.toLowerCase(), ...normalized, birthday, originId: data.originId, powerIds: [...data.powerIds].sort((a,b) => a-b)};
}
async function saveHeroDraft(request) {
  const uid = localIdentity(request), draft = input(request.data), source = await catalogs();
  const origin = source.origins.find(value => value.id === draft.originId);
  const powers = draft.powerIds.map(id => source.powers.find(value => value.id === id));
  if (!origin || powers.some(value => !value)) invalid('Origem ou poder ausente no catálogo ComicVine.');
  const {draftId, ...fields} = draft;
  const hash = createHash('sha256').update(JSON.stringify(fields)).digest('hex');
  const ref = getFirestore().doc(`users/${uid}/heroDrafts/${draftId}`);
  await getFirestore().runTransaction(async transaction => {
    const previous = await transaction.get(ref);
    if (previous.exists && previous.get('contentHash') === hash) return;
    transaction.set(ref, {...fields, origin, powers, uid, state: 'draft', generatedImage: false, contentHash: hash, schemaVersion: 1,
      createdAt: previous.exists ? previous.get('createdAt') : FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp()});
  });
  return {uid, draftId, state: 'draft', generatedImage: false};
}
module.exports = {saveHeroDraft, catalogs, normalizeDraft: input};

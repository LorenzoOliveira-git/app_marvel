'use strict';
const {getFirestore, FieldValue} = require('firebase-admin/firestore');
const {HttpsError} = require('firebase-functions/v2/https');
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
async function updateHeroText(request) {
  if (process.env.FUNCTIONS_EMULATOR !== 'true' || process.env.GCLOUD_PROJECT !== 'demo-marvel-local'
      || !/^(127\.0\.0\.1|localhost):\d+$/.test(process.env.FIRESTORE_EMULATOR_HOST || '')) {
    throw new HttpsError('failed-precondition', 'Edição disponível somente no ambiente local.');
  }
  if (!request.auth) throw new HttpsError('unauthenticated', 'Entre na sua conta.');
  const data = request.data, allowed = ['heroId','revision','heroName','realName','description'];
  if (!data || typeof data !== 'object' || Array.isArray(data) || Object.keys(data).some(key => !allowed.includes(key))
      || typeof data.heroId !== 'string' || !UUID.test(data.heroId) || !Number.isSafeInteger(data.revision) || data.revision < 0) {
    throw new HttpsError('invalid-argument', 'Pedido inválido.');
  }
  const fields = {};
  for (const [key,limit] of [['heroName',100],['realName',100],['description',2000]]) {
    if (typeof data[key] !== 'string' || !data[key].trim() || data[key].trim().length > limit) throw new HttpsError('invalid-argument', 'Confira os campos obrigatórios e seus limites.');
    fields[key] = data[key].trim();
  }
  const uid = request.auth.uid, heroId = data.heroId.toLowerCase();
  const ref = getFirestore().doc(`users/${uid}/heroes/${heroId}`);
  const revision = await getFirestore().runTransaction(async tx => {
    const doc = await tx.get(ref);
    if (!doc.exists || doc.get('uid') !== uid || doc.get('operationId') !== heroId) throw new HttpsError('not-found', 'Herói indisponível nesta conta.');
    const current = doc.get('textRevision') ?? 0;
    if (!Number.isSafeInteger(current) || current < 0) throw new HttpsError('failed-precondition', 'Registro inválido.');
    // Repetição após perda da resposta não sobrescreve outra edição nem incrementa de novo.
    if (Object.entries(fields).every(([key,value]) => doc.get(key) === value)) return current;
    if (current !== data.revision) throw new HttpsError('aborted', 'O herói foi alterado. Recarregue antes de salvar.');
    tx.update(ref, {...fields, textRevision: current + 1, updatedAt: FieldValue.serverTimestamp()});
    return current + 1;
  });
  return {uid,heroId,revision};
}
module.exports = {updateHeroText};

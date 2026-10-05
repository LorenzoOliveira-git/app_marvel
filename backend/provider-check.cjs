'use strict';
const {HttpsError} = require('firebase-functions/v2/https');
const {POLICY} = require('./hero-creation.cjs');
const localHost = value => /^(127\.0\.0\.1|localhost):\d+$/.test(value || '');
const present = value => typeof value === 'string' && value.trim().length > 0;
async function providerConfigurationCheck(request) {
  if (process.env.FUNCTIONS_EMULATOR !== 'true' || process.env.GCLOUD_PROJECT !== 'demo-marvel-local'
      || !localHost(process.env.FIREBASE_AUTH_EMULATOR_HOST)) {
    throw new HttpsError('failed-precondition', 'Diagnóstico disponível somente no ambiente local demo.');
  }
  if (!request.auth) throw new HttpsError('unauthenticated', 'Entre na conta local para verificar a configuração.');
  const data = request.data;
  if (!data || typeof data !== 'object' || Array.isArray(data) || Object.keys(data).some(key => key !== 'checkConnections')
      || (data.checkConnections !== undefined && typeof data.checkConnections !== 'boolean')) {
    throw new HttpsError('invalid-argument', 'Informe somente a opção de consultar conexões.');
  }
  const cloudflareConfigured = require('./hero-image-providers.cjs').cloudflareConfigured();
  const cloudinaryConfigured = /^[a-z0-9-]+$/.test(process.env.CLOUDINARY_CLOUD_NAME || '')
      && present(process.env.CLOUDINARY_API_KEY) && present(process.env.CLOUDINARY_API_SECRET);
  const report = {
    uid: request.auth.uid, localEnvironment: true,
    generationEnabled: process.env.HERO_GENERATION_ENABLED === 'true',
    comicVineConfigured: present(process.env.COMICVINE_API_KEY),
    firestoreConfigured: localHost(process.env.FIRESTORE_EMULATOR_HOST),
    storageConfigured: localHost(process.env.FIREBASE_STORAGE_EMULATOR_HOST),
    model: POLICY.model,
    cloudflare: {configured: cloudflareConfigured, connection: cloudflareConfigured ? 'not_checked' : 'not_configured'},
    cloudinary: {configured: cloudinaryConfigured, connection: cloudinaryConfigured ? 'not_checked' : 'not_configured'},
    connectionsRequested: data.checkConnections === true,
    firstRealAttemptPending: true, generatedImage: false
  };
  if (data.checkConnections === true) {
    await Promise.all([
      (async () => {
        if (!cloudflareConfigured) return;
        try { await require('./hero-image-providers.cjs').verifyModelAccess(); report.cloudflare.connection = 'passed'; }
        catch (_) { report.cloudflare.connection = 'unavailable'; }
      })(),
      (async () => {
        if (!cloudinaryConfigured) return;
        try {
          const providers = require('./hero-image-providers.cjs');
          const response = await require('cloudinary').v2.api.ping({...providers.configuration(false), timeout: 20000});
          report.cloudinary.connection = response.status === 'ok' ? 'passed' : 'unavailable';
        } catch (_) { report.cloudinary.connection = 'unavailable'; }
      })()
    ]);
  }
  // Presença/consultas não provam permissão de gerar, enviar ou baixar uma imagem.
  report.configurationComplete = report.generationEnabled && report.comicVineConfigured && report.firestoreConfigured
    && report.storageConfigured && cloudflareConfigured && cloudinaryConfigured;
  return report;
}
module.exports = {providerConfigurationCheck};

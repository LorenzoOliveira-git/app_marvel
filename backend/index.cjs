'use strict';
const {initializeApp} = require('firebase-admin/app');
const {getFirestore, FieldValue} = require('firebase-admin/firestore');
const {onCall, HttpsError} = require('firebase-functions/v2/https');
initializeApp();

// Diagnóstico verdadeiro dos emuladores; não cria herói nem chama serviços pagos.
exports.localSessionCheck = onCall({region: 'us-central1'}, async request => {
  if (process.env.FUNCTIONS_EMULATOR !== 'true') {
    throw new HttpsError('failed-precondition', 'Operação disponível somente nos emuladores locais.');
  }
  if (!request.auth) throw new HttpsError('unauthenticated', 'Entre na sua conta local.');
  const uid = request.auth.uid;
  await getFirestore().doc(`users/${uid}/localChecks/session`).set({
    uid, environment: 'emulator', checkedAt: FieldValue.serverTimestamp()
  });
  return {uid, environment: 'emulator', generatedImage: false};
});

exports.saveHeroDraft = onCall({region: 'us-central1'}, require('./hero-drafts.cjs').saveHeroDraft);
exports.prepareHeroCreation = onCall({region: 'us-central1'}, require('./hero-creation.cjs').prepareHeroCreation);
const generation = require('./hero-generation.cjs');
exports.executeHeroCreation = onCall({region: 'us-central1', timeoutSeconds: 540, memory: '512MiB'}, generation.executeHeroCreation);
exports.resumeHeroCreation = onCall({region: 'us-central1', timeoutSeconds: 540, memory: '512MiB'}, generation.resumeHeroCreation);
exports.retryHeroGeneration = onCall({region: 'us-central1'}, generation.retryHeroGeneration);
exports.heroImageUrl = onCall({region: 'us-central1'}, generation.heroImageUrl);
exports.updateHeroText = onCall({region: 'us-central1'}, require('./hero-profile.cjs').updateHeroText);

'use strict';
// Test-only fixtures: hard stops prevent any access to a real Firebase project.
const assert = require('node:assert/strict');
const {randomUUID} = require('node:crypto');
const {createRequire} = require('node:module');
const fs = require('node:fs');
const path = require('node:path');
const adminRequire = createRequire(path.resolve('backend/package.json'));
const {initializeApp} = adminRequire('firebase-admin/app');
const {getFirestore, Timestamp} = adminRequire('firebase-admin/firestore');
const {getAuth} = adminRequire('firebase-admin/auth');
for (const variable of ['FIRESTORE_EMULATOR_HOST', 'FIREBASE_AUTH_EMULATOR_HOST']) {
  assert.match(process.env[variable] || '', /^(127\.0\.0\.1|localhost):\d+$/);
}
assert.match(process.env.DESIGN_TEST_UID || '', /^[A-Za-z0-9_-]{1,128}$/);
initializeApp({projectId: 'demo-marvel-local'});
(async () => {
  const uid = process.env.DESIGN_TEST_UID;
  const user = await getAuth().getUser(uid);
  assert.match(user.email || '', /^profile-\d+@example\.test$/);
  const db = getFirestore();
  const collection = db.collection(`users/${uid}/heroes`);
  const file = 'firebase-local-check/collection-fixture.json';
  if (process.argv[2] === 'verify') {
    const ids = JSON.parse(fs.readFileSync(file, 'utf8')).heroIds;
    const rows = await Promise.all(ids.map(id => collection.doc(id).get()));
    const edited = rows.find(row => row.get('heroName') === 'Aurora Revisada');
    assert.ok(edited, 'Alteração da interface não foi confirmada no servidor.');
    assert.equal(edited.get('textRevision'), 1);
    assert.equal(edited.get('designFixture'), true);
    console.log('Edição textual confirmada no Firestore local; nenhum provedor pago utilizado.');
  } else {
    assert.equal((await collection.get()).size, 0);
    const heroIds = [];
    for (const [i, name] of ['Aurora do Horizonte', 'Guardião dos Caminhos e das Estrelas Distantes'].entries()) {
      const operationId = randomUUID(); heroIds.push(operationId);
      await collection.doc(operationId).set({uid, operationId, heroName: name,
        realName: i ? 'Rafael Costa' : 'Lia Torres',
        description: 'Registro fictício para validar a ficha, os campos de edição e o salvamento no emulador.',
        textRevision: 0, designFixture: true, generatedImage: false,
        createdAt: Timestamp.fromMillis(Date.now() - i * 60000)});
    }
    fs.writeFileSync(file, JSON.stringify({heroIds, emulatorOnly: true, generatedImage: false}, null, 2));
    console.log('Duas fichas fictícias preparadas exclusivamente na conta de teste do emulador.');
  }
})().catch(error => { console.error(error.message); process.exitCode = 1; });

'use strict';
// Alternativa para ambientes que bloqueiam IPC Unix: Auth emulado + callable real do SDK sobre HTTP.
// Não substitui a verificação do Functions Emulator na CI e não serve funções de geração.
const {spawn} = require('node:child_process');
const assert = require('node:assert/strict');
assert.match(process.env.FIREBASE_AUTH_EMULATOR_HOST || '', /^(127\.0\.0\.1|localhost):\d+$/);
process.env.GCLOUD_PROJECT = 'demo-marvel-local';
process.env.FUNCTIONS_EMULATOR = 'true';
const app = require('express')();
app.use(require('express').json());
app.post('/demo-marvel-local/us-central1/providerConfigurationCheck', require('../backend/index.cjs').providerConfigurationCheck);
const server = app.listen(5001, '127.0.0.1', () => {
  const child = spawn(process.execPath, ['tools/check-provider-configuration-local.cjs'], {stdio: 'inherit'});
  child.on('error', () => {process.exitCode=1;server.close();});
  child.on('exit', code => {process.exitCode=code===0?0:1;server.close();});
});
server.on('error', () => {console.error('Não foi possível abrir o servidor HTTP local do SDK.');process.exitCode=1;});

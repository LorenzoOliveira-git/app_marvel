'use strict';
const req = require('node:module').createRequire(require.resolve('../backend/package.json'));
req('firebase-admin/app').initializeApp({projectId:'demo-marvel-local'});
require('../backend/hero-temporary-cleanup.cjs').cleanupExpiredTemporaryImages()
  .then(result=>console.log(`Temporários expirados removidos: ${result.deleted}`))
  .catch(()=>{console.error('Não foi possível limpar o armazenamento temporário local.');process.exitCode=1;});

'use strict';
const {getStorage} = require('firebase-admin/storage');
async function cleanupExpiredTemporaryImages() {
  if (!/^(127\.0\.0\.1|localhost):\d+$/.test(process.env.FIREBASE_STORAGE_EMULATOR_HOST || '')) throw new Error('Limpeza disponível somente no Storage local.');
  const [files] = await getStorage().bucket('demo-marvel-local.appspot.com').getFiles({prefix:'heroPending/'});
  let deleted = 0;
  for (const file of files) {
    const [metadata] = await file.getMetadata(), custom = metadata.metadata || {};
    if (custom.uid && custom.operationId && file.name === `heroPending/${custom.uid}/${custom.operationId}.png`
        && Number.isFinite(Number(custom.expiresAtMs)) && Number(custom.expiresAtMs) <= Date.now()) {
      await file.delete(); deleted++;
    }
  }
  return {deleted};
}
module.exports = {cleanupExpiredTemporaryImages};

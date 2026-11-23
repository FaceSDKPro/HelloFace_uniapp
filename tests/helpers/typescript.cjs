const path = require('node:path')

// Prefer project dependencies; HBuilderX also bundles the compiler for local checks.
try {
  module.exports = require('typescript')
} catch (_) {
  module.exports = require(path.join(process.env.HBUILDERX_PATH || '/Applications/HBuilderX.app/Contents/HBuilderX',
    'plugins/uniapp-uts-v1/node_modules/@dcloudio/uni-uts-v1/lib/typescript'))
}

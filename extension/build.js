const fs = require('node:fs');
const path = require('node:path');
const { stripTypeScriptTypes } = require('node:module');

const EXT_DIR = __dirname;
const SRC_DIR = path.join(EXT_DIR, 'src');
const DIST_DIR = path.join(EXT_DIR, 'dist');

if (!fs.existsSync(DIST_DIR)) {
  fs.mkdirSync(DIST_DIR, { recursive: true });
}

console.log('[ApplyPilot Extension Build] Starting compilation using Node v24...');

function transpileFile(srcPath) {
  const code = fs.readFileSync(srcPath, 'utf8');
  // Strip TS types
  const js = stripTypeScriptTypes(code);
  return js;
}

// 1. Build dist/background.js
try {
  const bgTs = path.join(SRC_DIR, 'background', 'service-worker.ts');
  let bgJs = transpileFile(bgTs);
  // Clean type imports
  bgJs = bgJs.replace(/import\s+[^;]+from\s+['"][^'"]+['"];?/g, '');
  fs.writeFileSync(path.join(DIST_DIR, 'background.js'), bgJs, 'utf8');
  console.log('✓ Compiled dist/background.js');
} catch (e) {
  console.error('Error building background.js:', e);
  process.exit(1);
}

// 2. Build dist/popup.js
try {
  const popupTs = path.join(SRC_DIR, 'popup', 'popup.ts');
  const workflowTs = path.join(SRC_DIR, 'popup', 'workflow.ts');
  const workflowJs = transpileFile(workflowTs)
    .replace(/import\s+[^;]+from\s+['"][^'"]+['"];?/g, '')
    .replace(/export\s+/g, '');
  let popupJs = transpileFile(popupTs);
  popupJs = popupJs.replace(/import\s+[^;]+from\s+['"][^'"]+['"];?/g, '');
  fs.writeFileSync(path.join(DIST_DIR, 'popup.js'), `${workflowJs}\n${popupJs}`, 'utf8');
  console.log('✓ Compiled dist/popup.js');
} catch (e) {
  console.error('Error building popup.js:', e);
  process.exit(1);
}

// 3. Build dist/content.js (bundled content script with detector, field-mapper, autofill)
try {
  const detectorTs = path.join(SRC_DIR, 'content', 'detector.ts');
  const mapperTs = path.join(SRC_DIR, 'content', 'field-mapper.ts');
  const autofillTs = path.join(SRC_DIR, 'content', 'autofill.ts');

  let detectorJs = transpileFile(detectorTs).replace(/import\s+[^;]+from\s+['"][^'"]+['"];?/g, '').replace(/export\s+/g, '');
  let mapperJs = transpileFile(mapperTs).replace(/import\s+[^;]+from\s+['"][^'"]+['"];?/g, '').replace(/export\s+/g, '');
  let autofillJs = transpileFile(autofillTs).replace(/import\s+[^;]+from\s+['"][^'"]+['"];?/g, '').replace(/export\s+/g, '');

  const bundledContent = `// ApplyPilot Chrome Content Script (Manifest V3 Bundle)
(function() {
  'use strict';

  // --- DETECTOR MODULE ---
  ${detectorJs}

  // --- FIELD MAPPER MODULE ---
  ${mapperJs}

  // --- AUTOFILL ENGINE ---
  ${autofillJs}

})();
`;

  fs.writeFileSync(path.join(DIST_DIR, 'content.js'), bundledContent, 'utf8');
  console.log('✓ Compiled and bundled dist/content.js');
} catch (e) {
  console.error('Error building content.js:', e);
  process.exit(1);
}

console.log('[ApplyPilot Extension Build] Build completed successfully.');

// Create the GitHub Release and upload the APK.
// Uses the credential that Git Credential Manager already holds for github.com
// (retrieved via `git credential fill`) -- no token is ever written to disk
// in this repo, and nothing is printed.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { fileURLToPath } from 'node:url';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OWNER = 'RivYayoi';
const REPO_NAME = 'RPG-Maker-MV-For-Android';
const TAG = 'v1.0.0';
const API = `https://api.github.com/repos/${OWNER}/${REPO_NAME}`;

function credential() {
  const input = 'protocol=https\nhost=github.com\n\n';
  const out = execFileSync('git', ['credential', 'fill'], { input, encoding: 'utf8' });
  const m = out.match(/^password=(.+)$/m);
  if (!m) throw new Error('no credential for github.com');
  return m[1].trim();
}

const TOKEN = credential();
const AUTH = 'Basic ' + Buffer.from(`${OWNER}:${TOKEN}`).toString('base64');
const HEADERS = {
  Authorization: AUTH,
  Accept: 'application/vnd.github+json',
  'X-GitHub-Api-Version': '2022-11-28',
  'User-Agent': 'rmmv-release-script',
};

const BODY = `## RPG Maker MV Editor for Android — v1.0.0

A **native Java** Android port of the RPG Maker MV authoring tool. No WebView, no Capacitor, no HTML shell — the whole UI is platform Views drawn with \`Canvas\`.

### Install

\`\`\`bash
adb install -r -t RPGMakerMVEditor-Android-1.0.0.apk
\`\`\`

### Why native?

Phase 1 REA analysis of the editor install tree showed the RPG Maker MV **editor is a Qt5/QML native desktop app, not an NW.js app**. Across all 204 JS files the editor's own UI code contains **0** Node-core, **0** NW.js and **0** IPC references, so there was no JavaScript to shim. The portable boundary is the RPG Maker MV *project format*, which this port re-implements in Java.

### What it does

* **PC Main Window reproduced** component-for-component from the editor's own manual screenshot (\`Help/inc/img/01_03_img01.png\`): horizontal menu bar (File / Edit / Mode / Draw / Scale / Tools / Game / Help), icon toolbar, vertical tile palette with A·B·R tabs, map-list tree, map view, panelled status bar.
* **Landscape + portrait** via resource qualifiers; edge-to-edge with \`WindowInsetsCompat\` so the PC menu bar clears the system status bar.
* **Map editing**: autotile painting, 4 layers, pinch-zoom, pan, grid, undo/redo, new/rename/delete maps.
* **Events**: create, select, rename, position, trigger, facing, movement type, through; command list view.
* **Database** (15 entity pages with schema-driven forms), **Plugin Manager**, **Resource Manager**, **Map Properties**.
* Projects are byte-compatible with the desktop editor: the produced \`Map001.json\` has **exact key parity** with the editor's own file.

### Verification (Android 14 emulator)

* end-to-end: **19/19** — paint writes \`2816\` (= \`TILE_ID_A2\`), \`data[]\` = \`w*h*4\`, schema parity \`missing=[] extra=[]\`, survives restart
* orientation: **20/20** — all six PC components in both orientations
* **zero** crashes, **zero** \`android.webkit\` usage in app code

### Note on assets

This APK embeds RPG Maker MV's engine and art assets, which are © Kadokawa. They are **not** redistributed in the git repository — run \`scripts/fetch-template.mjs\` to copy them from your own RPG Maker MV installation when building from source.

### SHA-256

See \`checksums.txt\`.
`;

const CHECKSUMS = `SHA256 (checksums.txt)
------------------------
`;

async function main() {
  // 1. create the release
  let release;
  const existing = await fetch(`${API}/releases/tags/${TAG}`, { headers: HEADERS });
  if (existing.ok) {
    release = await existing.json();
    console.log(`release for ${TAG} already exists (id ${release.id}) — reusing`);
  } else {
    const res = await fetch(`${API}/releases`, {
      method: 'POST',
      headers: { ...HEADERS, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        tag_name: TAG,
        name: 'RPG Maker MV Editor for Android v1.0.0 (native Java)',
        body: BODY,
        draft: false,
        prerelease: false,
      }),
    });
    if (!res.ok) throw new Error(`create release failed: ${res.status} ${await res.text()}`);
    release = await res.json();
    console.log(`created release id ${release.id}`);
  }

  // 2. stage the assets
  const apkSrc = path.join(REPO, 'dist', 'rmmv-editor-native-debug.apk');
  const apkName = 'RPGMakerMVEditor-Android-1.0.0.apk';
  const apkBuf = fs.readFileSync(apkSrc);
  const crypto = await import('node:crypto');
  const apkSha = crypto.createHash('sha256').update(apkBuf).digest('hex');
  console.log(`apk ${(apkBuf.length / 1048576).toFixed(2)} MB  sha256 ${apkSha}`);

  const checksums = `${apkName}\n${apkSha}\n`;
  fs.writeFileSync(path.join(REPO, 'dist', 'checksums.txt'), checksums, 'utf8');

  // 3. upload (replace if the asset name already exists)
  const assets = await (await fetch(`${API}/releases/${release.id}/assets`, { headers: HEADERS })).json();
  for (const a of assets) {
    if (a.name === apkName || a.name === 'checksums.txt') {
      await fetch(`${API}/releases/assets/${a.id}`, { method: 'DELETE', headers: HEADERS });
      console.log(`deleted old asset ${a.name}`);
    }
  }

  async function upload(name, data, type) {
    const url = `https://uploads.github.com/repos/${OWNER}/${REPO_NAME}/releases/${release.id}/assets?name=${encodeURIComponent(name)}`;
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        Authorization: AUTH,
        Accept: 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        'User-Agent': 'rmmv-release-script',
        'Content-Type': type,
        'Content-Length': String(data.length),
      },
      body: data,
    });
    if (!res.ok) throw new Error(`upload ${name} failed: ${res.status} ${await res.text()}`);
    const j = await res.json();
    console.log(`  uploaded ${j.name}  ${j.size} bytes  ${j.browser_download_url}`);
    return j;
  }

  await upload(apkName, apkBuf, 'application/vnd.android.package-archive');
  await upload('checksums.txt', Buffer.from(checksums, 'utf8'), 'text/plain');

  console.log('\nrelease URL: ' + release.html_url);
}

main().catch((e) => { console.error('FAILED: ' + e.message); process.exit(1); });

#!/usr/bin/env node
// Copy the RPG Maker MV new-project scaffold out of a local RPG Maker MV
// installation into the Android app's assets.
//
// The scaffold (engine JS + art) is (c) Kadokawa / RPG Maker MV and is
// therefore NOT redistributed in this repository. You need a local copy of
// RPG Maker MV to build the APK -- which you need anyway to work on the
// editor.
//
// Usage:
//   MV_INSTALL_DIR="C:\Program Files (x86)\KADOKAWA\RPGMV" node scripts/fetch-template.mjs
//   (defaults to the standard Windows install path)

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const INSTALL = process.env.MV_INSTALL_DIR || 'C:\\Program Files (x86)\\KADOKAWA\\RPGMV';
const SRC = path.join(INSTALL, 'NewData');
const DST = path.join(REPO, 'app-java', 'app', 'src', 'main', 'assets', 'template');

// Files copied verbatim from the editor's own NewData scaffold.
const TEMPLATE = [
  'index.html', 'package.json',
  'js/main.js', 'js/plugins.js', 'js/rpg_core.js', 'js/rpg_managers.js',
  'js/rpg_objects.js', 'js/rpg_scenes.js', 'js/rpg_sprites.js', 'js/rpg_windows.js',
  'js/libs/pixi.js', 'js/libs/pixi-tilemap.js', 'js/libs/pixi-picture.js',
  'js/libs/fpsmeter.js', 'js/libs/lz-string.js', 'js/libs/iphone-inline-video.browser.js',
  'data/System.json', 'data/Actors.json', 'data/Classes.json', 'data/Skills.json',
  'data/Items.json', 'data/Weapons.json', 'data/Armors.json', 'data/Enemies.json',
  'data/Troops.json', 'data/States.json', 'data/Animations.json', 'data/Tilesets.json',
  'data/CommonEvents.json',
  'fonts/gamefont.css', 'fonts/mplus-1m-regular.ttf',
  'icon/icon.png',
  'img/tilesets/World_A1.png', 'img/tilesets/World_A2.png',
  'img/tilesets/World_B.png', 'img/tilesets/World_C.png',
];

if (!fs.existsSync(SRC)) {
  console.error(`RPG Maker MV not found at:\n  ${SRC}`);
  console.error('Set MV_INSTALL_DIR to your RPG Maker MV installation folder.');
  process.exit(1);
}

let copied = 0, missing = 0;
for (const rel of TEMPLATE) {
  const from = path.join(SRC, rel);
  const to = path.join(DST, rel);
  if (!fs.existsSync(from)) { console.warn('  missing in install: ' + rel); missing++; continue; }
  fs.mkdirSync(path.dirname(to), { recursive: true });
  fs.copyFileSync(from, to);
  copied++;
}

const total = fs.readdirSync(DST, { recursive: true })
  .filter((f) => fs.statSync(path.join(DST, f)).isFile())
  .reduce((n, f) => n + fs.statSync(path.join(DST, f)).size, 0);

console.log(`copied ${copied} files (${missing} missing) -> ${path.relative(REPO, DST)}`);
console.log(`total ${(total / 1048576).toFixed(2)} MB`);
console.log('The Android app copies these into every new project it creates.');

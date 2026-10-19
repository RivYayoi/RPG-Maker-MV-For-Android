// Native end-to-end acceptance for the pure-Java build.
// Drives real touch input via `adb shell input` (there is no WebView, so there
// is no CDP -- this is the native equivalent) and verifies the saved project on
// disk through `run-as`, i.e. real java.io file output.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const SDK = `${REPO}/android-sdk/platform-tools/adb.exe`;
const EV = `${REPO}/evidence`;

const adb = (...a) => execFileSync(SDK, a, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
const sleep = (ms) => { const t = Date.now(); while (Date.now() - t < ms); };

function dump() {
  adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml');
  return adb('shell', 'cat', '/sdcard/ui.xml');
}
function nodes(xml) {
  const out = [];
  const re = /<node[^>]*>/g; let m;
  while ((m = re.exec(xml)) !== null) {
    const t = m[0];
    const b = t.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
    if (!b) continue;
    out.push({
      id: (t.match(/resource-id="([^"]*)"/) || [])[1] || '',
      text: (t.match(/text="([^"]*)"/) || [])[1] || '',
      desc: (t.match(/content-desc="([^"]*)"/) || [])[1] || '',
      cls: (t.match(/class="([^"]*)"/) || [])[1] || '',
      cx: Math.floor((+b[1] + +b[3]) / 2), cy: Math.floor((+b[2] + +b[4]) / 2),
      w: +b[3] - +b[1], h: +b[4] - +b[2],
    });
  }
  return out;
}
const byId = (ns, id) => ns.find((n) => n.id.endsWith(id));
const tap = (x, y) => adb('shell', 'input', 'tap', String(x), String(y));
const tapId = (ns, id) => { const n = byId(ns, id); if (n) tap(n.cx, n.cy); return n; };
const shot = (n) => { adb('shell', 'screencap', '-p', '/sdcard/s.png'); adb('pull', '/sdcard/s.png', `${EV}/${n}`); console.log('  shot ->', n); };
const restart = () => { adb('shell', 'am', 'force-stop', 'com.rmmv.editor'); adb('shell', 'am', 'start', '-n', 'com.rmmv.editor/.MainActivity'); sleep(4000); };

const R = [];
const check = (name, pass, detail) => { R.push({ name, pass, detail }); console.log(`  ${pass ? 'PASS' : 'FAIL'}  ${name}${detail ? '  ' + detail : ''}`); };

console.log('=== 1. cold start ===');
adb('shell', 'pm', 'clear', 'com.rmmv.editor');
restart();
let ns = nodes(dump());
check('project list renders', ns.some((n) => n.text === 'Projects'));

console.log('\n=== 2. create project ===');
tapId(ns, 'btnNew'); sleep(900);
ns = nodes(dump());
let et = ns.find((n) => n.cls.includes('EditText'));
if (et) {
  tap(et.cx, et.cy);
  // the field is pre-filled ("MyGame"); move to end and clear it before typing
  adb('shell', 'input', 'keyevent', '123');                 // KEYCODE_MOVE_END
  for (let i = 0; i < 24; i++) adb('shell', 'input', 'keyevent', '67'); // DEL
  adb('shell', 'input', 'text', 'NativeE2E');
}
sleep(400);
ns = nodes(dump());
const create = ns.find((n) => n.text === 'Create');
if (create) tap(create.cx, create.cy);
sleep(3500);
ns = nodes(dump());
check('map editor opened', !!byId(ns, 'canvas'));
check('palette present', !!byId(ns, 'palette'));
check('map list tree present', !!byId(ns, 'mapList'));
check('PC menu bar present', !!byId(ns, 'menuBar'));
check('PC toolbar present', !!byId(ns, 'toolBar'));
check('PC status bar present', !!byId(ns, 'statusBar'));
const modeMap = byId(ns, 'btnModeMap');
check('palette A/B/R tabs', !!byId(ns, 'tabA') && !!byId(ns, 'tabB') && !!byId(ns, 'tabR'));
shot('native-03-main-window.png');

console.log('\n=== 3. paint tiles (drag on native Canvas) ===');
const canvas = byId(ns, 'canvas');
const sx = canvas.cx - canvas.w / 4, sy = canvas.cy;
// swipe = drag-paint through MapCanvasView.onTouchEvent
adb('shell', 'input', 'swipe', String(sx), String(sy), String(sx + canvas.w / 3), String(sy), '700');
adb('shell', 'input', 'swipe', String(sx), String(sy + 60), String(sx + canvas.w / 3), String(sy + 60), '700');
sleep(600);
ns = nodes(dump());
const st = ns.find((n) => n.id.endsWith('status'));
console.log('  status bar:', st ? st.text : '(none)');
check('status bar shows tile readout', !!st && /tile/.test(st.text), st ? st.text : '');

console.log('\n=== 4. save + verify on disk (java.io) ===');
// The PC toolbar's save button is an ImageButton identified by its
// content-description, not a resource id.
let saveBtn = ns.find((n) => n.desc === 'Save Project');
if (!saveBtn) {
  const raw = dump();
  const m = raw.match(/content-desc="Save Project"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
  if (m) saveBtn = { cx: Math.floor((+m[1] + +m[3]) / 2), cy: Math.floor((+m[2] + +m[4]) / 2) };
}
check('save button found', !!saveBtn);
if (saveBtn) tap(saveBtn.cx, saveBtn.cy);
sleep(1800);
let mapJson = '';
try { mapJson = adb('shell', 'run-as', 'com.rmmv.editor', 'cat', 'files/Projects/NativeE2E/data/Map001.json'); }
catch (e) { mapJson = 'ERR ' + e.message; }
let parsed = null;
try { parsed = JSON.parse(mapJson); } catch (e) { /* handled below */ }
check('Map001.json written', parsed !== null);
if (parsed) {
  const expectLen = parsed.width * parsed.height * 4;
  check('data length = w*h*4', parsed.data.length === expectLen, `${parsed.data.length} vs ${expectLen}`);
  const painted = parsed.data.filter((v) => v >= 2048 && v < 8192).length;
  check('autotile ids present in data[]', painted > 0, `painted=${painted}`);
  console.log('  sample tileIds:', [...new Set(parsed.data.filter((v) => v > 0))].slice(0, 6).join(', '));
}
const keys = parsed ? Object.keys(parsed).sort() : [];
const orig = JSON.parse(fs.readFileSync('C:/Program Files (x86)/KADOKAWA/RPGMV/NewData/data/Map001.json', 'utf8'));
const missing = Object.keys(orig).sort().filter((k) => !keys.includes(k));
const extra = keys.filter((k) => !Object.keys(orig).includes(k));
check('schema parity with editor Map001.json', missing.length === 0 && extra.length === 0,
  `missing=[${missing}] extra=[${extra}]`);

console.log('\n=== 5. restart app and reopen ===');
restart();
ns = nodes(dump());
const row = ns.find((n) => n.text === 'NativeE2E');
check('project listed after restart', !!row);
if (row) { tap(row.cx, row.cy); sleep(3500); }
ns = nodes(dump());
check('reopened map editor', !!byId(ns, 'canvas'));
const st2 = ns.find((n) => n.id.endsWith('status'));
console.log('  status after reopen:', st2 ? st2.text : '(none)');
shot('native-04-reopened.png');

console.log('\n=== 6. PC menu bar + Tool pages ===');
// The PC menu bar is a horizontal row; in portrait it scrolls, so sweep it and
// collect every title that exists.
const seen = new Set();
const sweep = () => {
  nodes(dump()).forEach((n) => { if (n.text) seen.add(n.text); });
  const mb = nodes(dump()).find((n) => n.id.endsWith('menuBar'));
  if (mb) { adb('shell', 'input', 'swipe', '950', String(mb.cy), '80', String(mb.cy), '400'); sleep(600); }
};
sweep();
sweep();
const menuTitles = ['File', 'Edit', 'Mode', 'Draw', 'Scale', 'Tools', 'Game', 'Help']
  .filter((m) => [...seen].some((t) => t.toLowerCase() === m.toLowerCase()));
console.log('  menu bar titles present:', menuTitles.join(' | '));
check('PC menu bar titles File/Edit/Mode/Draw/Scale/Tools/Game/Help',
  menuTitles.length === 8, 'found ' + menuTitles.length + '/8');

// open the Tools dropdown, then Database
const toolsBtn = [...seen].includes('Tools')
  ? nodes(dump()).find((n) => n.text === 'Tools') : null;
if (toolsBtn) { tap(toolsBtn.cx, toolsBtn.cy); sleep(900); }
const dbItem = nodes(dump()).find((n) => n.text === 'Database');
check('Tools dropdown opens', !!dbItem);
if (dbItem) {
  tap(dbItem.cx, dbItem.cy);
  sleep(2600);
  const dns = nodes(dump());
  const texts = dns.map((n) => n.text).filter(Boolean);
  check('Database page opened', texts.includes('Database') && texts.includes('Actors'),
    texts.slice(0, 10).join(','));
  shot('native-05-database.png');
  adb('shell', 'input', 'keyevent', '4'); sleep(900);
}
console.log('\n=== RESULT ===');
const pass = R.filter((r) => r.pass).length;
console.log(`${pass}/${R.length} passed`);
R.filter((r) => !r.pass).forEach((r) => console.log(`  FAILED: ${r.name} ${r.detail || ''}`));

const bad = adb('logcat', '-d').split('\n').filter((l) => /FATAL EXCEPTION|AndroidRuntime.*FATAL|ANR in com\.rmmv/.test(l));
console.log('\ncrash scan:', bad.length ? bad.slice(0, 6).join('\n') : 'CLEAN');

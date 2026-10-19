// Landscape / portrait adaptation + PC-layout verification.
// Rotates the emulator, dumps the view hierarchy and screenshots each
// orientation, asserting the PC Main Window components are all present and
// that the system status bar does not overlap the PC menu bar.
import { execFileSync } from 'node:child_process';
const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const SDK = `${REPO}/android-sdk/platform-tools/adb.exe`;
const EV = `${REPO}/evidence`;
const adb = (...a) => execFileSync(SDK, a, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
const sleep = (ms) => { const t = Date.now(); while (Date.now() - t < ms); };

function dump() { adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml'); return adb('shell', 'cat', '/sdcard/ui.xml'); }
function nodes(xml) {
  const out = []; const re = /<node[^>]*>/g; let m;
  while ((m = re.exec(xml)) !== null) {
    const t = m[0]; const b = t.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
    if (!b) continue;
    out.push({
      id: (t.match(/resource-id="([^"]*)"/) || [])[1] || '',
      text: (t.match(/text="([^"]*)"/) || [])[1] || '',
      cls: (t.match(/class="([^"]*)"/) || [])[1] || '',
      x1: +b[1], y1: +b[2], x2: +b[3], y2: +b[4],
      cx: Math.floor((+b[1] + +b[3]) / 2), cy: Math.floor((+b[2] + +b[4]) / 2),
    });
  }
  return out;
}
const byId = (ns, id) => ns.find((n) => n.id.endsWith(id));
const tap = (n) => adb('shell', 'input', 'tap', String(n.cx), String(n.cy));
const shot = (n) => { adb('shell', 'screencap', '-p', '/sdcard/s.png'); adb('pull', '/sdcard/s.png', `${EV}/${n}`); };

function rotate(deg) {
  adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0');
  adb('shell', 'settings', 'put', 'system', 'user_rotation', String(deg));
  sleep(2500);
}
function size() {
  const out = adb('shell', 'wm', 'size');
  const m = out.match(/(\d+)x(\d+)/);
  return m ? { w: +m[1], h: +m[2] } : { w: 0, h: 0 };
}

const R = [];
const check = (name, pass, detail) => { R.push({ name, pass }); console.log(`  ${pass ? 'PASS' : 'FAIL'}  ${name}${detail ? '  ' + detail : ''}`); };

// ---- launch into the editor -------------------------------------------------
adb('shell', 'am', 'force-stop', 'com.rmmv.editor'); sleep(800);
adb('shell', 'am', 'start', '-n', 'com.rmmv.editor/.MainActivity'); sleep(3000);
let ns = nodes(dump());
const row = ns.find((n) => n.text === 'NativeE2E');
if (row) { tap(row); sleep(3500); } else { console.log('  (no project row; using existing state)'); }

const screen = size();
console.log(`device ${screen.w}x${screen.h}`);

for (const [deg, label] of [[0, 'portrait'], [1, 'landscape']]) {
  rotate(deg);
  const s = size();
  console.log(`\n=== ${label} (wm size ${s.w}x${s.h}) ===`);
  ns = nodes(dump());

  const menu = byId(ns, 'menuBar');
  const toolbar = byId(ns, 'toolBar');
  const palette = byId(ns, 'palette');
  const mapList = byId(ns, 'mapList');
  const canvas = byId(ns, 'canvas');
  const statusBar = byId(ns, 'statusBar');
  const root = byId(ns, 'root');

  check(`${label}: (1) menu bar present`, !!menu);
  check(`${label}: (2) toolbar present`, !!toolbar);
  check(`${label}: (3) tile palette present`, !!palette);
  check(`${label}: (4) map list present`, !!mapList);
  check(`${label}: (5) map view present`, !!canvas);
  check(`${label}: (6) status bar present`, !!statusBar);

  if (menu) {
    // In portrait the menu bar is horizontally scrollable, so scroll it to the
    // end and collect every title across the whole bar.
    const menuTexts = new Set();
    const collect = () => nodes(dump())
      .filter((n) => n.y1 >= menu.y1 - 4 && n.y2 <= menu.y2 + 4 && n.text)
      .forEach((n) => menuTexts.add(n.text));
    collect();
    adb('shell', 'input', 'swipe', '900', String(menu.cy), '100', String(menu.cy), '400');
    sleep(700);
    collect();
    adb('shell', 'input', 'swipe', '100', String(menu.cy), '900', String(menu.cy), '400');
    sleep(700);
    collect();

    const list = [...menuTexts];
    console.log('   menu bar:', list.join(' | '));
    const want = ['File', 'Edit', 'Mode', 'Draw', 'Scale', 'Tools', 'Game', 'Help'];
    const missing = want.filter((m) => !list.some((t) => t.toLowerCase() === m.toLowerCase()));
    check(`${label}: PC menus File/Edit/Mode/Draw/Scale/Tools/Game/Help`,
      missing.length === 0, missing.length ? 'missing=' + missing.join(',') : 'all 8');
  }

  if (menu && root) {
    // system status bar inset: the PC menu bar must start BELOW the status bar
    const statusBarH = 66; // 24dp * density(2.75) on this 440dpi device
    check(`${label}: menu bar clears the system status bar`, menu.y1 >= statusBarH,
      `menu.y1=${menu.y1} statusBarH~${statusBarH}`);
  }
  if (palette && mapList && canvas) {
    check(`${label}: palette + map list are LEFT of the map view`,
      palette.x2 <= canvas.x1 + 2 && mapList.x2 <= canvas.x1 + 2,
      `palette.x2=${palette.x2} mapList.x2=${mapList.x2} canvas.x1=${canvas.x1}`);
  }
  if (palette) {
    const w = palette.x2 - palette.x1;
    console.log(`   left column width: ${w}px`);
  }
  if (statusBar) {
    const bottom = Math.max(...ns.map((n) => n.y2));
    check(`${label}: status bar sits at the bottom`, statusBar.y1 > canvas.y2 - 2,
      `statusBar.y1=${statusBar.y1}`);
  }
  shot(`native-11-${label}.png`);
}

rotate(0);
const bad = adb('logcat', '-d').split('\n').filter((l) => /FATAL EXCEPTION|ANR in com\.rmmv/.test(l));
console.log('\ncrash scan:', bad.length ? bad.slice(0, 5).join('\n') : 'CLEAN');
const p = R.filter((r) => r.pass).length;
console.log(`${p}/${R.length} passed`);
R.filter((r) => !r.pass).forEach((r) => console.log('  FAILED: ' + r.name));

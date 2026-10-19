// Open each PC editor page from the native menu bar and screenshot it.
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
      cx: Math.floor((+b[1] + +b[3]) / 2), cy: Math.floor((+b[2] + +b[4]) / 2),
    });
  }
  return out;
}
const tap = (x, y) => adb('shell', 'input', 'tap', String(x), String(y));
const shot = (n) => { adb('shell', 'screencap', '-p', '/sdcard/s.png'); adb('pull', '/sdcard/s.png', `${EV}/${n}`); console.log('  shot ->', n); };

function tapText(t) {
  const n = nodes(dump()).find((x) => x.text === t);
  if (!n) { console.log(`  !! "${t}" not found`); return false; }
  tap(n.cx, n.cy); return true;
}
function back() { adb('shell', 'input', 'keyevent', '4'); sleep(700); }

// start from the map editor
adb('shell', 'am', 'force-stop', 'com.rmmv.editor'); sleep(900);
adb('shell', 'am', 'start', '-n', 'com.rmmv.editor/.MainActivity'); sleep(3000);
const row = nodes(dump()).find((n) => n.text === 'NativeE2E');
if (row) { tap(row.cx, row.cy); sleep(3500); }
else {
  const canvas = nodes(dump()).find((n) => n.id.endsWith('canvas'));
  console.log('  already inside editor:', !!canvas);
}

shot('native-06-main-window-styled.png');

const pages = [
  ['Database', 'native-07-database.png'],
  ['Plugin Manager', 'native-08-plugins.png'],
  ['Resource Manager', 'native-09-resources.png'],
  ['Map Properties', 'native-10-map-properties.png'],
];

for (const [label, file] of pages) {
  console.log(`\n> ${label}`);
  // reopen the menu bar
  const menuBtn = nodes(dump()).find((n) => n.id.endsWith('btnMenu'));
  if (!menuBtn) { console.log('  !! menu button not found'); break; }
  tap(menuBtn.cx, menuBtn.cy); sleep(900);
  if (!tapText(label)) { back(); continue; }
  sleep(2200);
  const ns = nodes(dump());
  const texts = ns.map((n) => n.text).filter((t) => t && t.length < 40);
  console.log('  page content:', texts.slice(0, 14).join(' | '));
  shot(file);
  back(); sleep(800);
  // Back can be intercepted by a save-confirm dialog, so re-enter the editor
  // explicitly instead of relying on the back stack.
  adb('shell', 'am', 'force-stop', 'com.rmmv.editor'); sleep(700);
  adb('shell', 'am', 'start', '-n', 'com.rmmv.editor/.MainActivity'); sleep(2500);
  const r2 = nodes(dump()).find((n) => n.text === 'NativeE2E');
  if (r2) { tap(r2.cx, r2.cy); sleep(3000); }
}

const bad = adb('logcat', '-d').split('\n').filter((l) => /FATAL EXCEPTION|AndroidRuntime.*FATAL|ANR in com\.rmmv/.test(l));
console.log('\ncrash scan:', bad.length ? bad.slice(0, 5).join('\n') : 'CLEAN');

// Drive the native (no-WebView) app on the emulator through uiautomator.
// No CDP is possible here precisely because there is no WebView -- this is the
// native equivalent: find a node by resource-id / text, tap its centre.
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const SDK = `${REPO}/android-sdk/platform-tools/adb.exe`;
const EV = `${REPO}/evidence`;

function adb(...args) {
  return execFileSync(SDK, args, { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
}

function dump() {
  adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml');
  return adb('shell', 'cat', '/sdcard/ui.xml');
}

function nodes(xml) {
  const out = [];
  const re = /<node[^>]*>/g;
  let m;
  while ((m = re.exec(xml)) !== null) {
    const tag = m[0];
    const id = (tag.match(/resource-id="([^"]*)"/) || [])[1] || '';
    const text = (tag.match(/text="([^"]*)"/) || [])[1] || '';
    const desc = (tag.match(/content-desc="([^"]*)"/) || [])[1] || '';
    const cls = (tag.match(/class="([^"]*)"/) || [])[1] || '';
    const b = (tag.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/) || []);
    if (b.length < 5) continue;
    const x1 = +b[1], y1 = +b[2], x2 = +b[3], y2 = +b[4];
    out.push({
      id, text, desc, cls,
      cx: Math.floor((x1 + x2) / 2),
      cy: Math.floor((y1 + y2) / 2),
      w: x2 - x1, h: y2 - y1,
    });
  }
  return out;
}

function find(ns, pred, label) {
  const hit = ns.find(pred);
  if (!hit) {
    console.log(`  !! not found: ${label}`);
    console.log('  visible:', ns.map((n) => `${n.id || n.text || n.desc || n.cls.split('.').pop()}`).slice(0, 25).join(' | '));
    return null;
  }
  return hit;
}

const tap = (n) => { adb('shell', 'input', 'tap', String(n.cx), String(n.cy)); };
const tapText = (ns, t) => {
  const n = find(ns, (x) => x.text === t, `text "${t}"`);
  if (n) tap(n);
  return !!n;
};
const tapId = (ns, id) => {
  const n = find(ns, (x) => x.id.endsWith(id), `id "${id}"`);
  if (n) tap(n);
  return !!n;
};

function shot(name) {
  adb('shell', 'screencap', '-p', '/sdcard/s.png');
  adb('pull', '/sdcard/s.png', `${EV}/${name}`);
  console.log(`  screenshot -> ${name}`);
}

function logcatErrors() {
  const out = adb('logcat', '-d');
  const bad = out.split('\n').filter((l) =>
    /FATAL EXCEPTION|AndroidRuntime.*FATAL|ANR in com\.rmmv/.test(l));
  return bad;
}

// ---------------------------------------------------------------- scenarios
const steps = [];
function step(name, fn) { steps.push({ name, fn }); }

// 1. create a project
step('open New Project dialog', () => {
  const ns = nodes(dump());
  tapId(ns, 'btnNew');
});
step('fill project name', () => {
  const ns = nodes(dump());
  // focus the first EditText and set text
  const et = ns.find((n) => n.cls.includes('EditText'));
  if (!et) { console.log('  !! no EditText'); return; }
  tap(et);
  adb('shell', 'input', 'text', 'NativeDemo');
});
step('confirm create', () => {
  const ns = nodes(dump());
  tapText(ns, 'Create');
});
step('wait for editor', () => {
  const ns = nodes(dump());
  const canvas = ns.find((n) => n.id.endsWith('canvas'));
  console.log('  map editor canvas present:', !!canvas);
  const pal = ns.find((n) => n.id.endsWith('palette'));
  console.log('  tile palette present:', !!pal);
  const spinner = ns.find((n) => n.id.endsWith('mapSpinner'));
  console.log('  map list spinner present:', !!spinner);
  shot('native-02-map-editor.png');
  return { canvas: !!canvas, palette: !!pal, spinner: !!spinner };
});

// -------------------------------------------------------------------- run
const results = [];
for (const s of steps) {
  console.log(`\n> ${s.name}`);
  try {
    const r = s.fn();
    if (r) results.push({ name: s.name, ...r });
  } catch (e) {
    console.log('  ERROR', e.message);
  }
}

console.log('\n=== crash scan ===');
const bad = logcatErrors();
console.log(bad.length ? bad.slice(0, 8).join('\n') : 'CLEAN: no fatal exceptions / ANRs');

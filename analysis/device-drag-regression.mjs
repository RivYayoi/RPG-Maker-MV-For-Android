// Regression test for the three drag bugs, executed inside the real WebView of
// the Android emulator via CDP. Drives synthesized TouchEvents through the
// actual viewport listeners.
const CDP_HTTP = 'http://127.0.0.1:9222';

function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    let id = 0;
    const pending = new Map();
    ws.addEventListener('message', (ev) => {
      const m = JSON.parse(ev.data);
      if (m.id && pending.has(m.id)) {
        const p = pending.get(m.id); pending.delete(m.id);
        m.error ? p.reject(new Error(JSON.stringify(m.error))) : p.resolve(m.result);
      }
    });
    ws.addEventListener('error', () => reject(new Error('ws error')));
    ws.addEventListener('open', () => resolve({
      send(method, params) {
        const mid = ++id;
        return new Promise((res, rej) => {
          pending.set(mid, { resolve: res, reject: rej });
          ws.send(JSON.stringify({ id: mid, method, params: params || {} }));
        });
      },
      close: () => ws.close(),
    }));
  });
}

const SCRIPT = `(async function(){
  var out = { tests: [] };
  try {
    await Store.openProject('DeviceAccept');
    await Store.composeCurrentTileset();
    document.getElementById('projects').classList.remove('active');
    document.getElementById('editor').classList.add('active');
    document.getElementById('ed-project').textContent = Store.projectName;

    var vp = document.getElementById('viewport');
    var r = vp.getBoundingClientRect();
    out.viewport = Math.round(r.width) + 'x' + Math.round(r.height);

    var E = window.Editor;
    function T(x, y, id) { return new Touch({identifier: id||1, target: vp, clientX: x, clientY: y}); }
    function fire(type, touches) {
      vp.dispatchEvent(new TouchEvent(type, {
        bubbles: true, cancelable: true,
        touches: touches, targetTouches: touches, changedTouches: touches
      }));
    }
    var cx = Math.round(r.width * 0.4), cy = Math.round(r.height * 0.4);
    var tile = MVFormat.makeAutotileId(16, 0);
    function painted() { return Store.map.data.filter(function(v){return v===tile;}).length; }

    // TEST 1 - pan mode: one finger scrolls, paints nothing
    E.panMode = true; E.mode = 'paint'; E.brush = tile;
    Store.map.data.fill(0);
    var bX = E.offX, bY = E.offY;
    fire('touchstart', [T(cx, cy, 1)]);
    for (var i = 1; i <= 8; i++) fire('touchmove', [T(cx + i*14, cy + i*10, 1)]);
    fire('touchend', []);
    out.tests.push({ name:'pan-mode-drag-scrolls', pass: Math.abs(E.offX-bX)>40, delta:[Math.round(E.offX-bX), Math.round(E.offY-bY)] });
    out.tests.push({ name:'pan-mode-paints-nothing', pass: painted()===0, painted: painted() });

    // TEST 2 - paint mode: one finger draws, does not scroll
    E.panMode = false;
    Store.map.data.fill(0);
    var pX = E.offX, pY = E.offY;
    fire('touchstart', [T(cx, cy, 1)]);
    for (var j = 1; j <= 10; j++) fire('touchmove', [T(cx + j*10, cy + j*7, 1)]);
    fire('touchend', []);
    out.tests.push({ name:'paint-mode-drag-draws', pass: painted()>0, painted: painted() });
    out.tests.push({ name:'paint-mode-does-not-scroll', pass: Math.abs(E.offX-pX)<1 && Math.abs(E.offY-pY)<1 });

    // TEST 3 - undo coalescing: one stroke == one undo entry
    var u0 = E.undoStack.length;
    Store.map.data.fill(0);
    fire('touchstart', [T(cx, cy, 1)]);
    for (var k = 1; k <= 14; k++) fire('touchmove', [T(cx + k*8, cy + k*6, 1)]);
    fire('touchend', []);
    out.tests.push({ name:'one-stroke-one-undo', pass: (E.undoStack.length - u0) === 1, entries: E.undoStack.length - u0 });

    // TEST 4 - after a pinch the remaining finger still scrolls
    E.panMode = true;
    Store.map.data.fill(0);
    fire('touchstart', [T(cx, cy, 1)]);
    fire('touchstart', [T(cx+200, cy, 2)]);
    fire('touchmove',  [T(cx+20, cy, 1), T(cx+240, cy, 2)]);
    var midX = E.offX;
    fire('touchend',   [T(cx+240, cy, 2)]);
    fire('touchmove',  [T(cx+80, cy+60, 1)]);
    out.tests.push({ name:'pinch-tail-finger-still-pans', pass: Math.abs(E.offX-midX) > 20, delta: Math.round(E.offX-midX) });
    out.tests.push({ name:'pinch-tail-paints-nothing', pass: painted()===0, painted: painted() });
    fire('touchend',   [T(cx+80, cy+60, 1)]);

    out.ok = out.tests.every(function(t){ return t.pass; });
    out.passed = out.tests.filter(function(t){return t.pass;}).length + '/' + out.tests.length;
  } catch(e) { out.error = e.message; out.ok = false; }
  return JSON.stringify(out);
})()`;

(async () => {
  const targets = await (await fetch(`${CDP_HTTP}/json/list`)).json();
  const page = targets.find((t) => t.type === 'page' && t.url.includes('localhost'));
  if (!page) { console.error('no page target'); process.exit(1); }
  const cdp = await connect(page.webSocketDebuggerUrl);
  const r = await cdp.send('Runtime.evaluate', { expression: SCRIPT, awaitPromise: true, returnByValue: true });
  const v = JSON.parse(r.result.value);
  console.log('viewport:', v.viewport);
  console.log('');
  v.tests.forEach((t) => console.log((t.pass ? '  PASS  ' : '  FAIL  ') + t.name + '   ' + JSON.stringify(t)));
  console.log('\n' + (v.passed || '') + '   ok=' + v.ok + (v.error ? '  error=' + v.error : ''));
  cdp.close();
  process.exit(v.ok ? 0 : 1);
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });

// Paint a small scene on the device and switch the UI to the editor screen so
// the emulator screenshot shows the real MVP editor running on Android.
import fs from 'node:fs';

const CDP_HTTP = 'http://127.0.0.1:9222';

function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    let id = 0;
    const pending = new Map();
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data);
      if (msg.id && pending.has(msg.id)) {
        const p = pending.get(msg.id); pending.delete(msg.id);
        msg.error ? p.reject(new Error(JSON.stringify(msg.error))) : p.resolve(msg.result);
      }
    });
    ws.addEventListener('error', () => reject(new Error('ws error')));
    ws.addEventListener('open', () => resolve({
      send(method, params) {
        const mid = ++id;
        return new Promise((res, rej) => { pending.set(mid, { resolve: res, reject: rej }); ws.send(JSON.stringify({ id: mid, method, params: params || {} })); });
      },
      close: () => ws.close(),
    }));
  });
}

const SCRIPT = `(async function(){
  await Store.openProject('DeviceAccept');
  await Store.composeCurrentTileset();
  var E = window.Editor;
  var g = MVFormat.makeAutotileId(16, 0);
  var w = MVFormat.makeAutotileId(0, 0);
  var d = MVFormat.makeAutotileId(32, 0);
  for (var x = 0; x < 20; x++) for (var y = 0; y < 15; y++) {
    MVFormat.setTile(Store.map, x, y, 0, (y >= 11) ? w : g);
  }
  for (var x2 = 0; x2 < 20; x2++) MVFormat.setTile(Store.map, x2, 0, 0, d);
  Store.map.events[1] = MVFormat.defaultEvent(1, 6, 6, 'Hero');
  Store.map.events[2] = MVFormat.defaultEvent(2, 13, 4, 'Chest');
  E.brush = g; E.layer = 0; E.paletteTab = 'auto';
  E.selectedEventId = 1;
  document.getElementById('projects').classList.remove('active');
  document.getElementById('editor').classList.add('active');
  document.getElementById('ed-project').textContent = Store.projectName;
  var sel = document.getElementById('map-select');
  sel.innerHTML = '';
  Store.mapInfos.forEach(function(info, id){ if(info && id>0){ var o=document.createElement('option'); o.value=String(id); o.textContent=id+' · '+info.name; sel.appendChild(o);} });
  sel.value = String(Store.mapId);
  var pal = Tileset.buildPalette(Store.tilesetCanvas, null, { shapes:[0], kinds:128 });
  var box = document.getElementById('palette');
  box.innerHTML = '';
  pal.slice(0,14).forEach(function(p){
    var d2 = document.createElement('div');
    d2.className = 'pal-item';
    d2.style.backgroundImage = 'url(' + p.canvas.toDataURL() + ')';
    box.appendChild(d2);
  });
  window.dispatchEvent(new Event('resize'));
  return 'painted';
})()`;

(async () => {
  const targets = await (await fetch(`${CDP_HTTP}/json/list`)).json();
  const page = targets.find((t) => t.type === 'page' && t.url.includes('localhost'));
  const cdp = await connect(page.webSocketDebuggerUrl);
  const r = await cdp.send('Runtime.evaluate', { expression: SCRIPT, awaitPromise: true, returnByValue: true });
  console.log('eval:', r.result.value, r.exceptionDetails ? JSON.stringify(r.exceptionDetails) : '');
  await new Promise((res) => setTimeout(res, 2500));
  cdp.close();
  process.exit(0);
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });

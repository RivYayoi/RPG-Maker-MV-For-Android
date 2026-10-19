// Drive the RPG Maker MV editor running inside the Android emulator's WebView
// over the Chrome DevTools Protocol, and run the MVP acceptance flow against
// REAL on-device storage (the browser-based test could not cover this).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const REPO = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const CDP_HTTP = 'http://127.0.0.1:9222';

async function listTargets() {
  const r = await fetch(`${CDP_HTTP}/json/list`);
  return r.json();
}

function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    let id = 0;
    const pending = new Map();
    ws.addEventListener('message', (ev) => {
      const msg = JSON.parse(ev.data);
      if (msg.id && pending.has(msg.id)) {
        const { resolve: res, reject: rej } = pending.get(msg.id);
        pending.delete(msg.id);
        if (msg.error) rej(new Error(JSON.stringify(msg.error)));
        else res(msg.result);
      }
    });
    ws.addEventListener('error', (e) => reject(new Error('ws error: ' + (e.message || 'unknown'))));
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
  var out = { steps: [] };
  function ink(cv){ var d=cv.getContext('2d').getImageData(0,0,48,48).data; var n=0; for(var i=3;i<d.length;i+=4) if(d[i]>0) n++; return n; }
  try {
    out.storageMode = Store.storageMode();
    out.storagePath = Store.storagePath();

    // 1) NEW PROJECT
    var created = await Store.createProject('DeviceAccept', { title:'设备验收', width:20, height:15 });
    out.steps.push('createProject -> ' + created.name);

    // 2) OPEN
    var opened = await Store.openProject('DeviceAccept');
    out.steps.push('openProject -> ' + JSON.stringify(opened));
    out.mapSize = Store.map.width + 'x' + Store.map.height;
    out.dataLen = Store.map.data.length;
    out.expectedLen = Store.map.width * Store.map.height * 4;

    // 3) TILESET
    var c = await Store.composeCurrentTileset();
    out.tilesetImagesDrawn = c ? c.imagesDrawn : 0;
    out.tilesetCanvas = c ? (c.canvas.width + 'x' + c.canvas.height) : null;
    out.kind16Ink = ink(Tileset.renderAutotile(c, 16, 0, 1));
    out.steps.push('composeTileset -> images=' + out.tilesetImagesDrawn + ' kind16ink=' + out.kind16Ink);

    // 4) PLACE TILE
    var tileId = MVFormat.makeAutotileId(16, 0);
    MVFormat.setTile(Store.map, 3, 4, 1, tileId);
    out.tileId = tileId;
    out.tileReadBack = MVFormat.getTile(Store.map, 3, 4, 1);
    out.rawIndex = Store.map.data[(1*Store.map.height + 4)*Store.map.width + 3];
    out.steps.push('placeTile -> ' + tileId + ' readback=' + out.tileReadBack);

    // 5) CREATE EVENT
    Store.map.events[1] = MVFormat.defaultEvent(1, 5, 6, 'DeviceEvent');
    out.steps.push('createEvent -> #1 DeviceEvent');

    // 6) SAVE (flushes to real Capacitor Filesystem)
    var rep = await Store.save();
    out.saveReport = rep;
    out.steps.push('save -> ' + JSON.stringify(rep));

    return JSON.stringify(out);
  } catch(e) {
    out.error = e.message + ' | ' + (e.stack||'').split('\\n')[1];
    return JSON.stringify(out);
  }
})()`;

const RELOAD_SCRIPT = `(async function(){
  var out = {};
  try {
    Store.close();                       // drop the in-memory mirror only
    var re = await Store.openProject('DeviceAccept');
    out.reopen = re;
    out.reopenTile = MVFormat.getTile(Store.map, 3, 4, 1);
    out.reopenEvent = Store.map.events[1] ? Store.map.events[1].name : null;
    out.reopenSize = Store.map.width + 'x' + Store.map.height;
    var parsed = JSON.parse(fs.readFileSync('Projects/DeviceAccept/data/Map001.json','utf8'));
    var orig = await (await fetch('_template/data/Map001.json')).json();
    var ko = Object.keys(orig).sort(), km = Object.keys(parsed).sort();
    out.schemaMatch = (JSON.stringify(ko) === JSON.stringify(km));
    out.missing = ko.filter(function(k){return km.indexOf(k)<0;});
    out.extra = km.filter(function(k){return ko.indexOf(k)<0;});
    out.projectsOnDevice = Store.listProjects().map(function(p){return p.name+'('+p.mapCount+')';});
    out.ok = out.reopenTile === 2816 && out.reopenEvent === 'DeviceEvent' && out.schemaMatch;
  } catch(e) { out.error = e.message; out.ok = false; }
  return JSON.stringify(out);
})()`;

(async () => {
  const targets = await listTargets();
  const page = targets.find((t) => t.type === 'page' && t.url.includes('localhost'));
  if (!page) { console.error('no page target; targets=' + JSON.stringify(targets.map(t=>t.url))); process.exit(1); }
  console.log('target:', page.url);

  const cdp = await connect(page.webSocketDebuggerUrl);

  const evaluate = async (expr) => {
    const r = await cdp.send('Runtime.evaluate', {
      expression: expr, awaitPromise: true, returnByValue: true,
    });
    if (r.exceptionDetails) throw new Error(JSON.stringify(r.exceptionDetails));
    return r.result.value;
  };

  const phase1 = JSON.parse(await evaluate(SCRIPT));
  console.log('\n=== PHASE 1 (fresh run, real device storage) ===');
  console.log(JSON.stringify(phase1, null, 2));

  // Simulate a full app restart: close the VFS, reload the page, reopen.
  await evaluate(`window.__reset = true; Store.close(); fs._clear(); 'cleared'`);
  await cdp.send('Page.reload', { ignoreCache: true });
  await new Promise((r) => setTimeout(r, 6000));

  const ready = await evaluate(`typeof Store !== 'undefined' && Store.ready ? 'ready' : 'notready'`);
  console.log('\nafter reload, Store:', ready);
  if (ready === 'ready') {
    const phase2 = JSON.parse(await evaluate(RELOAD_SCRIPT));
    console.log('\n=== PHASE 2 (after page reload = app restart) ===');
    console.log(JSON.stringify(phase2, null, 2));
    fs.writeFileSync(`${REPO}/evidence/device-acceptance.json`,
      JSON.stringify({ phase1, phase2 }, null, 2));
  }

  cdp.close();
  process.exit(0);
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });

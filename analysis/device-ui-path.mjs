// Drive the real UI path (click the project row like a user would) and
// screenshot, so the capture reflects genuine app behaviour rather than
// internal state poked by a test harness.
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

// Go back to the project list, then click the first project row for real.
const SCRIPT = `(async function(){
  var out = {};
  try {
    // Land on the project list the way the back button does.
    if (Store.dirty) await Store.save();
    document.getElementById('btn-back').click();
    await new Promise(function(r){ setTimeout(r, 400); });

    var rows = document.querySelectorAll('#project-list .proj');
    out.projectRows = rows.length;
    if (!rows.length) { out.error = 'no project rows'; return JSON.stringify(out); }

    // Real click -> openProject + composeTileset + openEditor.
    rows[0].click();
    await new Promise(function(r){ setTimeout(r, 2500); });

    out.activeScreen = (document.querySelector('.screen.active')||{}).id;
    out.title = document.getElementById('ed-project').textContent;
    out.mapOptions = document.getElementById('map-select').options.length;
    out.paletteItems = document.querySelectorAll('#palette .pal-item').length;
    out.bottomButtons = document.querySelectorAll('.bbtn').length;
    out.tilesetImages = Store.tilesetCanvas ? Store.tilesetCanvas.imagesDrawn : 0;
    out.zoom = E ? Math.round(E.scale*100)+'%' : '?';
    out.ok = out.activeScreen === 'editor' && out.paletteItems > 0 && out.mapOptions > 0 && out.bottomButtons === 6;
  } catch(e) { out.error = e.message; out.ok = false; }
  return JSON.stringify(out);
})()`;

(async () => {
  const targets = await (await fetch(`${CDP_HTTP}/json/list`)).json();
  const page = targets.find((t) => t.type === 'page' && t.url.includes('localhost'));
  const cdp = await connect(page.webSocketDebuggerUrl);
  const r = await cdp.send('Runtime.evaluate', { expression: SCRIPT, awaitPromise: true, returnByValue: true });
  const v = JSON.parse(r.result.value);
  console.log(JSON.stringify(v, null, 2));
  await new Promise((res) => setTimeout(res, 1500));
  cdp.close();
  process.exit(v.ok ? 0 : 1);
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });

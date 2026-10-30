// Real browser smoke test. Run against a fresh mock session: node scripts/ui-smoke.mjs http://localhost:8082
import { spawn } from "node:child_process";
import { mkdtemp, rm, mkdir, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import assert from "node:assert/strict";
const base = process.argv[2] || "http://localhost:8082";
const profile = await mkdtemp(join(tmpdir(), "eluno-ui-"));
const browser = spawn(
  process.env.UI_BROWSER ||
    "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
  [
    "--headless=new",
    "--disable-gpu",
    "--no-first-run",
    "--remote-debugging-port=9333",
    `--user-data-dir=${profile}`,
    "about:blank",
  ],
  { windowsHide: true, stdio: "ignore" },
);
let ws;
try {
  let targets;
  for (let i = 0; i < 100; i++) {
    try {
      targets = await (await fetch("http://127.0.0.1:9333/json")).json();
      break;
    } catch {
      await new Promise((r) => setTimeout(r, 100));
    }
  }
  assert(targets, "Browser did not start");
  ws = new WebSocket(
    targets.find((t) => t.type === "page").webSocketDebuggerUrl,
  );
  await new Promise((resolve, reject) => {
    ws.onopen = resolve;
    ws.onerror = reject;
  });
  let seq = 0;
  const requests = new Map();
  const errors = [];
  function cdp(method, params = {}) {
    return new Promise((resolve, reject) => {
      const id = ++seq;
      requests.set(id, { resolve, reject });
      ws.send(JSON.stringify({ id, method, params }));
    });
  }
  ws.onmessage = (event) => {
    const m = JSON.parse(event.data);
    if (m.id) {
      const p = requests.get(m.id);
      requests.delete(m.id);
      m.error ? p.reject(Error(m.error.message)) : p.resolve(m.result);
    } else if (m.method === "Page.javascriptDialogOpening") {
      cdp("Page.handleJavaScriptDialog", { accept: true });
    } else if (m.method === "Runtime.exceptionThrown") {
      errors.push(m.params.exceptionDetails.text);
    }
  };
  await cdp("Page.enable");
  await cdp("Runtime.enable");
  async function evaluate(expression) {
    const r = await cdp("Runtime.evaluate", {
      expression,
      returnByValue: true,
      awaitPromise: true,
    });
    if (r.exceptionDetails) throw Error(JSON.stringify(r.exceptionDetails));
    return r.result.value;
  }
  async function wait(expression) {
    for (let i = 0; i < 300; i++) {
      if (await evaluate(expression)) return;
      await new Promise((r) => setTimeout(r, 100));
    }
    throw Error(
      "Timed out: " +
        expression +
        "; " +
        (await evaluate("document.body.innerText")),
    );
  }
  async function click(id) {
    await evaluate(`document.getElementById(${JSON.stringify(id)}).click()`);
    await wait("document.body.getAttribute('aria-busy')!=='true'");
  }
  await cdp("Page.navigate", { url: base });
  await wait(
    "document.getElementById('status')?.textContent.startsWith('Ready.')",
  );
  assert.equal(await evaluate("document.title"), "Eluno POS | Point of Sale");
  assert.equal(
    await evaluate("document.querySelector('h1').textContent"),
    "Eluno POS",
  );
  assert.equal(
    await evaluate(
      "getComputedStyle(document.getElementById('invoice')).display",
    ),
    "none",
  );
  await evaluate(
    "document.getElementById('store').value='HSR';document.getElementById('store').dispatchEvent(new Event('change'))",
  );
  await wait("document.body.getAttribute('aria-busy')!=='true'");
  await click("lookup");
  assert.match(
    await evaluate("document.getElementById('customer').textContent"),
    /prescription/,
  );
  await evaluate(
    "document.querySelector('[data-type=frame]').value='91000003';document.querySelector('[data-type=lens]').value='91000083';document.querySelector('[data-type=lens]').dispatchEvent(new Event('change',{bubbles:true}))",
  );
  await evaluate(
    "document.getElementById('phone').value='+919000000001';document.getElementById('phone').dispatchEvent(new Event('input'))",
  );
  await click("quote");
  assert.match(
    await evaluate("document.getElementById('status').textContent"),
    /ADD/,
  );
  assert.equal(
    await evaluate("document.getElementById('charge').disabled"),
    true,
  );
  await evaluate(
    "document.getElementById('phone').value='+919000000005';document.getElementById('phone').dispatchEvent(new Event('input'))",
  );
  await click("lookup");
  await click("quote");
  assert.equal(
    await evaluate("document.getElementById('charge').disabled"),
    false,
  );
  assert.match(
    await evaluate("document.getElementById('quoteResult').textContent"),
    /Payable/,
  );
  await mkdir(".local", { recursive: true });
  const screenshot = await cdp("Page.captureScreenshot", {
    format: "png",
    captureBeyondViewport: true,
  });
  await writeFile(
    ".local/ui-review.png",
    Buffer.from(screenshot.data, "base64"),
  );
  await evaluate(`window.realFetch=window.fetch;window.fetch=(url,options)=>{
    if(url==='/api/checkouts'&&options?.method==='POST'){
      window.fetch=window.realFetch;return Promise.reject(new TypeError('Simulated network interruption'));
    }
    return window.realFetch(url,options);
  }`);
  await click("charge");
  assert.equal(await evaluate("operation.state"), "SUBMITTING");
  const savedReference = await evaluate("pending.reference");
  await click("resume");
  assert.equal(await evaluate("pending.reference"), savedReference);
  assert.equal(
    await evaluate("operation.state"),
    "COMPLETED",
    await evaluate("document.getElementById('status').textContent"),
  );
  const saleId = await evaluate("operation.orderId");
  assert(saleId);
  await cdp("Page.reload");
  await wait(
    "document.getElementById('status')?.textContent.startsWith('Ready.')",
  );
  assert.equal(await evaluate("operation.orderId"), saleId);
  assert.equal(
    await evaluate("document.getElementById('charge').disabled"),
    true,
  );
  await evaluate("document.getElementById('refundReason').value=''");
  await click("refund");
  assert.equal(await evaluate("refundPending"), null);
  await evaluate(
    "document.getElementById('refundReason').value='CUSTOMER_RETURN'",
  );
  await click("refund");
  assert.equal(
    await evaluate("operation.state"),
    "COMPLETED",
    await evaluate("document.getElementById('status').textContent"),
  );
  assert.equal(await evaluate("operation.kind"), "REFUND");
  await click("report");
  assert.match(
    await evaluate("document.getElementById('reportResult').textContent"),
    /Refunds/,
  );
  const reportText = await evaluate(
    "document.getElementById('reportResult').textContent",
  );
  assert.match(reportText, /Taxable sales/);
  assert.match(reportText, /By payment method/);
  assert.doesNotMatch(reportText, /NaN|undefined/);
  await click("newSale");
  assert.equal(await evaluate("pending"), null);
  assert.equal(await evaluate("document.getElementById('phone').value"), "");
  assert.equal(
    await evaluate("document.getElementById('discount').value"),
    "0",
  );
  assert.equal(
    await evaluate("document.querySelector('[data-type=frame]').value"),
    "",
  );
  assert.equal(
    await evaluate(
      "getComputedStyle(document.getElementById('invoice')).display",
    ),
    "none",
  );
  await cdp("Emulation.setDeviceMetricsOverride", {
    width: 768,
    height: 1024,
    deviceScaleFactor: 1,
    mobile: true,
  });
  assert.equal(
    await evaluate("document.documentElement.scrollWidth<=window.innerWidth"),
    true,
  );
  assert.deepEqual(errors, []);
  console.log(
    "Browser smoke passed: lookup → quote/offers → charge → reload recovery → refund → day-end report. Order " +
      saleId,
  );
} finally {
  ws?.close();
  browser.kill();
  await new Promise((r) => setTimeout(r, 1000));
  await rm(profile, {
    recursive: true,
    force: true,
    maxRetries: 5,
    retryDelay: 500,
  }).catch(() => {});
}

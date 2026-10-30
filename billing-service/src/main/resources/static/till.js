"use strict";
const $ = (id) => document.getElementById(id);
const money = (n) =>
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(
    n / 100,
  );
let catalog = [],
  quote = null,
  quotedCart = null,
  operation = null,
  pending = null,
  refundPending = null,
  busy = false,
  pairSequence = 0;
const savedKey = "eluno-pending-v1";

function newReference() {
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) =>
    byte.toString(16).padStart(2, "0"),
  ).join("");
}
try {
  const saved = JSON.parse(localStorage.getItem(savedKey) || "null");
  if (saved) {
    pending = saved.pending;
    refundPending = saved.refundPending;
    operation = saved.operation;
  }
} catch {}
function save() {
  localStorage.setItem(
    savedKey,
    JSON.stringify({ pending, refundPending, operation }),
  );
}
function say(message, error = false) {
  $("status").textContent = message;
  $("status").className = error ? "error" : "";
}
async function api(path, body) {
  const r = await fetch("/api" + path, {
    method: body === undefined ? "GET" : "POST",
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await r.json();
  if (!r.ok) throw new Error([data.error, ...(data.details || [])].join(" · "));
  return data;
}
function invalidate() {
  quote = null;
  quotedCart = null;
  $("charge").disabled = true;
  $("quoteResult").replaceChildren();
}
function show(id, data) {
  $(id).textContent = JSON.stringify(data, null, 2);
}
function option(value, label) {
  return new Option(label, value);
}
async function staff() {
  const rows = await api(
    "/stores/" + encodeURIComponent($("store").value) + "/staff",
  );
  $("staff").replaceChildren(
    ...rows.map((s) => option(s.id, s.id + " · " + s.role)),
  );
  $("manager").replaceChildren(
    option("", "Select manager"),
    ...rows.filter((s) => s.canOverride).map((s) => option(s.id, s.id)),
  );
}
function addPair() {
  if ($("pairs").children.length >= 10) throw new Error("Maximum ten pairs");
  const row = document.createElement("div");
  row.className = "pair";
  row.dataset.ref = "pair" + ++pairSequence;
  for (const type of ["frame", "lens"]) {
    const label = document.createElement("label");
    label.textContent = type === "frame" ? "Frame" : "Lens";
    const select = document.createElement("select");
    select.dataset.type = type;
    select.add(option("", "None"));
    catalog
      .filter((v) => v.lineType.toLowerCase() === type)
      .forEach((v) =>
        select.add(
          option(v.id, v.sku + " · " + v.title + " · " + money(v.pricePaise)),
        ),
      );
    label.append(select);
    row.append(label);
  }
  const remove = document.createElement("button");
  remove.textContent = "Remove pair";
  remove.onclick = () => {
    row.remove();
    invalidate();
  };
  row.append(remove);
  $("pairs").append(row);
  invalidate();
}
function cart() {
  const c = {
    phone: $("phone").value.trim(),
    storeCode: $("store").value,
    staffId: $("staff").value,
    channel: $("channel").value,
    paymentMethod: $("payment").value,
    pairs: [...$("pairs").children].map((r) => ({
      pairRef: r.dataset.ref,
      frameVariantId:
        Number(r.querySelector("[data-type=frame]").value) || null,
      lensVariantId: Number(r.querySelector("[data-type=lens]").value) || null,
    })),
  };
  const bps = Math.round(Number($("discount").value) * 100);
  if (bps) {
    c.managerDiscountBps = bps;
    c.managerId = $("manager").value;
    c.reasonCode = $("discountReason").value.trim();
  }
  return c;
}
function table(target, rows) {
  const t = document.createElement("table");
  for (const [label, value] of rows) {
    const tr = t.insertRow();
    tr.insertCell().textContent = label;
    tr.insertCell().textContent = value;
  }
  $(target).append(t);
}
function renderOperation() {
  if (!operation) return;
  show("operation", operation);
  $("refreshOperation").disabled = false;
  $("resume").disabled = ["COMPLETED", "CANCELLED"].includes(operation.state);
  if (operation.orderId) {
    $("orderId").value = operation.orderId;
    $("invoice").href = "/api/orders/" + operation.orderId + "/invoice";
    $("invoice").hidden = false;
  }
  save();
}
async function action(fn) {
  if (busy) return;
  busy = true;
  document.body.setAttribute("aria-busy", "true");
  try {
    await fn();
  } catch (e) {
    say(e.message, true);
  } finally {
    busy = false;
    document.body.removeAttribute("aria-busy");
    $("charge").disabled = !quote || !!pending || !!refundPending;
  }
}
function bind(id, fn) {
  $(id).onclick = () => action(fn);
}
$("cartSection").addEventListener("change", invalidate);
$("phone").oninput = () => {
  invalidate();
  $("customer").textContent = "Customer changed. Look up again.";
};
$("staff").onchange = invalidate;
$("store").onchange = () =>
  action(async () => {
    invalidate();
    await staff();
    say("Store changed. Cart will be revalidated.");
  });
bind("lookup", async () => {
  show(
    "customer",
    await api(
      "/customers/lookup?phone=" + encodeURIComponent($("phone").value.trim()),
    ),
  );
  say("Customer and latest valid prescription loaded.");
});
bind("addPair", async () => addPair());
bind("quote", async () => {
  invalidate();
  const c = cart();
  const q = await api("/cart/quote", c);
  if (JSON.stringify(cart()) !== JSON.stringify(c)) {
    throw new Error("Cart changed during validation. Validate again.");
  }
  quotedCart = c;
  quote = q;
  table("quoteResult", [
    ["MRP", money(q.grossPaise)],
    ["Discount", money(q.discountPaise)],
    ["Taxable", money(q.taxablePaise)],
    ["CGST", money(q.cgstPaise)],
    ["SGST", money(q.sgstPaise)],
    ["Payable", money(q.totalPaise)],
  ]);
  for (const o of q.offers) {
    const p = document.createElement("p");
    p.textContent =
      (o.applied ? "Applied: " : "Not applied: ") +
      o.offerId +
      " · " +
      money(o.amountPaise) +
      " · " +
      o.reason;
    $("quoteResult").append(p);
  }
  for (const w of q.warnings || []) {
    const p = document.createElement("p");
    p.textContent = w;
    $("quoteResult").append(p);
  }
  say(
    "Cart and prescription validated. Review offers and payable before charging.",
  );
});
bind("charge", async () => {
  if (pending || refundPending)
    throw Error("Resolve the existing operation first.");
  if (!quote || JSON.stringify(cart()) !== JSON.stringify(quotedCart))
    throw Error("Cart changed. Validate again.");
  pending = {
    reference: newReference(),
    cart: quotedCart,
    expectedTotalPaise: quote.totalPaise,
  };
  operation = { reference: "sale-" + pending.reference, state: "SUBMITTING" };
  save();
  renderOperation();
  operation = await api("/checkouts", pending);
  renderOperation();
  say(
    operation.state === "COMPLETED"
      ? "Sale completed. Invoice is ready."
      : "Sale pending: " + operation.state,
    operation.state !== "COMPLETED",
  );
});
bind("refreshOperation", async () => {
  operation = await api(
    "/operations/" + encodeURIComponent(operation.reference),
  );
  renderOperation();
  say("Operation: " + operation.state);
});
bind("resume", async () => {
  // A lost response before intent creation may leave no server operation to resume.
  // Resubmit the saved, identical request; the backend checks its stable reference.
  if (operation.state === "SUBMITTING" && refundPending) {
    operation = await api(
      "/orders/" + refundPending.orderId + "/refund",
      refundPending.body,
    );
  } else if (operation.state === "SUBMITTING" && pending) {
    operation = await api("/checkouts", pending);
  } else {
    operation = await api(
      "/operations/" + encodeURIComponent(operation.reference) + "/resume",
      {},
    );
  }
  renderOperation();
  say("Operation: " + operation.state);
});
bind("newSale", async () => {
  if (
    (pending || refundPending) &&
    operation?.state !== "COMPLETED" &&
    operation?.state !== "CANCELLED"
  )
    throw new Error(
      "Current operation is unresolved. Refresh or resume it first.",
    );
  pending = null;
  refundPending = null;
  operation = null;
  save();
  invalidate();
  $("operation").textContent = "Ready for next sale.";
  $("invoice").hidden = true;
  $("resume").disabled = true;
  $("refreshOperation").disabled = true;
  $("orderId").value = "";
  $("phone").value = "";
  $("customer").textContent = "Look up a customer before billing.";
  $("order").textContent = "";
  $("discount").value = "0";
  $("manager").value = "";
  $("discountReason").value = "";
  $("refundReason").value = "CUSTOMER_RETURN";
  $("channel").value = "walk_in";
  $("payment").value = "upi";
  $("pairs").replaceChildren();
  addPair();
  say("Ready for next sale.");
});
function orderId() {
  const id = $("orderId").value.trim();
  if (!/^\d+$/.test(id)) throw Error("Enter a numeric order ID.");
  return id;
}
bind("loadOrder", async () => {
  show("order", await api("/orders/" + orderId()));
  say("Order loaded.");
});
bind("refund", async () => {
  if (pending && operation?.state !== "COMPLETED")
    throw Error("Resolve pending sale first.");
  const id = orderId();
  if (!refundPending) {
    if (!$("staff").value || !$("refundReason").value.trim()) {
      throw Error("Select a salesperson and enter a refund reason.");
    }
    if (
      !confirm(
        "Refund the entire order " +
          id +
          "? Frames return to stock; custom lenses do not.",
      )
    )
      return;
    refundPending = {
      orderId: id,
      body: {
        reference: newReference(),
        staffId: $("staff").value,
        reasonCode: $("refundReason").value.trim(),
      },
    };
    operation = {
      reference: "refund-" + refundPending.body.reference,
      state: "SUBMITTING",
    };
    save();
    renderOperation();
  }
  if (refundPending.orderId !== id)
    throw new Error("Resolve the existing refund first.");
  operation = await api("/orders/" + id + "/refund", refundPending.body);
  renderOperation();
  say("Refund: " + operation.state, operation.state !== "COMPLETED");
});
bind("report", async () => {
  const r = await api(
    "/reports/day-end?storeCode=" +
      encodeURIComponent($("store").value) +
      "&date=" +
      $("reportDate").value,
  );
  $("reportResult").replaceChildren();
  table("reportResult", [
    ["Bills", r.billCount],
    ["Gross sales (before discounts)", money(r.grossPaise)],
    ["Discounts", money(r.discountsPaise)],
    ["Sales (including GST)", money(r.salesPaise)],
    ["Taxable sales (excluding GST)", money(r.taxablePaise)],
    ["Sales GST", money(r.taxPaise)],
    ["Refunds", money(r.refundsPaise)],
    ["Net sales (after refunds)", money(r.netPaise)],
    ["Average bill", money(r.averageBillPaise)],
    ["Unresolved operations", r.unresolvedOperations],
  ]);
  for (const [heading, splits] of [
    ["By payment method", r.paymentMethods],
    ["By sales channel", r.channels],
  ]) {
    const title = document.createElement("h3");
    title.textContent = heading;
    $("reportResult").append(title);
    table(
      "reportResult",
      Object.entries(splits || {}).flatMap(([name, amounts]) => [
        [name.replaceAll("_", " ") + " - sales", money(amounts.salesPaise)],
        [name.replaceAll("_", " ") + " - refunds", money(amounts.refundsPaise)],
        [name.replaceAll("_", " ") + " - net", money(amounts.netPaise)],
      ]),
    );
  }
  for (const issue of r.reconciliationIssues || []) {
    const warning = document.createElement("p");
    warning.textContent = issue;
    warning.className = "error";
    $("reportResult").append(warning);
  }
  say("Shopify day-end report loaded (" + r.timezone + ").");
});
$("reportDate").value = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Kolkata",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
}).format(new Date());
action(async () => {
  const [stores, variants] = await Promise.all([
    api("/stores"),
    api("/catalog"),
  ]);
  catalog = variants;
  $("store").replaceChildren(
    ...stores.map((s) => option(s.code, s.code + " · " + s.name)),
  );
  if (pending?.cart?.storeCode) $("store").value = pending.cart.storeCode;
  await staff();
  if (pending?.cart?.staffId) $("staff").value = pending.cart.staffId;
  addPair();
  renderOperation();
  say("Ready. Look up a mock customer to begin.");
});

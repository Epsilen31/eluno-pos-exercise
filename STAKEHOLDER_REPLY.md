Subject: Re: Replace Shopify POS — proposed v1, decisions and rollout

Hi everyone,

Shopify will remain the record for customers, inventory, orders, payments and refunds. The till will own prescription validation, offer evaluation, GST calculations, store invoice numbering and recovery of interrupted bills. I propose proving the complete billing/refund/reconciliation flow in one pilot store before committing all seven stores to 15 November.

**Decisions we need**

- **Rohan and Meena:** Does ten seconds include customer payment entry and terminal/network latency? I propose measuring confirmation-to-recorded-invoice separately from payment wait, tracking p95 and failures. An outage must leave a visible pending bill, never a false success. Please agree pilot acceptance criteria and who can pause rollout.
- **Meena and Vikram:** Retail's blanket stacking conflicts with non-stackable configured offers. For this exercise I will choose the lowest payable eligible combination, treating a non-stackable offer as exclusive across the cart; priority resolves ties and application order. Please confirm scope and allocation before rollout. Manager discounts are explicit requests up to 10%, require a manager identity and reason, and cannot silently apply to everyone. Direct manager approval can avoid an extra approval chain while preserving accountability.
- **Dr. Iyer:** Please confirm lens ranges, prescription validity and handling of external prescriptions with non-5-degree axes. We must never round a clinical value silently. The exercise will preserve external values, flag them for review and block missing/expired prescriptions or incompatible lenses. Which clinical reviewer owns exceptions? Remake eligibility, cost bearer and approval policy need agreement before automating remakes.
- **Vikram:** Please sign off invoice/credit-note prefixes, fiscal-year rollover, cancelled-number retention, effective-dated HSN rates and report definitions. My proposal: reserve numbers durably; retain failed/cancelled entries; never recycle a number. Report sales by billing date and refunds by refund date in IST, with gross, discounts and net separately. Historical refunds reverse original invoice tax, not today's rate.
- **Farhan:** Please confirm store/location mapping, return eligibility and stock ownership. Frame returns go to the billing store; custom lenses never restock. For online pickups, how do we link an existing paid order so that collecting it cannot bill or reserve stock twice?
- **Meena and Dr. Iyer:** For family bills, who is the payer versus the patient, and how are permissions and prescriptions linked per pair? One Shopify customer cannot represent three patients safely without explicit line-level ownership. I will support multiple pairs for one customer and defer multi-patient billing until that contract is agreed.

**What I will deliver in this exercise**

A Spring Boot application with a functional browser/tablet UI: phone/customer lookup, latest valid prescription, stocked frame/lens cart with clinical validation, data-driven offers, exact inclusive GST, terminal payment followed by Shopify order creation, durable per-store invoice allocation, full refund with credit-note reference and correct restocking, and Shopify-derived day-end reconciliation. Every order records store, staff and channel. Tests will cover rounding, offer eligibility/stacking, refund reversal and recovery behavior. A printable invoice and channel totals are small additions once Must is verified.

Cash sessions/paid-outs/close variance, partial refunds, family prescriptions, remakes and automated morning emails are deferred. They deserve explicit policies and reliable ledger semantics rather than a half-working flow. The daily report will provide the founder's bill count, average bill and discounts; scheduling email is a separate integration. Queue beverage fields and the footfall camera should be assigned separately and must not block billing. No AI-generated clinical or financial decisions are needed for v1.

**Recovery and production readiness**

A stable checkout reference and durable local state will track validation, payment and Shopify confirmation. Known throttles retry with backoff. An ambiguous write is reconciled before further action; where the provider cannot prove whether payment succeeded, the bill stops for operator reconciliation rather than charging again. A pending invoice remains accounted for. This is a safe exercise recovery model, not a promise of unrestricted offline billing.

To ship: Finance must approve sample invoices/credit notes and reconciliation; Optometry/lab must approve lens data and clinical exceptions; Retail supplies pilot staff, return policy and training; Ops validates store stock mappings and connectivity; platform owners provide authenticated APIs, payment status/idempotency contracts, Shopify permissions, webhook/event access, privacy controls and a migration baseline for invoice counters. We also need production authentication/roles, backup/restore, monitoring, printer testing, deployment ownership and rollback arrangements. I propose a pilot with daily Finance comparison, including outage/refund drills, before staged rollout to the remaining stores.

I acknowledge the revised UI requirement. I will send the current repository, stakeholder reply and Loom by today's email deadline, then provide the complete browser/tablet Must flow and a free-tier hosted demo against the supplied mock within 24 hours of your update. I will include a short README account of where AI helped and where verification showed its assumptions were wrong. The hosted exercise will use synthetic data and disclose its reset/persistence limits.

Thanks,
Abhishek

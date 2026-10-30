# Eluno POS — Spring Boot assignment

A local REST API replacing the billing slice of Shopify POS. Shopify remains the source of truth. A browser/tablet UI is included; no Shopify account or paid service is required for local use.

The Spring Boot application is entirely inside **`billing-service/`**. Open that folder as the Java/Maven project in your IDE. The outer folder contains the assignment pack, demo launcher, and submission documents.

```text
eluno-pos-exercise/
├── billing-service/             # Spring Boot project root
│   ├── .mvn/
│   ├── src/main/java/com/eluno/pos/
│   ├── src/main/resources/
│   ├── src/test/java/
│   ├── target/                  # Generated output; never edit files here
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   └── lombok.config
├── starter-kit/                 # Original mock and exercise data
├── brief/
├── docs/
├── scripts/
└── run.py                       # Starts the mock and nested application
```

Read [the stakeholder reply](STAKEHOLDER_REPLY.md) first. The revised brief requires the repository, stakeholder reply and Loom by the email deadline; the UI and hosted mock demo may follow within 24 hours of that email. The email timestamp is not available here.

## Run

Prerequisites: **JDK 21–25** and **Python 3.8+** on PATH. Maven is optional: the included Maven Wrapper downloads it. The first build requires internet access for dependencies. Python is only the supplied mock, launcher and demo; all billing logic is Java.

From the repository root:

```powershell
python run.py
```

On macOS/Linux use `python3 run.py`. This builds the jar, starts the original mock with **429/503 failures enabled**, starts the API at `http://localhost:8080`, and prints log/database paths. Ctrl+C stops both processes. `--no-flake` disables injected failures; `--no-build` uses the existing jar; `--port 8081 --mock-port 8788` changes ports.

In a second terminal:

```powershell
python scripts/demo.py
```

The demo verifies lookup, invalid progressive rejection, offers/tax, charge/order creation, repeated-checkout deduplication, full refund, restored frame stock and a Shopify-derived report. It saves the quote, invoice HTML, sale, refund and report under `.local/demo-*`. Open `invoice.html` in a browser to print it. For another port use `--base http://localhost:8081`.

**Mock lifecycle matters:** the supplied mock resets on restart, including payment history. Each managed launch therefore creates a fresh, separate H2 ledger under `.local/`; prior ledgers are retained. Do not pair an old ledger with a reset mock.

To test app-only restarts while preserving upstream state, keep the mock running independently:

```powershell
# Terminal 1: leave this running
python starter-kit/mock-apis/mock_server.py

# Terminal 2: stop/restart this command; same ledger, same still-running mock
python run.py --external-mock --database .local/persistent/till
```

## Test

```powershell
cd billing-service
.\mvnw.cmd test
```

Run these commands inside `billing-service`. On macOS/Linux: `sh mvnw test`. Installed Maven also works: `mvn test`. From the outer folder, use `mvn -f billing-service/pom.xml test`.

Tests cover exact seed-invoice tax, odd-paisa rounding, effective dates, offer selection/stacking/caps/allocation, manager permissions, prescription limits, original-tax refunds and IST reporting. Integration tests automatically launch the supplied **flaky** mock on an available port. They test real checkout/refund/inventory, repeated requests, concurrent requests, lost Shopify responses, payment uncertainty and operator-assisted recovery. JPA tests verify persisted invoice/audit state, duplicate references, transaction rollback on a unique invoice violation, and Lombok DTO JSON compatibility. Set `PYTHON` to your Python executable if needed. Test logs are in `billing-service/target/`; tests never use the normal file-backed database.

## API walkthrough

All response money fields ending in `Paise` are integer paise. Shopify decimal strings exist only in the adapter. Import [the Postman collection](docs/Eluno-Till.postman_collection.json), or use any HTTP client.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/health` | App readiness |
| GET | `/api/customers/lookup?phone=%2B919000000005` | Shopify customer, tags and latest valid SMT prescription |
| GET | `/api/stores` | Store IDs, codes and invoice prefixes |
| GET | `/api/stores/HSR/staff` | Staff and roles |
| GET | `/api/catalog` | Active frame/lens variants, lens type/index/coating and clinical limits |
| GET | `/api/stock?storeCode=HSR&variantId=91000003` | Store frame stock |
| POST | `/api/cart/quote` | Validate and compute offers/taxes without charging |
| POST | `/api/checkouts` | Persist intent, charge and create Shopify order |
| GET | `/api/orders/{id}` | Read order from Shopify |
| GET | `/api/orders/{id}/invoice` | Printable HTML with line tax/HSN and credit-note references |
| POST | `/api/orders/{id}/refund` | Full refund, credit note and correct restock |
| GET | `/api/reports/day-end?storeCode=HSR&date=2026-10-26` | Shopify totals, methods and channels |
| GET | `/api/operations` | Local invoice/credit-note ledger, including unresolved/cancelled entries |
| GET | `/api/operations/{reference}` | Persisted state and calculation snapshot |
| POST | `/api/operations/{reference}/resume` | Resume known-safe work or reconcile an uncertain Shopify result |
| POST | `/api/operations/{reference}/reconcile-payment` | Verify operator-supplied terminal evidence before continuing |
| POST | `/api/operations/{reference}/cancel` | Retain an uncharged READY sale as cancelled |
| GET | `/api/operations/{reference}/audit` | State transitions, actor, timestamp and reasons |

Quote body:

```json
{
  "phone": "+919000000005",
  "storeCode": "HSR",
  "staffId": "S105",
  "channel": "walk_in",
  "paymentMethod": "upi",
  "pairs": [{"pairRef": "pair1", "frameVariantId": 91000003, "lensVariantId": 91000083}]
}
```

Checkout body: `{"reference":"bill-001","cart": <the quote request above>,"expectedTotalPaise": <quote.totalPaise>}`. Use a stable reference and the identical body for retries. Changed payloads with the same reference are rejected. The returned operation reference has a `sale-` prefix. **Only `state: COMPLETED` means the invoice is confirmed.** A 200 response with another state is a durable pending operation, not a successful bill.

Refund body: `{"reference":"return-001","staffId":"S105","reasonCode":"CHANGED_MIND"}`. The returned reference has a `refund-` prefix. Retries reuse that reference; a second reference for the same order is rejected.

Reconcile-payment body: `{"chargeId":"chg_...","staffId":"S105","reasonCode":"VERIFIED_RECEIPT"}`. For a sale, the adapter verifies captured status, amount, checkout reference and no prior refund. For an uncertain refund, it verifies the original charge's fully refunded amount; the provider's unavailable refund ID is explicitly marked as status-confirmed. This endpoint never creates another payment. If evidence is unavailable or an uncertain Shopify write is absent from reads, the operation remains blocked for investigation. Cancel body: `{"staffId":"S105","reasonCode":"CUSTOMER_LEFT"}`; charged/uncertain operations cannot be cancelled this way.

Validation errors return 400, domain rejection 422, unavailable reads 503. Writes that have started return their saved state where possible; always inspect the operation after a client timeout. Other failures leave the pre-write intent available for recovery.

## Rules and decisions

- **Prescription:** latest by test date among valid, non-future prescriptions for this customer. Both eyes must fit sphere/cylinder limits. Progressive/bifocal needs ADD within configured bounds. Missing/expired prescriptions block lens billing with an actionable message. External non-5-degree axes are preserved and warned, never rounded; in-store non-grid axes block billing. Clinical approval of this external-axis policy is outstanding.
- **Pairs:** one frame and/or one lens line, quantity one, sharing a unique `pairRef`; repeat pairs to buy more. Multiple pairs use the same customer's prescription. Multi-patient family billing is deferred. Archived products, non-prescription frames paired with lenses, and insufficient aggregate stock are rejected.
- **Offers:** JSON files reload per quote, without rebuilding. Evaluate valid subsets (bounded to 16 rules). Any non-stackable rule is exclusive across the cart. Lowest payable combination wins; deterministic subset order derived from ascending priority/ID breaks ties. Within a combination apply in priority/ID order to remaining inclusive prices. Flat frame/lens offers apply per eligible line, pair offers per complete pair, cart offers once per cart. Second-pair offer targets the cheapest original-price lens once. Caps are per offer/cart. Allocate cart/pair discounts proportionally with largest-remainder allocation, stable line order for ties; never negative prices. Each rule gets an applied/rejected explanation. Unsupported rule keys fail closed.
- **Manager discount:** opt-in `managerDiscountBps` (1–1000), `managerId` and `reasonCode`. Manager must belong to this store and have override capability. It competes with other eligible combinations; it does not automatically replace a better offer. Staff/approver/reason remain recorded.
- **Tax:** exercise HSN rates with effective dates; exactly one matching rate required. After discount, taxable = inclusive × 10000 / (10000 + rate), half-up per line. CGST is half tax rounded half-up, SGST receives the remainder. No floating-point money. Refunds use original line amounts, unaffected by later tax/offer changes.
- **Invoices:** H2 transaction reserves per-store sequence and operation together; initialize above Shopify's historical maximum. Pending and cancelled numbers stay in the ledger and are never recycled. Credit notes have a separate `CN/` sequence and parent-invoice reference. Prefixes are supplied data; fiscal-year rollover needs Finance-approved provisioning.
- **Checkout safety:** persist each intent before a remote side effect. Known pre-execution mock 429s honor `Retry-After`; known terminal 503s retry with a bounded backoff. Successful charges are never resent on application retries. On ambiguous Shopify writes, scan for the stable reference before doing anything else. If absent, stop rather than guessing it is safe to recreate. Operator terminal evidence can recover uncertain payment success. All writes are serialized in one app process; H2 locks out another process using that file. This deliberately trades throughput for an understandable single-till exercise model.
- **Reporting:** Shopify reads only for sale/refund amounts; IST calendar dates. `grossPaise` is MRP before discounts; `salesPaise` is after-discount included-tax invoice value; `taxablePaise`/`taxPaise` are sale-only. Refunds are counted on refund date, including old invoices. Net fields subtract refunds and their stored tax. Bill count includes non-cancelled zero-value historical remakes, consistent with reading invoice records. Average bill is sales / bill count, half-up. Payment/channel splits expose sales, refunds and net. Unresolved local operations are disclosed separately, never silently added into Shopify totals.

## Architecture and scope

```text
com.eluno.pos
├── controller       # REST endpoints, grouped by feature
├── service          # Checkout, quote, offers, GST, refund, reporting and invoice logic
├── repository       # Spring Data JpaRepository interfaces and JSON rule loading
├── entity           # BillingOperation, InvoiceSeries and AuditEvent JPA entities
├── dto              # Typed internal/API data objects, one per file
│   └── request      # Validated request bodies
├── client           # Shopify, store-tool and payment-terminal interfaces
│   └── impl         # HTTP implementations that translate external API formats
├── validator        # Prescription compatibility checks
├── exception        # Business/upstream exceptions and global HTTP error handling
├── util             # Documented exact money conversions and allocation
└── config           # Spring configuration, including the IST clock
```

The flow is `controller → service → repository/client`. Controllers validate input and delegate; services contain business logic; repositories persist local data; clients translate external API shapes. Calculations use our own DTOs rather than Shopify JSON. Replacing Shopify means replacing `ShopifyClientImpl`, rather than rewriting GST, offers or prescription validation.

DTOs are ordinary Lombok classes with `@Data`, `@Builder`, `@NoArgsConstructor` and `@AllArgsConstructor`, with standard getters/setters such as `getVariant()` and `setPairRef(...)`. They are separate from persistence entities. Spring services/controllers use Lombok `@RequiredArgsConstructor` for constructor injection where appropriate.

Persistence uses **Spring Data JPA + Hibernate + H2**. `BillingOperation`, `InvoiceSeries` and `AuditEvent` are `@Entity` classes with explicit table/column mappings, IDs, and JPA-compatible no-argument constructors. Entities use `@Getter`/`@Setter` rather than `@Data` to avoid generated entity equality semantics. Repositories extend `JpaRepository`. `OperationPersistenceService` wraps invoice allocation, operation state and audit in short `@Transactional` methods; `InvoiceSeriesRepository` takes a pessimistic write lock on an existing store series. External terminal/Shopify calls remain outside those transactions. `schema.sql` initializes the schema and Hibernate validates it (`ddl-auto=validate`); startup never drops existing invoice history.

Code uses four-space Java formatting with explicit imports. `.editorconfig` supplies editor defaults; Spotless provides repeatable formatting:

```powershell
# Inside billing-service/
.\mvnw.cmd spotless:apply
.\mvnw.cmd spotless:check
```

`MoneyUtils` is limited to necessary arithmetic: `fromDecimalString("104.98")` returns `10498`, `toDecimalString(10498)` returns `"104.98"`, and `allocateProportionally` splits discounts while preserving every paisa. Clinical checks live in `PrescriptionValidator`; normal `if`/`throw` statements replace the earlier custom `require(...)` helper.

**Implemented:** all seven Must flows; channel metadata/reporting, basic printable HTML invoice, operation audit, multiple pairs for one patient, cancellations and operator-assisted payment recovery.

**Deferred:** cash-drawer sessions/paid-ins/paid-outs/variance, partial refunds, remakes, multi-patient family billing, automated email, AI stretch . No Shopify/webhook/clinical/terminal production integration is claimed. Hosting configuration is included; a live deployment has not yet been verified.

Known limits:

1. Local evaluation app: claimed staff IDs are checked against store roles, but are not authenticated identities. Production needs login/authorization, data protection, backup/restore, observability and retention controls. Do not use real customer data here.
2. Original mock is **unchanged**. Its terminal claims `order_ref` idempotency but actually creates new charges on repeated successful POSTs. It also lacks charge search by reference/refund-status APIs. Local state prevents repeats; lost payment responses require an operator receipt/status check. Seed historical orders have no corresponding live terminal charges, so their payment refunds cannot be completed through this mock. Refund an order created in the current mock session.
3. Shopify order-create has no server-enforced idempotency. This app guarantees it never blindly repeats an ambiguous create; an absent result remains pending. A production provider contract or reconciliation worker is needed to resolve proven non-commits automatically. No unrestricted offline checkout or ten-second SLA is claimed.
4. The exercise mock has no pagination cursors; its adapter scans with a high explicit limit and fails rather than truncating at that limit. Production needs cursor pagination, incremental reporting and authenticated API scopes. Reports and reconciliation currently scan all store history.
5. Stock check/payment/create are separate systems. External stock changes after capture can leave a PAID operation awaiting resolution. The supplied mock may partially decrement multi-line stock before a later line fails; it is not transactionally faithful. Production needs reservation/compensation and concurrent-provider testing. Our aggregate precheck and serialized writes handle local requests only.
6. H2 file mode is for one local process, not seven production stores. Multiple nodes need a shared transactional database with unique checkout keys, locking/outbox coordination and a reconciler. Legal invoice fields, registration/address details and tax policy require Finance review; HTML is an exercise invoice, not a certified production document.

See the [implementation walkthrough](docs/IMPLEMENTATION_GUIDE.md) and [AI usage disclosure](AI_USAGE.md).

## Browser UI and revised delivery

Run `python run.py`, then open **http://localhost:8080** on the same computer. The responsive screen calls the existing API for customer/Rx lookup, up to ten frame/lens pairs, validation, offer explanations, inclusive tax, payment, printable invoice, full refund and IST day-end reporting. Choose a store and salesperson, look up a mock phone, select products, validate, review payable and charge. Refund the newly created order; seed orders lack live terminal charges. Pending operations retain their reference in browser storage and can be refreshed/resumed. Only COMPLETED confirms success. After completing a refund, select Start next sale.

For tablet access on your LAN, set `SERVER_ADDRESS=0.0.0.0` before starting and use the computer's LAN address and port. This demo checks claimed staff roles but has no login: use synthetic data only.

### Free-tier hosting

`Dockerfile` packages the Java application and unchanged Python mock together. `render.yaml` describes one free Docker web service. Push this checkout to your repository, create a Render Blueprint from it, select the free plan and deploy. The health path is `/api/health`; the browser UI is `/`. The launcher uses Render's PORT and binds the app to all interfaces. No separate mock URL or database service is required.

The mock and its paired H2 ledger reset together on each container start; demo data is ephemeral. Free hosting can sleep and take time to wake. This is a single-instance exercise deployment. Container execution and a live hosted URL have not been verified in this workspace (Docker's daemon is unavailable and no hosting account is connected).

### AI assistance in this revision

AI helped build the browser forms against the existing Java API, preserve operation references across refreshes, and prepare deployment files. Earlier AI assumptions about the mock's HTTP body handling and shared test stock were wrong; integration runs exposed both. I treat generated code and deployment configuration as work to verify, not evidence that a hosted service works. The fuller history is in [AI_USAGE.md](AI_USAGE.md). This note was AI-drafted; I need to review and personalize it before submission.

### Verification of this revision

All 27 Java tests passed against the supplied mock. Packaging and Spotless verification passed after formatting TaxService (no calculation changes). `node scripts/ui-smoke.mjs http://localhost:8082` drove headless Edge through customer lookup, missing-ADD rejection, offers/quote, charge, page-reload recovery, full refund and day-end report, and checked for horizontal overflow at tablet width. Start a separate fresh app/mock session on that port first (`python run.py --port 8082 --mock-port 8789`). The script defaults to Edge on Windows; set UI_BROWSER to another Chromium executable when needed. The API demo also passed with flaky upstream responses enabled. Docker execution and hosted behavior remain unverified.

## Recording guide

Follow [the step-by-step recording script](RECORDING_SCRIPT.md) for preparation, what to say, exact browser clicks and what to show in the code walkthrough. Target 6?7 minutes; the maximum is eight minutes.

### Render startup troubleshooting

The managed mock is started through `scripts/start_mock.py`, which binds it to 127.0.0.1 without changing the supplied mock source. Only Spring Boot binds publicly. The Blueprint pins PORT to 10000; Docker allows 180 seconds for startup, caps Java heap at 256 MB and streams child-process logs into the hosting console. If a deploy fails, inspect the Java exception or memory termination in Render Logs. A successful image build alone does not verify application readiness.

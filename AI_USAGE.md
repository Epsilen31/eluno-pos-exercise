# AI usage disclosure

This implementation was created with substantial assistance from an AI coding agent in this workspace. The agent read the supplied brief, stakeholder thread, mock implementation and seed data, drafted the stakeholder reply, and generated the Java application, tests, launcher/demo and documentation. The technology choice was guided by the candidate's familiarity with Java/Spring Boot.

Useful contributions included identifying the 48/72-hour discrepancy, separating the domain from Shopify payloads, recognizing that money must remain integer paise, and inspecting the mock's terminal implementation rather than trusting its idempotency description.

Verification exposed an incorrect early assumption: a default streaming HTTP request would work with the supplied server. The mock only reads Content-Length; explicit byte bodies fixed that integration issue. An early integration-test sequence also consumed shared frame stock; cleanup was added instead of weakening stock validation. These are examples of why generated code needs real integration runs.

The automated tests include actual mock calls, exact tax fixtures, offer/refund calculations, duplicate requests and injected lost responses. Those checks provide evidence for the tested cases; they do not establish production readiness, legal compliance or a ten-second SLA.

Before submitting, the candidate should review and personalize the stakeholder reply, run tests/demo independently, and be able to explain the calculation and recovery code. Add your own review changes and any remaining concerns here. Do not claim the generated work was written without AI assistance.

Following the candidate's feedback, the agent reorganized the application into `billing-service/`, replaced record DTOs with Lombok JavaBeans, and replaced the JDBC ledger with JPA/Hibernate entities and repositories. Additional tests exercise actual JPA persistence and rollback behavior. These framework choices reflect the candidate's preferred Spring Boot conventions; they do not change Shopify's role as the system of record.

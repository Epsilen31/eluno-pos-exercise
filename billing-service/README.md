# Eluno billing service

Open this folder as the Spring Boot/Maven project. It contains the parent POM declaration, source, tests, Maven Wrapper, Lombok configuration and generated `target` directory.

Stack: Java 21, Spring Boot, Spring Web, Bean Validation, Lombok, Spring Data JPA, Hibernate and file-backed H2.

```powershell
# From this folder:
.\mvnw.cmd clean verify
.\mvnw.cmd spotless:apply

# Start both the application and the supplied Python mock:
python ../run.py
```

From another terminal at the outer assignment root, run `python scripts/demo.py`.

DTOs are Lombok JavaBean classes. Entities are separate `@Entity` classes, and Spring Data repositories extend `JpaRepository`. The local database contains operations, invoice counters and audit events; Shopify remains authoritative for orders/customers/inventory/payments/refunds.

Application configuration lives in `src/main/resources`. Do not edit generated files in `target/classes`.

See the [full run guide and implementation decisions](../README.md), [walkthrough](../docs/IMPLEMENTATION_GUIDE.md), and [stakeholder reply](../STAKEHOLDER_REPLY.md).

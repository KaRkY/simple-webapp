# Copilot Instructions

## Build & Test

```bash
# Build
./mvnw clean package

# Run all tests (requires Docker for Testcontainers)
./mvnw test

# Run a single test class
./mvnw test -Dtest=ApplicationIntegrationTests

# Run a single test method
./mvnw test -Dtest=ApplicationIntegrationTests#bootstrapsApplication

# Run app locally with Testcontainers (spins up Postgres + Redis automatically)
./mvnw spring-boot:test-run
```

> Tests marked `@Testcontainers(disabledWithoutDocker = true)` are skipped if Docker is unavailable.

## Architecture

This is a **Spring Modulith 2 (v2.0.3) modular monolith** with two domain modules: `order` and `inventory`. Modules communicate exclusively through **domain events** — no direct cross-module bean injection.

**Spring Modulith 2 starters in use:**
- `spring-modulith-starter-core` — module detection, boundary enforcement, `@ApplicationModuleListener`
- `spring-modulith-starter-jdbc` — JDBC-backed event publication journal (`event_publication` / `event_publication_archive`)
- `spring-modulith-starter-test` — `Scenario` DSL, `@EnableScenarios`, module integration test support
- `spring-modulith-actuator` — `/actuator/modulith` endpoint exposing module metadata
- `spring-modulith-observability` — distributed tracing spans per module interaction

All starters are version-managed via the `spring-modulith-bom` BOM (no individual version pins needed).

```
simple.simple_webapp/
├── order/           # Publishes OrderCompleted
└── inventory/       # Listens for OrderCompleted, publishes InventoryUpdated
```

**Event flow:**
1. `OrderManagement.complete()` (transactional) publishes `OrderCompleted`
2. Spring Modulith persists it to the `event_publication` table via JDBC
3. `InventoryManagement.on(OrderCompleted)` handles it asynchronously via `@ApplicationModuleListener`
4. On success, the event is archived to `event_publication_archive`

The `event_publication` / `event_publication_archive` tables (in `db/migration/__root/V1__init.sql`) are the event bus backbone — they enable at-least-once delivery and retry.

## Key Conventions

**Module boundaries:** Each module lives in its own sub-package. `@ApplicationModuleListener` (not `@EventListener`) must be used for cross-module event handling so Modulith can enforce boundaries and track publications.

**Domain events as records:** Events (`OrderCompleted`, `InventoryUpdated`) are Java records — immutable, no-arg-accessible fields. `OrderCompleted` carries `@DomainEvent` (jMolecules); use this annotation for events that cross module boundaries.

**Flyway migrations are per-module:** Place migrations in `db/migration/__root/` for shared/infrastructure schema, and `db/migration/<module>/` for module-specific schema. Module migration directories currently exist but are empty stubs.

**Null safety:** All packages declare `@NullMarked` (jSpecify) in `package-info.java`. New packages should follow this pattern.

**Test infrastructure:** `TestcontainersConfiguration` provides `@ServiceConnection` beans for PostgreSQL and Redis. All integration tests `@Import(TestcontainersConfiguration.class)` rather than managing containers themselves. Use `@Testcontainers(disabledWithoutDocker = true)` to make Docker-dependent tests skip gracefully.

**Scenario-based integration tests:** Use Spring Modulith's `@EnableScenarios` + `Scenario` parameter injection for event-driven test flows (stimulate → wait → assert on published events), as shown in `ApplicationIntegrationTests`.

**Module verification:** `ModularityTests` verifies architectural rules and regenerates module documentation on each test run. Run it after adding new modules or cross-module dependencies.

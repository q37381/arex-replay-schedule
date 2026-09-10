# AREX Storage Abstraction

## Scope

The schedule module's business layer depends only on storage-neutral repository contracts in
`com.arextest.schedule.dao`. Two backends implement those contracts and are selected by the
`arex.schedule.repository.type` property (default `mysql`, `mongodb` for the legacy edition),
mirroring the `arex.storage.repository.type` switch of arex-storage:

- `com.arextest.schedule.dao.mysql` — MyBatis mappers over the shared `arex_storage_db`
  database (tables defined in `resources/db/mysql/schema.sql`)
- `com.arextest.schedule.dao.mongodb` — legacy MongoTemplate adapters

## Contracts

The following storage contracts are defined:

- `ApplicationRepository`
- `SystemConfigurationRepository`
- `ReplayPlanRepository`
- `ReplayPlanActionRepository`
- `ReplayActionCaseItemRepository`
- `ReplayNoiseRepository`
- `ReplayCompareResultRepository`
- `ReplayBizLogRepository`

These interfaces define the business-facing persistence API and form the extension point for
future storage backends.

## Neutral Models

To remove Mongo query types from the domain model, the following storage-neutral models are added:

- `CaseQueryCondition`
  - Describes execution-context case filters without exposing `Criteria`.
  - Supports context identifier selection (`contextIdentifier` / `nullContextIdentifier`) and
    rerun action scoping (`planItemIds`).
- `ReplayActionCaseItemRepository.GroupCountRes`
  - Carries aggregation output (per-plan-item counts, per-case-type counts and earliest record
    times) needed by self-healing and progress logic.

## Backend Adapter Responsibility

Backend-specific concerns are limited to the adapter packages:

- `MongoTemplate` / `Criteria` / `Query` / `Aggregation` usage and Mongo-specific update path
  composition live under `dao.mongodb`; MyBatis mappers and the JSON/epoch-millis row mapping
  live under `dao.mysql`.
- collection document mapping under `model.dao.mongodb` is shared: both backends reuse the
  mongo POJOs as the neutral wire format, so conversion between them and the business models
  (MapStruct converters) stays backend-independent.

This keeps business services, execution context construction, auth initialization, and
desensitization configuration independent from backend APIs.

## Current Wiring

- Business services inject repository interfaces from `com.arextest.schedule.dao`.
- Backend selection: both implementations are annotated with
  `@ConditionalOnProperty(name = "arex.schedule.repository.type", havingValue = "mysql" | "mongodb")`.
  `MongodbConfiguration` is mongo-only, `ScheduleMysqlConfiguration` is mysql-only and builds the
  Hikari pool (`useAffectedRows=true` aligns update counts with mongo's `getModifiedCount()`).
  In mysql mode no Mongo dependency (neither driver connection nor `arex.mongo.uri`) is required:
  `WebSpringBootServletInitializer` excludes `MongoAutoConfiguration`,
  `MongoDataAutoConfiguration` and `MongoRepositoriesAutoConfiguration` (the mongo backend builds
  its own `MongoDatabaseFactory`/`MongoTemplate` in `MongodbConfiguration`, and the legacy Spring
  Data repository interfaces are gone), and the log4j2 `Mongo4` NoSql appender was removed in
  favour of the `RollingFile` appender, mirroring arex-storage's mysql edition.
- `AppAuthAspectExecutor` uses `SystemConfigurationRepository` instead of `MongoTemplate`.
- `DataDesensitizationConfiguration` (remote desensitization jar) is mongo-only; in mysql mode
  `ScheduleMysqlConfiguration` provides a null-jar `DesensitizationProvider`, matching the
  storage-mysql edition.
- `PlanExecutionContext` uses `CaseQueryCondition` instead of Mongo `Criteria`.
- Biz log query responses return `BizLog` rather than Mongo document classes.
- Nested documents (stage lists, noise item maps, `msgInfo`) are persisted as JSON columns and
  dates as epoch millis in the mysql backend; uniqueness of `replay_noise` uses a
  SHA-256-derived `operation_name_hash` column to stay within InnoDB index length limits, and
  `SELECT ... FOR UPDATE` inside `TransactionTemplate` (with deadlock retry) preserves the mongo
  upsert/merge semantics.

## Extension Rule

To add a new storage backend:

1. Implement the contracts in `com.arextest.schedule.dao`.
2. Keep backend-specific query and serialization logic inside the backend package only.
3. Do not leak backend-native query objects into `service`, `model`, `planexecution`, or `web`.
4. Reuse neutral models such as `CaseQueryCondition` for cross-backend filtering semantics.
5. Gate the backend wiring on `arex.schedule.repository.type` so only one backend is active.

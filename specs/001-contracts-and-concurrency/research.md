# Phase 0 Research: Contracts and Concurrency

All Technical Context unknowns are resolved below. Each decision records the choice, rationale, and the alternatives evaluated.

## R1. Backend stack

- **Decision**: Java 21 (LTS) with Spring Boot 3.3.x and Maven, packaged as independent Spring Boot services in a Maven multi-module monorepo.
- **Rationale**: Confirmed with the team. Java 21 gives virtual threads for blocking DynamoDB/SQS calls and mature Spring Boot/Spring Cloud AWS support. Maven multi-module lets the shared `common-contracts` jar be versioned once and consumed by every service.
- **Alternatives considered**: Node.js/NestJS, Python/FastAPI, Go — all viable, rejected to keep one strongly-typed language across services and because the team chose Java.

## R2. Resource identifier scheme

- **Decision**: Prefixed ULIDs, e.g. `evt_01J8Z...`, `res_01J8Z...`, `match_01J8Z...`, `slot_01J8Z...`. `event_id` and all resource ids are globally unique, lexicographically time-sortable, and carry a type prefix for operability/readability.
- **Rationale**: ULIDs are monotonic and collision-resistant without coordination, so multiple service instances can mint ids independently (Principle IV). Prefixed ids make logs and event traces self-describing, matching the requirement example (`evt_01`, `match_01`). The prefix is part of the contract; the ULID is the uniqueness mechanism.
- **Alternatives considered**: UUIDv4 (not sortable), UUIDv7 (sortable but a new dependency and no prefix), database sequences (centralized, breaks autonomy and offline id generation), Snowflake (needs worker-id coordination).

## R3. Event envelope and logical clock

- **Decision**: A canonical `EventEnvelope` carrying `event_id`, `type`, `timestamp` (UTC, ISO-8601), `service_id`, `logical_clock`, plus `schema_version`, `correlation_id`, `causation_id`, and a typed `payload`. Each service instance keeps a Lamport counter (`AtomicLong`); it increments on publish and updates to `max(local, received) + 1` on consume. Total ordering is `(timestamp, logical_clock, event_id)`.
- **Rationale**: Satisfies Principle IV and makes ordering independent of clock synchronization. `correlation_id`/`causation_id` support distributed tracing and consistency analysis; `schema_version` supports contract evolution.
- **Alternatives considered**: Wall-clock ordering (unsafe under skew), vector clocks (more precise causality but heavier and not required for per-resource ordering), no logical clock (fails FR-004/FR-005).

## R4. Event publication: transactional outbox via DynamoDB Streams

- **Decision**: The concurrency-critical writes and their resulting domain events are made atomic by enabling DynamoDB Streams on the state tables. A stream processor in each service maps stream records to event envelopes and publishes them to Amazon SNS; SNS fans out to SQS queues consumed by other services.
- **Rationale**: The state change and the event become observable together (no lost/duplicated domain events from a separate "write then publish" step), satisfying Principles III and IV without a second database commit. LocalStack supports DynamoDB Streams, SNS and SQS, so the same path is testable locally.
- **Alternatives considered**: Publish directly after the write (can lose an event if publish fails), a separate outbox table polled by a scheduler (extra table and latency), EventBridge (not mandated by the constitution; SNS is).

## R5. Court-slot reservation uniqueness

- **Decision**: `reservations` table with primary key `courtId` (partition) and `slotId` (sort). Creating a reservation is a `PutItem` with `ConditionExpression: attribute_not_exists(courtId) AND attribute_not_exists(slotId)` (equivalently `attribute_not_exists(PK)`), so at most one reservation per `(court, slot)` can exist. Cancellation is a conditional update/delete guarded by `attribute_exists` and the current `status`.
- **Rationale**: The condition is evaluated atomically by DynamoDB across all writers, so concurrent attempts serialize to exactly one success (Principle II, FR-012, SC-001). No distributed lock or external coordinator is needed.
- **Alternatives considered**: Unique index in Aurora (possible, but the constitution mandates DynamoDB conditional writes for concurrency), optimistic version column with retry loop (more round trips, still needs a unique constraint for the create case).

## R6. Match overbooking prevention

- **Decision**: `matches` table (PK `matchId`, attributes `maxParticipants`, `participantCount`, `status`) plus `participations` table (PK `matchId`, SK `playerId`, attributes `participationId`, `status`, `joinedAt`). Joining uses a `TransactWriteItems` with two condition-checked actions: (a) `Put` participation with `attribute_not_exists(playerId)` (dedup/idempotency per player), and (b) `Update` match with `ConditionExpression: participantCount < maxParticipants` and `ADD participantCount 1`. Leaving is a transaction that deletes the participation and conditionally decrements `participantCount`. When the increment makes `participantCount == maxParticipants`, the service emits `MATCH_FULL` in addition to `MATCH_UPDATED`.
- **Rationale**: A DynamoDB transaction makes the capacity check, the counter change and the participation insert a single serializable unit, preventing overbooking under concurrent joins (Principle II, FR-013, SC-002) and making retries idempotent (FR-017).
- **Alternatives considered**: Separate read-then-write (classic race, rejected), conditional update only (loses per-player dedup), storing participants as a set attribute on the match item (single-item condition works but limits per-participation metadata and query patterns).

## R7. Idempotent requests (client-supplied operation id)

- **Decision**: Every state-changing request carries an `Idempotency-Key` header (the client operation id, mapped to `operationId`). Before the mutation, the service performs a conditional `Put` into `idempotency` table keyed by `operationId` with `attribute_not_exists(operationId)`. If the put succeeds, the request proceeds; the resulting status and body are recorded against the key with a TTL. If the put fails, the stored response is replayed. TTL is 24 hours by default.
- **Rationale**: Gives at-most-once effects for retries/timeouts (FR-017, SC-008) even when the original response was lost, with bounded storage.
- **Alternatives considered**: Deriving idempotency from the target key (works for reservation create but not for cancellation/join), natural-key upserts (cannot replay the original response).

## R8. Idempotent event consumption and dedup

- **Decision**: Each consuming service owns a `processed_events` table keyed by `consumerId` + `eventId`. A consumer conditionally inserts the `eventId` before applying effects; a duplicate delivery is dropped. Ordering-sensitive handlers buffer events whose `logical_clock` is ahead of the expected sequence for a resource and re-drive them, or fall back to a per-resource version check.
- **Rationale**: SQS is at-least-once; dedup by `event_id` is exactly what Principle III and FR-010 require (SC-004). TTL keeps the table bounded.
- **Alternatives considered**: Relying on SQS FIFO dedup only (5-minute window, per-queue, not durable across restarts), exactly-once semantics (not offered by the platform).

## R9. API versioning

- **Decision**: Version through content negotiation: clients send and receive `application/vnd.jogafacil.v1+json`; the unversioned default resolves to the current stable version. Incompatible changes introduce `v2` media types while `v1` continues to be served. Paths remain exactly as specified (`/reservations`, `/matches/{id}/participations`).
- **Rationale**: Keeps the spec's resource paths stable while satisfying FR-007/FR-025 and Principle III. Documented in OpenAPI 3.1.
- **Alternatives considered**: Path versioning `/v1/...` (changes the spec's paths), header `X-API-Version` (less self-describing than media types), query parameter (caching-unfriendly).

## R10. Conflict and error model

- **Decision**: RFC 9457 (Problem Details) JSON body with `type`, `title`, `status`, `detail`, `instance`, plus domain extension members `code`, `correlationId`, `timestamp`. Concurrency outcomes use HTTP 409 with codes `RESERVATION_CONFLICT`, `MATCH_FULL`, and `MATCH_NOT_OPEN`; idempotent replay returns the original 2xx status. Validation errors use 400 with `VALIDATION_ERROR`.
- **Rationale**: A machine-readable, consistent conflict contract across services (FR-015, FR-025, SC-005) that is standard and easy for consumers and tests to assert on.
- **Alternatives considered**: Bare HTTP status only (not machine-readable), custom envelope (less standard, more documentation).

## R11. Local development and testing strategy

- **Decision**: Docker Compose runs LocalStack with a bootstrap script that creates DynamoDB tables, streams, the SNS topic, and SQS queues. Automated tests use Testcontainers with the LocalStack module. Real AWS is used only for deployment/demo. The same configuration is selected by Spring profiles (`local` vs `aws`).
- **Rationale**: Reproducible, cost-free concurrency experiments in CI while preserving fidelity; the AWS SDK endpoints point to LocalStack in the `local` profile. LocalStack supports all services used here (DynamoDB, Streams, SNS, SQS).
- **Alternatives considered**: Real AWS for tests (cost, credentials, flakiness), mocked repositories (would not exercise real conditional-write semantics, invalidating the core guarantee).

## R12. Persistence split (DynamoDB vs Aurora)

- **Decision**: Concurrency-critical, conditionally-written state lives in DynamoDB (reservations, participations, idempotency, processed events). Relational, non-contended metadata (users, establishments, courts, matches catalog) lives in Aurora Multi-AZ and is owned by the respective services. `match-service` keeps its authoritative capacity counter in DynamoDB, while its descriptive metadata lives in Aurora.
- **Rationale**: Honors the constitution's split between DynamoDB conditional writes (Principle II) and Aurora Multi-AZ persistence, and keeps each service owning its data (Principle I).
- **Alternatives considered**: All-DynamoDB (would deviate from the mandated Aurora usage), all-Aurora with `SELECT ... FOR UPDATE` (no longer the mandated conditional-write mechanism).

## R13. Observability of concurrency

- **Decision**: Micrometer counters/timers for `reservation.requests`, `reservation.conflicts`, `match.join.requests`, `match.conflicts`, `match.capacity_reached`, exported through the CloudWatch registry in AWS and a logging/simple registry locally. Structured JSON logs include `correlationId`, `operationId`, resource ids and conflict code; secrets and tokens are never logged.
- **Rationale**: Directly satisfies FR-019/FR-020, SC-007 and Principle VI, and provides the evidence needed for the consistency/fault experiments.
- **Alternatives considered**: Log-only observability (not queryable/alarmable), ad-hoc CloudWatch SDK calls (reinvents Micrometer).

## R14. Fault-tolerance groundwork

- **Decision**: All services are stateless with respect to request handling, expose `/actuator/health` and `/actuator/info`, and rely on ECS Fargate for task replacement/scaling. Interrupted operations are safe to retry because every mutation is conditional and idempotent.
- **Rationale**: Principle V requires continuity and state preservation; conditional writes plus idempotency keys mean a task that dies mid-request leaves no partial state and the client's retry is safe.
- **Alternatives considered**: In-memory coordination (breaks under multiple instances), background compensation jobs (not needed since operations are single-transaction).

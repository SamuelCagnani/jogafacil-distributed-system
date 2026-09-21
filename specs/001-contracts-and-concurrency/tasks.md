---
description: "Task list for Contracts and Concurrency"
---

# Tasks: Contracts and Concurrency

**Input**: Design documents from `/specs/001-contracts-and-concurrency/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Included. The project constitution makes concurrency, consistency and fault-tolerance tests mandatory whenever the corresponding mechanisms are implemented, so the concurrency/contract tests below are not optional.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Shared library: `libs/common-contracts/src/main/java/com/jogafacil/contracts/...`
- Services: `services/<service>/src/main/java/com/jogafacil/<service>/...`
- Tests: per-service `src/test/java/...` and the cross-service harness `tests/concurrency/`
- Infrastructure: `infra/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Create the Maven reactor parent `pom.xml` at the repository root (Java 21, Spring Boot 3.3 BOM, dependency management, modules `libs/common-contracts`, `services/reservation-service`, `services/match-service`, `tests/concurrency`)
- [ ] T002 [P] Create `libs/common-contracts/pom.xml`
- [ ] T003 [P] Create `services/reservation-service/pom.xml` depending on `common-contracts`
- [ ] T004 [P] Create `services/match-service/pom.xml` depending on `common-contracts`
- [ ] T005 [P] Create `tests/concurrency/pom.xml` (JUnit 5, Testcontainers, LocalStack, k6/Gatling binding)
- [ ] T006 [P] Create `infra/docker/docker-compose.yml` running LocalStack plus both services
- [ ] T007 [P] Create `infra/localstack/init/01-bootstrap.sh` creating the five DynamoDB tables and GSIs from data-model.md, enabling streams on `jogafacil-reservations` and `jogafacil-matches`, and creating the SNS topics and SQS queues from contracts/events/domain-events.md
- [ ] T008 [P] Create `infra/docker/Dockerfile` as a reusable multi-stage Java 21 build for services
- [ ] T009 [P] Configure `local` and `aws` Spring profiles (LocalStack endpoint `http://localhost:4566`, region) in `services/reservation-service/src/main/resources/application.yml` and `services/match-service/src/main/resources/application.yml`
- [ ] T010 [P] Configure Spotless and Checkstyle in the parent `pom.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared contracts library, cross-cutting configuration and test harness that MUST exist before any user story

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T011 Implement the `ResourceId` value type and validation regex `^[a-z]+_[0-9A-HJKMNP-TV-Z]{26}$` in `libs/common-contracts/src/main/java/com/jogafacil/contracts/id/ResourceId.java`
- [ ] T012 Implement `UlidGenerator` with prefixes `usr_`, `est_`, `court_`, `slot_`, `match_`, `part_`, `res_`, `evt_` in `libs/common-contracts/src/main/java/com/jogafacil/contracts/id/UlidGenerator.java` (depends on T011)
- [ ] T013 [P] Implement the `DomainEventType` enum (`MATCH_CREATED`, `MATCH_UPDATED`, `MATCH_FULL`, `MATCH_CANCELLED`, `RESERVATION_CREATED`, `RESERVATION_CANCELLED`) in `libs/common-contracts/src/main/java/com/jogafacil/contracts/event/DomainEventType.java`
- [ ] T014 [P] Implement `LogicalClock` (`next()`, `observe(remote)` = `max(local, remote) + 1`) in `libs/common-contracts/src/main/java/com/jogafacil/contracts/event/LogicalClock.java`
- [ ] T015 Implement the canonical `EventEnvelope` record with required fields `event_id`, `type`, `timestamp`, `service_id`, `logical_clock`, `schema_version`, `payload` and optional `correlation_id`, `causation_id` in `libs/common-contracts/src/main/java/com/jogafacil/contracts/event/EventEnvelope.java` (depends on T011, T013, T014)
- [ ] T016 [P] Implement `ProblemCode` (VALIDATION_ERROR, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, RESERVATION_CONFLICT, MATCH_FULL, MATCH_NOT_OPEN, IDEMPOTENCY_KEY_REUSED) and `ProblemDetail` in `libs/common-contracts/src/main/java/com/jogafacil/contracts/error/`
- [ ] T017 [P] Implement `ApiVersion`/`MediaTypes` constants and content-negotiation configuration for `application/vnd.jogafacil.v1+json` in `libs/common-contracts/src/main/java/com/jogafacil/contracts/api/`
- [ ] T018 Implement the shared `GlobalExceptionHandler` mapping conflicts and validation failures to RFC 9457 `ProblemDetail` bodies in `libs/common-contracts/src/main/java/com/jogafacil/contracts/error/GlobalExceptionHandler.java` (depends on T016)
- [ ] T019 [P] Implement AWS client beans (DynamoDB enhanced client, SNS, SQS; endpoint/region from profile) in `services/reservation-service/src/main/java/com/jogafacil/reservation/config/AwsConfig.java`
- [ ] T020 [P] Implement AWS client beans (DynamoDB enhanced client, SNS, SQS; endpoint/region from profile) in `services/match-service/src/main/java/com/jogafacil/match/config/AwsConfig.java`
- [ ] T021 [P] Configure structured JSON logging, correlation-id MDC filter and Micrometer in `services/reservation-service/src/main/java/com/jogafacil/reservation/config/ObservabilityConfig.java`
- [ ] T022 [P] Configure structured JSON logging, correlation-id MDC filter and Micrometer in `services/match-service/src/main/java/com/jogafacil/match/config/ObservabilityConfig.java`
- [ ] T023 Create the concurrency test harness (Testcontainers LocalStack lifecycle, HTTP clients, seed courts/slots/matches) in `tests/concurrency/src/test/java/com/jogafacil/concurrency/ConcurrencyFixture.java`

**Checkpoint**: Foundation ready - user stories can now begin in parallel

---

## Phase 3: User Story 1 - Reserve a court slot without double booking (Priority: P1) 🎯 MVP

**Goal**: `POST /reservations` confirms at most one reservation per `(court, time slot)` under concurrency; `DELETE /reservations/{reservationId}` atomically releases it; retries are idempotent.

**Independent Test**: Fire 100 simultaneous `POST /reservations` for the same slot with distinct idempotency keys; expect exactly one 201 and 99 × 409 `RESERVATION_CONFLICT`, exactly one stored item, and a stable replayed response on retry.

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T024 [P] [US1] Contract test validating `POST /reservations` and `DELETE /reservations/{reservationId}` against `contracts/openapi.yaml` in `services/reservation-service/src/test/java/com/jogafacil/reservation/ReservationContractTest.java`
- [ ] T025 [P] [US1] Concurrency integration test asserting exactly one confirmed reservation and no double booking in `tests/concurrency/src/test/java/com/jogafacil/concurrency/ReservationConcurrencyIT.java`
- [ ] T026 [P] [US1] Idempotency test (replay returns original 201; same key with different body returns 422 `IDEMPOTENCY_KEY_REUSED`) in `services/reservation-service/src/test/java/com/jogafacil/reservation/ReservationIdempotencyTest.java`

### Implementation for User Story 1

- [ ] T027 [P] [US1] Create the `Reservation` domain model (fields `reservationId`, `courtId`, `slotId`, `ownerId`, `matchId?`, `status`, `createdAt`, `cancelledAt?`, `version`; status enum `ACTIVE|CANCELLED`) in `services/reservation-service/src/main/java/com/jogafacil/reservation/domain/Reservation.java`
- [ ] T028 [P] [US1] Create the `ReservationStatus` enum in `services/reservation-service/src/main/java/com/jogafacil/reservation/domain/ReservationStatus.java`
- [ ] T029 [US1] Implement `ReservationRepository` with `PutItem` condition `attribute_not_exists(courtId) AND attribute_not_exists(slotId)` for create and `#status = :active` for cancel, plus the `byReservationId` GSI lookup, in `services/reservation-service/src/main/java/com/jogafacil/reservation/persistence/ReservationRepository.java` (depends on T027, T028)
- [ ] T030 [US1] Implement `IdempotencyStore` (conditional put `attribute_not_exists(operationId)`, `requestHash`, stored response, TTL 24h) in `services/reservation-service/src/main/java/com/jogafacil/reservation/persistence/IdempotencyStore.java`
- [ ] T031 [US1] Implement `ReservationService` create/cancel using the conditional repository and idempotency store, translating failed conditions into `RESERVATION_CONFLICT` in `services/reservation-service/src/main/java/com/jogafacil/reservation/domain/ReservationService.java` (depends on T029, T030)
- [ ] T032 [P] [US1] Create `ReservationCreateRequest` and `ReservationResponse` DTOs in `services/reservation-service/src/main/java/com/jogafacil/reservation/api/dto/`
- [ ] T033 [US1] Implement `ReservationController` (`POST /reservations`, `DELETE /reservations/{reservationId}`) requiring the `Idempotency-Key` header in `services/reservation-service/src/main/java/com/jogafacil/reservation/api/ReservationController.java` (depends on T031, T032)
- [ ] T034 [US1] Add bean validation and map failed conditions to HTTP 409 `RESERVATION_CONFLICT` (never 500) in `services/reservation-service/src/main/java/com/jogafacil/reservation/api/`
- [ ] T035 [US1] Add the `reservation.conflicts` Micrometer counter and structured conflict logs in `services/reservation-service/src/main/java/com/jogafacil/reservation/domain/ReservationService.java`
- [ ] T036 [US1] Expose reservation-service health/readiness via Spring Boot Actuator in `services/reservation-service/src/main/resources/application.yml`

**Checkpoint**: User Story 1 is fully functional and independently testable

---

## Phase 4: User Story 2 - Join a match without overbooking (Priority: P1)

**Goal**: `POST /matches/{matchId}/participations` confirms at most one join per remaining spot; `DELETE /matches/{matchId}/participations/{playerId}` releases a spot atomically; retries are idempotent.

**Independent Test**: With one remaining spot, fire 100 simultaneous joins with distinct keys; expect exactly one 201, 99 × 409 `MATCH_FULL`, `participantCount == maxParticipants`, and no growth on retry.

### Tests for User Story 2 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T037 [P] [US2] Contract test validating the participation endpoints against `contracts/openapi.yaml` in `services/match-service/src/test/java/com/jogafacil/match/ParticipationContractTest.java`
- [ ] T038 [P] [US2] Concurrency integration test asserting at most one acceptance on the last spot and `participantCount` never exceeding `maxParticipants` in `tests/concurrency/src/test/java/com/jogafacil/concurrency/MatchConcurrencyIT.java`
- [ ] T039 [P] [US2] Idempotency test for repeated join and leave in `services/match-service/src/test/java/com/jogafacil/match/ParticipationIdempotencyTest.java`

### Implementation for User Story 2

- [ ] T040 [P] [US2] Create the `Match` domain model (`matchId`, `courtId`, `slotId`, `maxParticipants`, `participantCount`, `status`, `version`; `maxParticipants` positive; `0 ≤ participantCount ≤ maxParticipants`) in `services/match-service/src/main/java/com/jogafacil/match/domain/Match.java`
- [ ] T041 [P] [US2] Create the `MatchStatus` (`OPEN|FULL|CANCELLED`) and `ParticipationStatus` (`ACTIVE|LEFT`) enums in `services/match-service/src/main/java/com/jogafacil/match/domain/`
- [ ] T042 [P] [US2] Create the `Participation` model (`matchId`, `playerId`, `participationId`, `status`, `joinedAt`, `leftAt?`) in `services/match-service/src/main/java/com/jogafacil/match/domain/Participation.java`
- [ ] T043 [US2] Implement `MatchRepository` join transaction: `Put` participation with `attribute_not_exists(playerId)` plus `Update` match with `participantCount < maxParticipants` and `ADD participantCount 1`, setting `status = FULL` when the count reaches the maximum, in `services/match-service/src/main/java/com/jogafacil/match/persistence/MatchRepository.java` (depends on T040, T041, T042)
- [ ] T044 [US2] Implement leave transaction (delete participation with `attribute_exists(playerId)` and conditional decrement) in `services/match-service/src/main/java/com/jogafacil/match/persistence/ParticipationRepository.java`
- [ ] T045 [US2] Implement `MatchService` join/leave translating condition failures into `MATCH_FULL` and `MATCH_NOT_OPEN` and enforcing idempotency in `services/match-service/src/main/java/com/jogafacil/match/domain/MatchService.java` (depends on T043, T044)
- [ ] T046 [US2] Create the join/leave DTOs and `ParticipationController` requiring the `Idempotency-Key` header in `services/match-service/src/main/java/com/jogafacil/match/api/` (depends on T045)
- [ ] T047 [US2] Add the `match.conflicts` and `match.capacity_reached` Micrometer counters and structured logs in `services/match-service/src/main/java/com/jogafacil/match/domain/MatchService.java`

**Checkpoint**: User Stories 1 and 2 both work independently

---

## Phase 5: User Story 3 - Trustworthy event identity and ordering (Priority: P2)

**Goal**: Every domain event carries a unique id and the canonical envelope; events are published via DynamoDB Streams → SNS → SQS; consumers are idempotent and ordering is reconstructed by logical clock.

**Independent Test**: Produce events from two service instances, publish duplicates and out-of-order deliveries, and verify every event validates against `contracts/events/envelope.schema.json`, ids are unique, duplicates cause no extra state change, and the stream can be totally ordered by `(timestamp, logical_clock, event_id)`.

### Tests for User Story 3 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T048 [P] [US3] Test validating every emitted event against `contracts/events/envelope.schema.json` in `tests/concurrency/src/test/java/com/jogafacil/concurrency/EventEnvelopeIT.java`
- [ ] T049 [P] [US3] Idempotent-consumer test proving duplicate delivery causes no extra state change in `tests/concurrency/src/test/java/com/jogafacil/concurrency/ConsumerDedupIT.java`
- [ ] T050 [P] [US3] Ordering test reconstructing a cross-instance event sequence without wall-clock reliance in `tests/concurrency/src/test/java/com/jogafacil/concurrency/EventOrderingIT.java`

### Implementation for User Story 3

- [ ] T051 [US3] Implement `ReservationEventPublisher` mapping `jogafacil-reservations` DynamoDB stream records to `RESERVATION_CREATED`/`RESERVATION_CANCELLED` envelopes and publishing to SNS in `services/reservation-service/src/main/java/com/jogafacil/reservation/event/ReservationEventPublisher.java` (depends on T015)
- [ ] T052 [US3] Implement `MatchEventPublisher` emitting `MATCH_CREATED`, `MATCH_UPDATED`, `MATCH_FULL`, `MATCH_CANCELLED` from match state transitions in `services/match-service/src/main/java/com/jogafacil/match/event/MatchEventPublisher.java` (depends on T015)
- [ ] T053 [US3] Implement `ProcessedEventStore` (conditional claim `attribute_not_exists(eventId)` keyed by `consumerId`+`eventId`, TTL 30d) in each service under `.../persistence/ProcessedEventStore.java`
- [ ] T054 [US3] Implement the idempotent SQS consumer for `MATCH_CANCELLED` and logical-clock `observe` in `services/reservation-service/src/main/java/com/jogafacil/reservation/event/MatchEventListener.java` (depends on T014, T053)
- [ ] T055 [US3] Implement the idempotent SQS consumer for `RESERVATION_CREATED` and logical-clock `observe` in `services/match-service/src/main/java/com/jogafacil/match/event/ReservationEventListener.java` (depends on T014, T053)
- [ ] T056 [US3] Wire SNS topics and SQS subscriptions (and extend `infra/localstack/init/01-bootstrap.sh`) in `services/*/src/main/java/.../config/EventingConfig.java`

**Checkpoint**: Event identity, ordering and dedup are verified

---

## Phase 6: User Story 4 - Integrate through stable, versioned contracts (Priority: P3)

**Goal**: Synchronous capabilities are versioned via `application/vnd.jogafacil.v1+json`, services never touch another service's storage, and services are referenced by stable logical names.

**Independent Test**: Negotiate `v1` on every endpoint, assert incompatible versions are rejected, assert no service reads another's DynamoDB table, and verify service references use logical names.

### Tests for User Story 4 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T057 [P] [US4] Media-type version negotiation test for both services in `tests/concurrency/src/test/java/com/jogafacil/concurrency/ApiVersioningIT.java`
- [ ] T058 [P] [US4] Architecture/data-ownership test asserting no cross-service table access in `tests/concurrency/src/test/java/com/jogafacil/concurrency/DataOwnershipTest.java`

### Implementation for User Story 4

- [ ] T059 [US4] Enforce `application/vnd.jogafacil.v1+json` negotiation on all controllers via the shared configuration from T017 in `libs/common-contracts/src/main/java/com/jogafacil/contracts/api/` and both services
- [ ] T060 [US4] Publish OpenAPI 3.1 documentation via springdoc at `/v3/api-docs` in both services' `application.yml`
- [ ] T061 [US4] Externalize inter-service references to logical service names (Cloud Map-ready, no fixed IPs) in both services' `application.yml` and `.../config/DiscoveryConfig.java`
- [ ] T062 [US4] Cross-service integration test exercising reservation ↔ match communication through contracts/events only in `tests/concurrency/src/test/java/com/jogafacil/concurrency/CrossServiceIT.java`

**Checkpoint**: All user stories are independently functional

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T063 [P] Run all `quickstart.md` scenarios A–E locally and record the observed results in `specs/001-contracts-and-concurrency/quickstart.md`
- [ ] T064 [P] Add CloudWatch metric filters and alarms for `RESERVATION_CONFLICT`, `MATCH_FULL` and `match.capacity_reached` in `infra/terraform/observability.tf`
- [ ] T065 [P] Write the concurrency/consistency experiment report in `docs/experimentos.md`
- [ ] T066 Security hardening review: verify secrets come from environment/Secrets Manager and never appear in code, logs or the repository, and record the review in `docs/experimentos.md`
- [ ] T067 Run `mvn verify` and the full `tests/concurrency` harness; fix all failures before completion
- [ ] T068 Review constitution adherence (Principles II, IV and V) and record the evidence in `docs/experimentos.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phases 3–6)**: All depend on Foundational completion
  - Can proceed in parallel if staffed, or sequentially in priority order (P1 → P1 → P2 → P3)
- **Polish (Phase 7)**: Depends on all targeted user stories being complete

### User Story Dependencies

- **US1 (P1)**: Starts after Phase 2; no dependency on other stories
- **US2 (P1)**: Starts after Phase 2; no dependency on US1
- **US3 (P2)**: Starts after Phase 2; emits events for the state changed by US1/US2 but its envelope/ordering/dedup guarantees are testable with synthetic events
- **US4 (P3)**: Starts after Phase 2; validates versioning and data ownership across US1–US3 endpoints

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Models before repositories/services
- Repositories/services before controllers
- Core implementation before integration
- Story complete and validated before the next priority

### Parallel Opportunities

- T002–T010 (Setup) run in parallel after T001
- T013, T014, T016, T017 and the config tasks T019–T022 run in parallel in Phase 2
- Once Phase 2 completes, US1 and US2 can be implemented in parallel by different developers
- Within each story, the `[P]` test and model tasks run in parallel
- `tests/concurrency` test tasks T025, T038, T048–T050, T057–T058, T062 can be authored in parallel

---

## Parallel Example: User Story 1

```bash
# Tests for User Story 1 (write first, must fail):
Task: "Contract test for POST/DELETE reservations in services/reservation-service/src/test/java/com/jogafacil/reservation/ReservationContractTest.java"
Task: "Concurrency test in tests/concurrency/src/test/java/com/jogafacil/concurrency/ReservationConcurrencyIT.java"
Task: "Idempotency test in services/reservation-service/src/test/java/com/jogafacil/reservation/ReservationIdempotencyTest.java"

# Models for User Story 1 (different files, parallel):
Task: "Create Reservation model in services/reservation-service/src/main/java/com/jogafacil/reservation/domain/Reservation.java"
Task: "Create ReservationStatus enum in services/reservation-service/src/main/java/com/jogafacil/reservation/domain/ReservationStatus.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (critical - blocks everything)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: run T025 and confirm exactly one reservation under concurrency
5. Demo the duplicate-reservation prevention experiment

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → validate → demo (MVP: no double booking)
3. US2 → validate → demo (no overbooking)
4. US3 → validate → demo (event identity, ordering, idempotent consumers)
5. US4 → validate → demo (versioned contracts, data ownership)
6. Polish → experiment report and quickstart evidence

### Parallel Team Strategy

1. Team completes Setup + Foundational together
2. Then: Developer A takes US1, Developer B takes US2; the `tests/concurrency` harness is shared
3. US3 and US4 proceed after the P1 stories, or in parallel if enough capacity

---

## Requirement Coverage

| Requirement | Tasks |
|-------------|-------|
| FR-001–FR-006 (identity/events) | T011–T015, T048, T050–T052, T054–T056 |
| FR-007–FR-011 (contracts/integration) | T017, T057, T059–T062 |
| FR-012, FR-014–FR-018 (reservation concurrency/idempotency) | T024–T035 |
| FR-013, FR-014–FR-018 (match concurrency/idempotency) | T037–T047 |
| FR-019–FR-020 (observability) | T021, T022, T035, T047, T064 |
| FR-021–FR-025 (concurrency-critical interfaces) | T033, T046, T059, T060 |

---

## Notes

- `[P]` tasks touch different files and have no incomplete dependencies
- `[Story]` labels map tasks to user stories for traceability
- Tests are mandatory here because the constitution requires concurrency/consistency evidence with every mechanism
- Commit after each task or logical group; never merge with mandatory tests failing
- Stop at any checkpoint to validate a story independently

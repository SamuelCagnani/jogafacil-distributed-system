# Implementation Plan: Contracts and Concurrency

**Branch**: `001-contracts-and-concurrency` | **Date**: 2026-09-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-contracts-and-concurrency/spec.md`

## Summary

Establish the platform's shared contracts and concurrency-control foundation: a canonical resource-identifier and event-envelope scheme with logical clocks, versioned synchronous interfaces, idempotent event consumption, and DynamoDB conditional-write/transactional guarantees that make court-slot reservations unique per (court, slot) and prevent match overbooking. Delivered as a Maven multi-module monorepo: a shared `common-contracts` library plus runnable `reservation-service` and `match-service`, validated locally against LocalStack and deployable to AWS ECS Fargate.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 3.3.x (Web, Validation, Actuator), AWS SDK for Java v2 (DynamoDB, SNS, SQS), Spring Cloud AWS 3.x, Micrometer (CloudWatch registry in AWS, simple registry locally), `com.github.f4b6a3:ulid-creator`, springdoc-openapi 2.x, Jackson, Maven.

**Storage**: Amazon DynamoDB for concurrency-critical state (reservations, participations, idempotency records, processed-event dedup) using conditional writes and `TransactWriteItems`; Amazon Aurora Multi-AZ (PostgreSQL-compatible) for relational metadata (users, establishments, courts, matches). This feature exercises the DynamoDB path only; Aurora is provisioned by the infrastructure specification.

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (LocalStack module) for DynamoDB/SQS/SNS, concurrency tests with `ExecutorService`/`CountDownLatch`/`Phaser`, OpenAPI contract validation, and k6 or Gatling for the 100+ simultaneous-request scenarios.

**Target Platform**: Linux containers (Docker) on Amazon ECS Fargate; local Docker Compose with LocalStack for development and CI.

**Project Type**: Multi-service backend monorepo (Java/Maven).

**Performance Goals**: Support 100+ concurrent conflicting attempts on a single court slot or match spot with exactly one success; p95 HTTP latency below 300 ms under the concurrency-test load.

**Constraints**: At-most-once side effects on retries; tolerate at-least-once, out-of-order event delivery; event ordering must not depend on synchronized wall clocks; no cross-service storage access; every confirmation must be conditional on expected state; secrets never in code or logs.

**Scale/Scope**: Foundation for the 5-service MVP (`user`, `court`, `match`, `reservation`, `notification`). This feature delivers the shared contracts library, the two concurrency-critical services (reservation, match), their DynamoDB tables, and the concurrency-test harness.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Pre-Research | Post-Design |
|-----------|------|--------------|-------------|
| I. Domain microservices | Independent deployable services; each owns its data; no service reads another's storage; stateless, multi-instance | PASS | PASS |
| II. Consistency under concurrency (non-negotiable) | Confirmations via conditional writes/transactions; reservations unique per (court, slot); match capacity never exceeded | PASS | PASS |
| III. Contractual communication | REST over HTTPS with versioned contracts; SQS/SNS for async; idempotent consumers; logical service names (Cloud Map) | PASS | PASS |
| IV. Unique identity & event ordering | Globally unique resource/event ids; canonical envelope (`event_id`, `type`, `timestamp`, `service_id`, `logical_clock`); logical-clock ordering | PASS | PASS |
| V. Fault tolerance | Stateless instances, health checks, metrics and alarms; automatic recovery provided by ECS/Auto Scaling | PASS | PASS |
| VI. Security & observability | Structured logs, metrics and alarms for requests/errors/conflicts; no secrets in code/logs; HTTPS/IAM/Secrets/KMS/WAF at deploy | PASS | PASS |
| Technology constraints | AWS ECS Fargate, API Gateway, App Mesh, DynamoDB conditional writes, SQS/SNS, Aurora Multi-AZ, Cloud Map, CloudWatch | PASS | PASS |

No violations; Complexity Tracking is intentionally empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-contracts-and-concurrency/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output
│   ├── openapi.yaml
│   ├── error-model.md
│   └── events/
│       ├── envelope.schema.json
│       └── domain-events.md
├── checklists/
│   └── requirements.md  # /speckit-specify output
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created here)
```

### Source Code (repository root)

```text
pom.xml                              # Maven reactor (parent build)

libs/
└── common-contracts/               # Shared contracts library (jar)
    └── src/
        ├── main/java/com/jogafacil/contracts/
        │   ├── id/                   # Prefixed ULID resource identifiers
        │   ├── event/                # Event envelope, event types, logical clock
        │   ├── error/                # Problem-details error model + domain codes
        │   └── api/                  # Versioning constants, shared DTOs
        └── test/java/com/jogafacil/contracts/

services/
├── reservation-service/            # Court-slot reservations (conditional writes)
│   └── src/
│       ├── main/java/com/jogafacil/reservation/
│       │   ├── api/                # REST controllers (POST/DELETE /reservations)
│       │   ├── domain/             # Reservation aggregate, conflict outcomes
│       │   ├── persistence/        # DynamoDB repositories, idempotency store
│       │   └── event/              # Stream-to-SNS publisher
│       ├── main/resources/
│       └── test/java/com/jogafacil/reservation/
└── match-service/                  # Match participation/vacancy control
    └── src/
        ├── main/java/com/jogafacil/match/
        │   ├── api/                # REST controllers (participations)
        │   ├── domain/             # Match aggregate, capacity rules
        │   ├── persistence/        # DynamoDB transaction repositories
        │   └── event/              # MATCH_* event publisher
        ├── main/resources/
        └── test/java/com/jogafacil/match/

tests/
└── concurrency/                    # Cross-service load/concurrency harness (k6/Gatling + JUnit)

infra/
├── docker/
│   ├── Dockerfile                  # Shared multi-stage build for services
│   └── docker-compose.yml          # LocalStack + services
├── localstack/                     # LocalStack init scripts (tables, topics, queues)
└── terraform/                      # AWS provisioning (ECS, DynamoDB, SNS/SQS, Cloud Map)
```

**Structure Decision**: Maven multi-module monorepo. `libs/common-contracts` is a versioned internal jar consumed by every service so identifiers, event envelope and error model stay identical across domains. Each service is an independent Spring Boot application owning its own DynamoDB tables. `tests/concurrency` holds the reproducible concurrency experiments required by the constitution. Infrastructure and the remaining three services are separate specifications, but the layout reserves their locations.

## Complexity Tracking

> No constitution violations. No entries required.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

# Quickstart: Contracts and Concurrency

Runnable validation guide for feature `001-contracts-and-concurrency`. It proves the concurrency guarantees and event contracts end to end against LocalStack. Implementation details live in `tasks.md` and the services' source.

## Prerequisites

- Java 21 and Maven 3.9+
- Docker Engine with Docker Compose v2
- `curl` and `jq`
- (Optional) `k6` for the load scenarios; otherwise the JUnit concurrency harness is used

## Setup

```bash
# 1. Start LocalStack and bootstrap DynamoDB tables, streams, topic and queues
docker compose -f infra/docker/docker-compose.yml up -d

# 2. Build the shared contracts library and services
mvn -q -DskipTests install

# 3. Run reservation-service and match-service against LocalStack (local profile)
mvn -q -pl services/reservation-service spring-boot:run -Dspring-boot.run.profiles=local
mvn -q -pl services/match-service        spring-boot:run -Dspring-boot.run.profiles=local
```

Wait until both `/actuator/health` endpoints report `UP`.

```bash
curl -s localhost:8081/actuator/health | jq .status   # reservation-service
curl -s localhost:8082/actuator/health | jq .status   # match-service
```

Seed one court and one time slot (court-service stub or the LocalStack seed script) and note the ids used below.

## Scenario A — Concurrent reservation of one slot (SC-001, US1)

Fire 100 simultaneous `POST /reservations` for the same `(courtId, slotId)`, each with a distinct `Idempotency-Key`.

Expected:

- Exactly one response is `201` with an `ACTIVE` reservation.
- The other 99 are `409` with `code = RESERVATION_CONFLICT`.
- Reading `jogafacil-reservations` shows exactly one item for `(courtId, slotId)`.
- The `reservation.conflicts` metric increases by 99.

## Scenario B — Concurrent join on the last match spot (SC-002, US2)

Create a match with `maxParticipants` = N, fill N-1 spots, then fire 100 simultaneous joins with distinct keys.

Expected:

- Exactly one join is `201`; the rest are `409 MATCH_FULL`.
- `participantCount` equals `maxParticipants` and never exceeds it.
- A `MATCH_FULL` event is produced exactly once.
- The `match.conflicts` metric increases by 99.

## Scenario C — Idempotent retries (SC-008, FR-017)

Replay Scenario A's winning request with its original `Idempotency-Key`.

Expected:

- The response is the stored `201` body with the same `reservationId`.
- No new item and no new event are created.
- Reusing the key with a different body returns `422 IDEMPOTENCY_KEY_REUSED`.

## Scenario D — Cancellation releases the slot (FR-018)

Cancel the winning reservation with `DELETE /reservations/{reservationId}`, then retry Scenario A for the same slot.

Expected:

- The `DELETE` returns `200` with `status = CANCELLED`; repeating it is a no-op returning the same state.
- A subsequent reservation on the freed slot returns `201`.
- A `RESERVATION_CANCELLED` event is published.

## Scenario E — Event envelope, ordering and dedup (SC-003, SC-004, SC-006)

Inspect the SQS queue LocalStack receives after Scenarios A–D.

Expected:

- Every message validates against `contracts/events/envelope.schema.json`.
- All `event_id` values are unique; all required fields are present.
- Messages can be ordered by `(timestamp, logical_clock, event_id)` deterministically.
- Redelivering any message to the consumer produces no additional state change (`jogafacil-processed-events` claim fails).

## One-command smoke check

```bash
mvn -q -pl tests/concurrency verify -Dlocalstack=true
```

The harness starts LocalStack via Testcontainers, seeds data, runs Scenarios A–E, and fails the build if any expected outcome is not observed.

## Troubleshooting

- **All requests succeed** — the DynamoDB condition on create is missing; check `attribute_not_exists` in the reservation repository.
- **Responses are 500 instead of 409** — conflict conditions must be translated to `RESERVATION_CONFLICT`/`MATCH_FULL`, not treated as infrastructure errors.
- **Duplicate events after retries** — the event id must be stable per domain occurrence and consumers must claim `(consumerId, eventId)` before applying effects.
- **LocalStack connection errors** — ensure the `local` profile points the AWS SDK endpoint at `http://localhost:4566` and that the bootstrap script created the tables/streams/queues.

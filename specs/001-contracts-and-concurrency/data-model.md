# Phase 1 Data Model: Contracts and Concurrency

The model is split between DynamoDB (concurrency-critical, conditionally written state) and Aurora Multi-AZ (relational metadata owned by each service). Identifiers are prefixed ULIDs (R2).

## Shared value objects (`libs/common-contracts`)

### ResourceId
- `value` (string, required): `<prefix>_<ULID>`, e.g. `res_01J8Z9W2K3`.
- Known prefixes: `usr_`, `est_`, `court_`, `slot_`, `match_`, `part_`, `res_`, `evt_`.
- Validation: immutable, non-blank, matches `^[a-z]+_[0-9A-HJKMNP-TV-Z]{26}$`.

### EventEnvelope
| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `event_id` | ResourceId (`evt_`) | yes | unique; dedup key for consumers |
| `type` | enum (see events) | yes | e.g. `RESERVATION_CREATED` |
| `timestamp` | string (ISO-8601 UTC) | yes | `Instant` truncated to milliseconds |
| `service_id` | string | yes | logical name, e.g. `reservation-service` |
| `logical_clock` | long | yes | monotonic per service instance |
| `schema_version` | integer | yes | event schema version, starts at `1` |
| `correlation_id` | string | no | request/flow correlation |
| `causation_id` | ResourceId (`evt_`) | no | id of the event that caused this one |
| `payload` | object | yes | type-specific body |

Validation: no event is published unless `event_id`, `type`, `timestamp`, `service_id` and `logical_clock` are present (FR-002/FR-003); total order is `(timestamp, logical_clock, event_id)`.

### LogicalClock
- Per-instance `AtomicLong`.
- `next()`: local value `+ 1` on publish.
- `observe(remote)`: `max(local, remote) + 1` on consume.
- Persistence: not required; causality is reconstructed from the envelope, and per-resource ordering is additionally guarded by a `version` attribute (below).

## DynamoDB tables

### `jogafacil-reservations`
Guarantees reservation uniqueness per `(courtId, slotId)` (FR-012).

| Attribute | Type | Key | Notes |
|-----------|------|-----|-------|
| `courtId` | S | PK | `court_...` |
| `slotId` | S | SK | `slot_...` |
| `reservationId` | S | — | `res_...` |
| `ownerId` | S | — | `usr_...` requesting party |
| `matchId` | S | — | optional `match_...` reference |
| `status` | S | — | `ACTIVE` \| `CANCELLED` |
| `operationId` | S | — | idempotency key of the creating request |
| `createdAt` | S | — | ISO-8601 UTC |
| `cancelledAt` | S | — | ISO-8601 UTC, optional |
| `version` | N | — | incremented per state transition |

Create condition: `attribute_not_exists(courtId) AND attribute_not_exists(slotId)`.
Cancel condition: `attribute_exists(courtId) AND attribute_exists(slotId) AND #status = :active`.

GSIs:
- `byReservationId` (PK `reservationId`) — direct lookup for `DELETE /reservations/{reservationId}` and by-owner queries.

### `jogafacil-matches`
Authoritative capacity counter for a match (FR-013).

| Attribute | Type | Key | Notes |
|-----------|------|-----|-------|
| `matchId` | S | PK | `match_...` |
| `courtId` | S | — | `court_...` |
| `slotId` | S | — | `slot_...` |
| `maxParticipants` | N | — | configured capacity |
| `participantCount` | N | — | current confirmed participants |
| `status` | S | — | `OPEN` \| `FULL` \| `CANCELLED` |
| `version` | N | — | incremented per transition |

Join condition (inside transaction): `participantCount < maxParticipants` and `#status = :open` with `SET participantCount = participantCount + :one, version = version + :one`; when the result equals `maxParticipants` the service also sets `status = FULL`.

### `jogafacil-participations`
Per-player participation; dedup unit for join/leave (FR-013/FR-017).

| Attribute | Type | Key | Notes |
|-----------|------|-----|-------|
| `matchId` | S | PK | `match_...` |
| `playerId` | S | SK | `usr_...` |
| `participationId` | S | — | `part_...` |
| `status` | S | — | `ACTIVE` \| `LEFT` |
| `joinedAt` | S | — | ISO-8601 UTC |
| `leftAt` | S | — | ISO-8601 UTC, optional |

Join condition: `attribute_not_exists(playerId)` (a repeated join is a duplicate and returns the recorded result). Leave condition: `attribute_exists(playerId) AND #status = :active`.

### `jogafacil-idempotency`
Replays the original response for a repeated operation id (R7, FR-017).

| Attribute | Type | Key | Notes |
|-----------|------|-----|-------|
| `operationId` | S | PK | client `Idempotency-Key` |
| `requestHash` | S | — | hash of method+path+body; detects key reuse with different payload |
| `responseStatus` | N | — | stored HTTP status |
| `responseBody` | S | — | stored JSON (or S3 pointer if large) |
| `resourceId` | S | — | created/affected resource, if any |
| `createdAt` | S | — | ISO-8601 UTC |
| `ttl` | N | — | epoch seconds, default now + 24h |

Claim condition: `attribute_not_exists(operationId)`. Reuse with a different `requestHash` returns 422 `IDEMPOTENCY_KEY_REUSED`.

### `jogafacil-processed-events`
Consumer-side dedup by event id (FR-010).

| Attribute | Type | Key | Notes |
|-----------|------|-----|-------|
| `consumerId` | S | PK | e.g. `notification-service` |
| `eventId` | S | SK | `evt_...` |
| `processedAt` | S | — | ISO-8601 UTC |
| `ttl` | N | — | epoch seconds, default now + 30d |

Claim condition: `attribute_not_exists(eventId)`; a failed claim means the event was already applied.

## Aurora Multi-AZ schema (owned by service specs)

Provisioned by the infrastructure specification; this feature only reads/writes `courts` and `time_slots` references and the `matches` metadata row.

- `users(id, email, display_name, role, status, created_at)` — `user-service`.
- `establishments(id, owner_id, name, status)` / `courts(id, establishment_id, name, sport, status)` — `court-service`.
- `time_slots(id, court_id, starts_at, ends_at, status)` — `court-service`; referenced by reservations.
- `matches(id, court_id, slot_id, organizer_id, max_participants, status, created_at)` — descriptive metadata mirroring the DynamoDB capacity record.

## Relationships

```text
Establishment 1─* Court 1─* TimeSlot
Court + TimeSlot ──0..1── Reservation ──0..1── Match
Match 1─* Participation *─1 User
Reservation 1─* DomainEvent   (via DynamoDB Streams + SNS)
Match       1─* DomainEvent   (MATCH_CREATED/UPDATED/FULL/CANCELLED)
```

## State transitions

- **Reservation**: `ACTIVE → CANCELLED` (terminal). Re-creating a cancelled `(court, slot)` is allowed and produces a new `reservationId`; the previous record is retained for audit.
- **Participation**: `ACTIVE → LEFT` (terminal) with `(matchId, playerId)` recreated on rejoin as a new claim while the retained row keeps history.
- **Match**: `OPEN → FULL → OPEN` as participants join/leave; `OPEN|FULL → CANCELLED` (terminal).

## Validation rules

1. A reservation requires an existing `courtId` and `slotId` and an authenticated `ownerId`; `matchId` is optional.
2. `maxParticipants` is a positive integer; `participantCount` is `0 ≤ count ≤ maxParticipants` at all times.
3. Every state-changing request requires a non-blank `Idempotency-Key`.
4. Event envelope fields listed as required above must be present before publication.
5. `logical_clock` is a non-negative long.
6. Cancellation of an already-cancelled reservation or leave from an already-left participation is a no-op that returns the current state (idempotent).

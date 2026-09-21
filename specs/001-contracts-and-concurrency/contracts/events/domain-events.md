# Domain Events

Every event uses the canonical envelope in [envelope.schema.json](./envelope.schema.json). Events are published to Amazon SNS (topic per service) from DynamoDB Streams and fanned out to SQS queues. Delivery is at-least-once and possibly out of order; consumers deduplicate by `event_id`.

Ordering across events is determined by `(timestamp, logical_clock, event_id)`.

## Event catalog

| Type | Producer | Payload fields | Consumers | Notes |
|------|----------|----------------|-----------|-------|
| `MATCH_CREATED` | match-service | `matchId`, `courtId`, `slotId`, `organizerId`, `maxParticipants`, `status` | notification-service, later analytics | Emitted when a match becomes `OPEN`. |
| `MATCH_UPDATED` | match-service | `matchId`, `participantCount`, `maxParticipants`, `status`, `changedFields[]` | notification-service | Emitted on any participant-count or status change other than full/cancel. |
| `MATCH_FULL` | match-service | `matchId`, `participantCount`, `maxParticipants` | notification-service, later matchmaking | Emitted exactly when `participantCount` reaches `maxParticipants`. |
| `MATCH_CANCELLED` | match-service | `matchId`, `reason` | reservation-service, notification-service | Emitted when the match is cancelled. |
| `RESERVATION_CREATED` | reservation-service | `reservationId`, `courtId`, `slotId`, `ownerId`, `matchId?` | notification-service, match-service | Emitted on a confirmed reservation. |
| `RESERVATION_CANCELLED` | reservation-service | `reservationId`, `courtId`, `slotId`, `reason?` | notification-service | Emitted when an active reservation is cancelled. |

## Producer rules

1. Never publish an event without all required envelope fields (FR-002/FR-003).
2. `event_id` is minted once per domain occurrence and is stable across retries of the publishing step.
3. Increment the Lamport clock on every publish; include the current `correlation_id` when acting within a client request.
4. Publish at most one event per state transition; the stream record maps 1:1 to an event.
5. On consume, update the clock to `max(local, event.logical_clock) + 1`.

## Consumer rules

1. Claim `(consumerId, eventId)` in `jogafacil-processed-events` with a conditional write before applying effects; if the claim fails, the event is a duplicate and is dropped (FR-010).
2. Handlers must be safe to run after a crash between claim and effect (use idempotent, conditional writes on the target state).
3. If an event references a resource that has not yet been observed (out-of-order arrival), either buffer it by `logical_clock` or apply it once the prerequisite exists; never corrupt state.

## Example

```json
{
  "event_id": "evt_01J8Z9W3Q4K7M2P6R8T0V1X2Y3",
  "type": "RESERVATION_CREATED",
  "timestamp": "2026-09-18T20:00:00.123Z",
  "service_id": "reservation-service",
  "logical_clock": 42,
  "schema_version": 1,
  "correlation_id": "8f1c2a6e-5d3b-4a1e-9c77-1f0d2b7a9e11",
  "payload": {
    "reservationId": "res_01J8Z9W2K3N5Q7S9U1W3Y5A7C9",
    "courtId": "court_01J8Z9W1A2B3C4D5E6F7G8H9J0",
    "slotId": "slot_01J8Z9W1K2L3M4N5P6Q7R8S9T0",
    "ownerId": "usr_01J8Z9W1Z2Y3X4W5V6U7T8S9R0",
    "matchId": "match_01J8Z9W1M2N3P4Q5R6S7T8U9V0"
  }
}
```

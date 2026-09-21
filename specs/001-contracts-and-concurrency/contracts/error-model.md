# Error Model

All error responses use RFC 9457 Problem Details with the media type `application/vnd.jogafacil.v1+json`. Concurrency outcomes are always explicit and machine-readable (FR-015, FR-025, SC-005).

## Shape

| Member | Type | Required | Description |
|--------|------|----------|-------------|
| `type` | URI | yes | Problem category, e.g. `https://jogafacil.example.com/problems/conflict`. |
| `title` | string | yes | Human-readable summary. |
| `status` | integer | yes | HTTP status code. |
| `detail` | string | no | Human-readable explanation for this occurrence. |
| `instance` | string | no | Request path where the problem occurred. |
| `code` | string | yes | Stable domain code (table below); this is what clients and tests branch on. |
| `correlationId` | string | no | Trace correlation id. |
| `timestamp` | string | yes | UTC ISO-8601 instant. |

## Domain codes

| Code | HTTP | Meaning | Client action |
|------|------|---------|---------------|
| `VALIDATION_ERROR` | 400 | Malformed or invalid request fields. | Fix request; do not retry unchanged. |
| `UNAUTHORIZED` | 401 | Missing or invalid authentication. | Re-authenticate. |
| `FORBIDDEN` | 403 | Authenticated caller lacks the role. | Do not retry. |
| `NOT_FOUND` | 404 | Court, slot, match, player or reservation does not exist. | Do not retry. |
| `RESERVATION_CONFLICT` | 409 | The court slot was reserved (possibly concurrently). | Do not retry; pick another slot. |
| `MATCH_FULL` | 409 | No spots remain in the match. | Do not retry; pick another match. |
| `MATCH_NOT_OPEN` | 409 | Match is cancelled or otherwise not accepting joins. | Do not retry. |
| `IDEMPOTENCY_KEY_REUSED` | 422 | The same `Idempotency-Key` was used with a different request payload. | Use a new key. |

## Idempotent replay

A retried request with the same `Idempotency-Key` and the same payload does **not** produce a Problem response. The service replays the originally recorded HTTP status and body (including a previously returned 409) so that the client observes a stable outcome (FR-017, SC-008).

## Example

```http
HTTP/1.1 409 Conflict
Content-Type: application/vnd.jogafacil.v1+json

{
  "type": "https://jogafacil.example.com/problems/conflict",
  "title": "Reservation conflict",
  "status": 409,
  "detail": "Court slot is no longer available.",
  "instance": "/reservations",
  "code": "RESERVATION_CONFLICT",
  "correlationId": "8f1c2a6e-5d3b-4a1e-9c77-1f0d2b7a9e11",
  "timestamp": "2026-09-18T20:00:00.123Z"
}
```

## Logging and metrics

- Every conflict emits a counter increment (`reservation.conflicts` or `match.conflicts`) tagged by `code` (FR-019).
- Logs are structured JSON and include `correlationId`, `operationId`, the affected resource ids and the `code`; authentication tokens and secrets are never logged (FR-020).

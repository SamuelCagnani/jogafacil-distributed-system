# Feature Specification: Contracts and Concurrency

**Feature Branch**: `001-contracts-and-concurrency`

**Created**: 2026-09-18

**Status**: Draft

**Input**: User description: "001-contracts-and-concurrency"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reserve a court slot without double booking (Priority: P1)

A player or organizer tries to reserve a specific court for a specific time slot. Several people attempt to reserve the same court and the same slot at the same moment. The platform accepts exactly one attempt and rejects the others with a clear, immediate conflict response, so a slot is never sold twice.

**Why this priority**: Preventing duplicate reservations is the central distributed-systems challenge of the project and its primary measurable objective. It is the foundation on which matches and reservations depend.

**Independent Test**: Submit many simultaneous reservation requests for the same (court, time slot). Verify that exactly one is confirmed, every other request receives an explicit conflict outcome, and the stored state holds exactly one reservation. Repeat across multiple trials.

**Acceptance Scenarios**:

1. **Given** a court slot with no active reservation, **When** two or more requests reserve it simultaneously, **Then** exactly one request is confirmed and every other request receives an explicit conflict outcome.
2. **Given** a court slot already reserved, **When** a new request tries to reserve it, **Then** the request is rejected without changing the existing reservation.
3. **Given** a confirmed reservation, **When** the same reservation request is retried, **Then** the retry is recognized as the same operation and no duplicate reservation is created.
4. **Given** a confirmed reservation, **When** it is cancelled, **Then** the slot becomes available and a subsequent reservation attempt can be confirmed.

---

### User Story 2 - Join a match without overbooking (Priority: P1)

A player joins an open match. When a match has only a few spots left and several players try to take the last spot simultaneously, the platform accepts only as many players as there are remaining spots and rejects the rest with a clear conflict response.

**Why this priority**: Overbooking participants is explicitly listed as a primary risk of the domain and is the second core concurrency scenario of the project.

**Independent Test**: With one remaining spot, submit many simultaneous join requests. Verify at most one acceptance, a participant count that never exceeds the maximum, and explicit conflict outcomes for the others. Repeat across multiple trials.

**Acceptance Scenarios**:

1. **Given** a match with one remaining spot, **When** multiple players try to join simultaneously, **Then** at most one is accepted and the participant count never exceeds the maximum.
2. **Given** a match already at maximum capacity, **When** a player tries to join, **Then** the request is rejected with an explicit conflict outcome.
3. **Given** a player already participating, **When** the same join request is retried, **Then** the retry does not add a duplicate participation.
4. **Given** a player leaves a full match, **When** another player tries to join, **Then** the freed spot can be occupied.

---

### User Story 3 - Trustworthy event identity and ordering (Priority: P2)

A service or operator reconstructs what happened in the platform by reading domain events. Every event is uniquely identified, carries a canonical set of attributes and a logical ordering value, so duplicate deliveries are ignored and the order of events produced by different instances can be rebuilt without relying on synchronized wall clocks.

**Why this priority**: Unique, orderable events are the prerequisite for idempotent delivery, auditing, consistency analysis and debugging. They support the P1 stories but do not by themselves block the MVP.

**Independent Test**: Produce events from multiple instances of the same service and from different services, deliver some of them twice and out of order, then verify every event is uniquely identifiable, carries all envelope attributes, and can be ordered and deduplicated deterministically.

**Acceptance Scenarios**:

1. **Given** an event is published, **When** it is inspected, **Then** it carries a unique event identifier, a type, a UTC timestamp, the originating service identity, and a logical clock value.
2. **Given** several events produced by the same service instance, **When** they are ordered by logical clock, **Then** the order reflects the sequence in which the operations occurred.
3. **Given** the same event delivered more than once, **When** a consumer processes it repeatedly, **Then** its effect is applied at most once.
4. **Given** events produced by different instances, **When** they are merged, **Then** they can be totally ordered by timestamp, logical clock and event identifier.

---

### User Story 4 - Integrate through stable, versioned contracts (Priority: P3)

A service team integrates with another service without accessing its data store. Synchronous capabilities are published as versioned interfaces and asynchronous capabilities are published as event types, so each service can evolve independently as long as its published contract is preserved.

**Why this priority**: Contract discipline keeps services decoupled and is required for the platform to scale services independently, but it is an enabling concern rather than a directly demonstrable concurrency outcome.

**Independent Test**: Consume another service only through its published interface and events while the providing service is redeployed independently, and verify integration keeps working without any shared storage access.

**Acceptance Scenarios**:

1. **Given** a published interface, **When** a compatible change is made, **Then** existing consumers continue to work; incompatible changes require a new version.
2. **Given** a service needs data owned by another service, **When** it integrates, **Then** it obtains that data through the published interface or events, never through direct storage access.
3. **Given** a service is replaced or scaled, **When** consumers call it, **Then** it is resolved through a stable logical name rather than a fixed network address.

---

### Edge Cases

- Two conflicting requests where one is cancelled while the other is in flight: only one net reservation may result and the final state must be consistent.
- A retried request after a timeout where the original actually succeeded: no duplicate resource may be created.
- An instance fails after validating a condition but before confirming: no partial state may remain and the operation must be safely retriable.
- A request arrives for a slot or match that no longer exists or is in the past: rejected without side effects.
- Events delivered out of order or duplicated: consumers stay idempotent and ordering is reconstructed by logical clock.
- Clock skew between instances: ordering must not depend on synchronized wall clocks.
- Simultaneous cancellation and occupation of the same last spot: the final state reflects at most one success per resource.
- A consumer processes an event for a resource it has not yet seen (out-of-order arrival): handled without corrupting state.

## Requirements *(mandatory)*

### Functional Requirements

**Identity and events**

- **FR-001**: The system MUST assign a globally unique identifier to every resource (user, establishment, court, time slot, match, participation, reservation) at creation.
- **FR-002**: Every domain event MUST conform to a canonical envelope containing `event_id`, `type`, `timestamp` (UTC, ISO-8601), `service_id`, and `logical_clock`.
- **FR-003**: The system MUST NOT publish any event that lacks any envelope attribute or a unique `event_id`.
- **FR-004**: The system MUST provide a deterministic total ordering of events using `timestamp`, `logical_clock` and `event_id`, independent of synchronized wall clocks.
- **FR-005**: Each service instance MUST maintain a monotonic logical clock so that the events it produces are causally ordered.
- **FR-006**: The system MUST emit at least the following domain events: `MATCH_CREATED`, `MATCH_UPDATED`, `MATCH_FULL`, `MATCH_CANCELLED`, `RESERVATION_CREATED`, `RESERVATION_CANCELLED`.

**Contracts and integration**

- **FR-007**: Synchronous capabilities MUST be exposed as versioned, documented interfaces; incompatible changes MUST require a new version and MUST NOT break existing consumers.
- **FR-008**: A service MUST NOT access the persistent storage of another service; all cross-service data MUST be obtained through published interfaces or events.
- **FR-009**: Services MUST be referenced by stable logical names rather than fixed network addresses.
- **FR-010**: Event consumers MUST be idempotent and deduplicate by `event_id`, so that repeated delivery applies an effect at most once.
- **FR-011**: Asynchronous delivery MUST be preferred whenever the requester does not need the result to complete the operation.

**Concurrency and coordination**

- **FR-012**: A reservation MUST be unique per (court, time slot); concurrent attempts on the same pair MUST result in at most one confirmation.
- **FR-013**: A match MUST NOT exceed its configured maximum number of participants; concurrent join attempts MUST NOT confirm more participants than there are available spots.
- **FR-014**: Confirmation MUST be conditional on the expected state of the resource still being valid at confirmation time.
- **FR-015**: A request that loses a conflict MUST fail explicitly with a machine-readable conflict outcome and MUST NOT partially modify state.
- **FR-016**: Conflicting operations MUST be serializable: the observed outcome MUST be equivalent to some sequential execution of the requests.
- **FR-017**: State-changing requests MUST be idempotent; retrying a request that already succeeded MUST NOT create a duplicate resource or side effect.
- **FR-018**: Cancelling a reservation or leaving a match MUST release the resource atomically so that it can be acquired again.

**Observability**

- **FR-019**: The system MUST record and expose every concurrency conflict occurrence through monitoring.
- **FR-020**: The system MUST emit structured logs and metrics for requests, errors, conflicts and reservation operations, and MUST NOT expose secrets in them.

**Concurrency-critical interfaces**

Only the operations whose correctness depends on concurrency control are specified here; the remaining per-service endpoint catalogs are deferred to the per-service specifications.

- **FR-021**: The platform MUST expose `POST /reservations` to create a reservation for a (court, time slot) with an optional match reference, and MUST confirm it only if the slot is still unreserved, otherwise returning an explicit conflict outcome.
- **FR-022**: The platform MUST expose `DELETE /reservations/{reservationId}` to cancel a reservation and atomically release its slot; the operation MUST be idempotent.
- **FR-023**: The platform MUST expose `POST /matches/{matchId}/participations` to occupy a match spot, confirming only if a spot remains and otherwise returning an explicit conflict outcome.
- **FR-024**: The platform MUST expose `DELETE /matches/{matchId}/participations/{playerId}` to release a match spot atomically; the operation MUST be idempotent.
- **FR-025**: The concurrency-critical operations MUST be versioned and MUST return a machine-readable conflict outcome on conflict, consistent with FR-015.

### Key Entities *(include if feature involves data)*

- **Resource Identifier**: A globally unique, immutable identifier assigned to each domain resource; it lets every service refer to the same resource unambiguously.
- **Event Envelope**: The canonical set of attributes attached to every domain event (`event_id`, `type`, `timestamp`, `service_id`, `logical_clock`) that enables deduplication, ordering and audit.
- **Domain Event**: A record of something relevant that happened (match created/updated/full/cancelled, reservation created/cancelled), distributed to one or more consumers.
- **Court Time Slot**: A reservable period of a specific court; the unit of uniqueness for reservations.
- **Reservation**: The association of the requesting party with a court time slot. A reservation may exist independently of a match and may optionally reference one; it is unique per (court, time slot).
- **Match**: An organized sporting event with a configured maximum number of participants.
- **Participation (Spot)**: The association of a player with a match; the number of participations must never exceed the match maximum.
- **Interface Contract**: A versioned, documented published capability (synchronous interface or event type) that other services depend on.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Across repeated trials, 100 or more simultaneous reservation attempts on the same court slot result in exactly one confirmed reservation and zero double bookings.
- **SC-002**: Across repeated trials, simultaneous join attempts on a match with a single remaining spot result in at most one acceptance and never exceed the match capacity.
- **SC-003**: 100% of published events carry all required envelope attributes and a unique event identifier.
- **SC-004**: Re-delivering the same event 10 or more times produces no additional state change or side effect.
- **SC-005**: 100% of conflicting requests receive an explicit, machine-readable conflict outcome within the response; no conflict is silent.
- **SC-006**: Event streams produced across multiple instances can be totally ordered and reconciled in 100% of test scenarios without relying on synchronized wall clocks.
- **SC-007**: Every observed concurrency conflict is recorded and visible through monitoring.
- **SC-008**: Retrying an already-confirmed state-changing request yields the original result without creating duplicates in 100% of trials.

## Assumptions

- This specification is the foundational slice of the platform; user management, establishment and court catalog, notification delivery, payments and user interfaces are covered by later specifications.
- Authentication and authorization already exist, and callers of the operations described here are authenticated and authorized according to the platform roles (PLAYER, ORGANIZER, OWNER, ADMIN).
- A time slot belongs to exactly one court, so (court, time slot) is the reservable unit.
- Match participation and court slot reservation are distinct resources that share the same concurrency guarantees.
- Domain events may be delivered more than once and out of order (at-least-once delivery); consumers must tolerate this.
- State-changing requests carry a client-supplied operation identifier that enables idempotent retries.
- The platform technologies mandated by the project constitution (cloud services, managed database, message queues/topics and write operations conditioned on expected state) constrain planning and implementation but do not change the behaviors required above.
- This specification includes only the concurrency-critical reservation and vacancy interfaces (`POST /reservations`, `DELETE /reservations/{reservationId}`, `POST /matches/{matchId}/participations`, `DELETE /matches/{matchId}/participations/{playerId}`); the full per-service endpoint catalogs are deferred to the per-service specifications.

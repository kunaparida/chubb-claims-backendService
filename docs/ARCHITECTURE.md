# Architecture and Design Decisions

Backend for a claims platform: claimants report and track incidents, officers work claims to a decision and settlement,
managers see workload, liability and performance. The frontend is out of scope, so the HTTP API and the event contract
are the product.

## 1. Shape of the system

**One deployable service (modular monolith), layered.**

```
                  HTTPS + Bearer JWT
 Client ──────────────────────────────────► JwtAuthenticationFilter ► SecurityConfig
                                                      │
                          ┌───────────────────────────▼───────────────────────────┐
                          │ controller  (role checks, validation, status codes)   │
                          ├───────────────────────────────────────────────────────┤
                          │ service     (transactions, business rules, state       │
                          │              machine, ClaimAuditRecorder)              │
                          ├───────────────────────────────────────────────────────┤
                          │ repository  (Spring Data JPA, aggregate queries)       │
                          └───────────────────────────┬───────────────────────────┘
                                                      ▼
                          H2 (default)  /  PostgreSQL (config provided)
                          tables: users, claims, claim_status_history, outbox_events
                                                      │
                  OutboxPublisher (scheduled) ────────┘──► EventPublisher
                                                            ├─ LoggingEventPublisher (default)
                                                            └─ KafkaEventPublisher (app.events.publisher=kafka)
```

Packages (`com.claims`): `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `security`, `config`,
`exception`, `validation`, `util`, `event`.

### Why one service, not several
| Driver | Assessment |
|---|---|
| Consistency | A claim, its status, its assignee, its history and its outgoing event must change atomically. One database transaction gives that for free; separate services would need sagas. |
| Size and team | One bounded context ("claim lifecycle"), one team, a 2-3 hour budget. |
| Scaling | No evidence yet that any part needs independent scaling. |
| Operations | One artefact to build, run and debug. |

**Where I would split first, and why:** *notifications* and *reporting/dashboards*. They only need to know that a claim
changed, they tolerate seconds of delay, and dashboards have a different read profile (large aggregates) from claim
writes. The seam already exists: the `ClaimStatusChanged` event.

## 2. Domain model

```
users ─┬─< claims >─┬─< claim_status_history
       │            └─ (assigned_officer_id → users)
       └─ (claimant_id → users)                    outbox_events (aggregate = Claim, id = claim id)
```

| Entity | Key fields |
|---|---|
| `User` | email (unique), password hash, fullName, role (CLAIMANT / OFFICER / MANAGER) |
| `Claim` | claimNumber (unique), policyNumber, claimant details, incidentDate, description, amountClaimed, **claimType** (MOTOR / PROPERTY), **market** (2-letter code), status, claimant, assignedOfficer, informationRequest, **informationResponse**, decisionNotes, timestamps |
| `ClaimStatusHistory` | claim, fromStatus (null for the first record), toStatus, changedBy, note, changedAt: append-only audit trail |
| `OutboxEvent` | aggregateType, aggregateId, eventType, JSON payload, createdAt, publishedAt (null = not yet published) |

`market` is a validated 2-letter code, not an enum, because the list of markets is business data I do not own.

### Claim lifecycle (state machine)

```
SUBMITTED ──► UNDER_REVIEW ──► APPROVED ──► SETTLED
                  │  ▲
                  ▼  │
          MORE_INFO_REQUIRED          UNDER_REVIEW ──► REJECTED
```
Allowed: `SUBMITTED→UNDER_REVIEW`, `UNDER_REVIEW→MORE_INFO_REQUIRED|APPROVED|REJECTED`, `MORE_INFO_REQUIRED→UNDER_REVIEW`,
`APPROVED→SETTLED`. `REJECTED` and `SETTLED` are terminal. Anything else is `409 Conflict`.
The rules live in one table in `ClaimService`. `MORE_INFO_REQUIRED → UNDER_REVIEW` is triggered by the **claimant**
(`provide-info`) or by an officer (`review`).

## 3. What is synchronous (REST) and what is event-driven

| Concern | Style | Reason |
|---|---|---|
| Submit, read, list, queue, pick up, assign | REST (sync) | Caller needs an immediate, consistent answer. |
| Every status change (review, request-info, provide-info, approve, reject, settle) | REST (sync) command | Must validate against current state and answer success or conflict. |
| "A claim changed status" | **Event** (`ClaimStatusChanged`) | Consumers (claimant notifications, reporting, audit exports) are asynchronous, can fail and retry independently, and must never slow or fail the claim update. |
| Dashboards | REST read | Aggregate queries today; first candidate for a read model if volume grows. |

### Outbox pattern (how events are produced)
1. In **one database transaction** the service updates the claim, appends a `claim_status_history` row and inserts an
   `outbox_events` row (`ClaimAuditRecorder`, `Propagation.MANDATORY`).
2. A scheduled `OutboxPublisher` reads unpublished rows oldest-first and calls `EventPublisher.publish(topic, claimId, json)`.
3. Only after a successful publish is `publishedAt` set. A failure stops the batch (keeps order) and the row is retried.

Guarantees and limits:
- **No lost events and no phantom events:** the event exists if and only if the status change committed.
- **At-least-once delivery:** a crash between send and commit re-sends the row. Consumers de-duplicate on `eventId`.
- **Ordering per claim:** Kafka record key is the claim id, so one claim's events stay in one partition, in order.
- **Default publisher** logs the event, so the app runs with no broker. `app.events.publisher=kafka` plus
  `spring.kafka.bootstrap-servers` switches to Kafka (`KafkaEventPublisher` waits for the broker acknowledgement).

Event contract (`claims.claim-status-changed`, JSON):
```json
{"eventId":"uuid","claimId":1,"claimNumber":"CLM-...","claimType":"PROPERTY","market":"HK",
 "amountClaimed":1250.00,"fromStatus":"UNDER_REVIEW","toStatus":"APPROVED",
 "changedBy":"officer@claims.local","occurredAt":"2026-10-14T06:45:17Z"}
```
`fromStatus` is null for the initial submission.

## 4. Read/write profile and what officers need
- **Writes** are low volume, transactional, per claim. **Reads** are dominated by lists and dashboards.
- Officers need: *what is waiting* (`GET /api/claims/queue`: unassigned `SUBMITTED`, oldest first), *what is mine*
  (`GET /api/claims`, optional `?status=`), and *what happened* (`GET /api/claims/{id}/history`).
- Indexes: explicit indexes exist on `claim_status_history.claim_id` and `outbox_events.publishedAt`. H2 indexes foreign
  keys automatically; **on PostgreSQL I would add explicit indexes** on `claims(claimant_id)`, `claims(assigned_officer_id)`
  and `claims(status)`, because the list, queue and dashboard queries filter on them.
- Managers: workload (counts per status, unassigned), liability (pending exposure and approved-not-settled, **broken down
  by claim type and market**) and officer performance, all computed by the database.

## 5. Security model
JWT (HS256) in `Authorization: Bearer`, stateless; BCrypt passwords; method security with `@PreAuthorize`; RFC 7807
`application/problem+json` for every error including 401 and 403.

| Endpoint | CLAIMANT | OFFICER | MANAGER |
|---|---|---|---|
| `POST /api/claims` | yes (own account email) | no | no |
| `GET /api/claims`, `GET /api/claims/{id}` | own claims | assigned claims; may open unassigned (queue) claims | all |
| `GET /api/claims/queue` | no (403) | yes | yes |
| `PUT .../pickup` | no | yes | no |
| `PUT .../assign` | no | self only | any officer |
| `PUT .../review`, `request-info`, `approve`, `reject`, `settle` | no | assigned officer | yes |
| `PUT .../provide-info` | owner, only when info requested | no | no |
| `GET .../history` | own claims | same as view | all |
| `GET /api/dashboard/*` | no | no | yes |

Other claimants' or officers' claims return **404**, not 403, so existence is not leaked. Public registration can only
create claimants.

## 6. Error model
RFC 7807: `type`, `title`, `status`, `detail`, `instance`, and an `errors` map for field validation.
`400` validation or bad input, `401` no/invalid token, `403` role or ownership, `404` not found or not visible,
`405`/`415` wrong method or content type, `409` invalid state transition or conflict, `500` generic (details only in logs).

## 7. Testing strategy
- Unit tests with Mockito for services, the audit recorder, the outbox relay and the publishers.
- Validation tests for request rules (policy, amount, future date, description length, claim type, market).
- Two Spring Boot integration tests on H2: the original role/workflow flow and the lifecycle extensions (queue, pick-up,
  claimant answer, history, filter, outbox, liability breakdown).
- Wiring tests prove the publisher is chosen by configuration.
- Postman collection (`postman/`) replays the same flow, with assertions, in collection order.

## 8. Known limitations (deliberate, for the sprint)
| Limitation | Next step |
|---|---|
| No optimistic locking: two officers acting at the same instant could race | Add `@Version` to `Claim`, map the conflict to 409 |
| Kafka path wired and unit-tested but **not run against a real broker** | Docker Compose with Kafka + an integration test (Testcontainers) |
| Outbox poller runs in every instance, so duplicates are possible with several instances | Row locking (`FOR UPDATE SKIP LOCKED`) or a dedicated relay |
| `ddl-auto=update`, in-memory H2, bootstrap users | Flyway migrations, PostgreSQL, real user provisioning |
| `assignedOfficer` can be re-assigned without a history entry | Record assignment events too |
| Performance dashboard counts only; no time-to-decision | Use `claim_status_history` timestamps |
| No claimant notifications | Consumer of `ClaimStatusChanged` (the seam exists) |
| JWT: no refresh or revocation; default dev secret | Short-lived access tokens + refresh, secret from a vault |
| No rate limiting on login | Gateway or filter-level throttling |

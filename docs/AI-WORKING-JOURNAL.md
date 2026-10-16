# AI Working Journal

| | |
|---|---|
| **Project** | Claims Management System (`chubb-claims-backendService`) |
| **Author** | Kuna Parida |
| **Document purpose** | Transparent record of how AI assistance was used: what was requested, what was produced, and what was accepted, challenged or overridden, with the reasoning. |
| **Last updated** | 2026-10-16 |

The author reviews and takes responsibility for every line in this repository. AI output was treated as a proposal, not
as a result, until it was verified by a build, a test run or a manual check.

## 1. Conventions

| Decision | Meaning |
|---|---|
| **ACCEPTED** | AI output used as generated. |
| **CHALLENGED** | AI output questioned or verified, then kept or changed. |
| **OVERRODE** | The author decided differently from the AI. |

Prompts appear in the order they were sent. Each entry follows the same structure: *Prompt, Result, Decision, Verification*.

## 2. Summary of the work

| Phase | Prompts | Location | Purpose |
|---|---|---|---|
| 1. Reference build | 1-11 | `C:\project\claims-management` | First complete Spring Boot claims system, built from an architect-style prompt, used to learn the domain and obtain a working, tested baseline. |
| 2. Presentation material | 12-13 | `claims-management/docs` | Walkthrough guide, first journal draft and prompt list. |
| 3. Submission repository | 14-23 | `C:\project\chubb-claims-backendService` | The repository under review. Verified against the assessment brief and extended to close gaps. |

## 3. Key outcomes

- A layered, modular-monolith Spring Boot 3.4 / Java 21 application with JWT security, role-based access and RFC 7807 errors.
- Workflow rules enforced in the service layer; invalid transitions raise `BusinessException`.
- Audit history and a transactional outbox for status-change events (log publisher by default, Kafka optional).
- `mvn clean verify` passes on JDK 27 with 82 tests and 0 failures (42 before Phase 3 work).
- End-to-end replay of the Postman flow against a running instance returned the expected status codes (200/201/400/403/409).

---

# Part A: Prompt Log

## Phase 1: Reference build (`claims-management`)

### Prompt 1: Build the application

**Prompt**
```text
Act as a Senior Java Architect.
Build a Claims Management System as a Monolithic Spring Boot Application.

Tech stack: Java 21, Spring Boot 3.x, Maven, Spring Web, Spring Data JPA, Spring Validation,
Spring Security, JWT Authentication, PostgreSQL (or H2 for development), Lombok, MapStruct,
OpenAPI/Swagger, JUnit 5, Mockito.

Architecture (package com.claims): controller, service, repository, entity, dto, mapper,
security, exception, validation, config, util.

Entities:
1. Claim: id, claimNumber, policyNumber, claimantName, claimantEmail, incidentDate, description,
   amountClaimed, status, assignedOfficer, createdAt, updatedAt
2. User: id, username, password, role

ClaimStatus enum: SUBMITTED, UNDER_REVIEW, MORE_INFO_REQUIRED, APPROVED, REJECTED, SETTLED

Valid status flow:
SUBMITTED -> UNDER_REVIEW
UNDER_REVIEW -> MORE_INFO_REQUIRED
MORE_INFO_REQUIRED -> UNDER_REVIEW
UNDER_REVIEW -> APPROVED
UNDER_REVIEW -> REJECTED
APPROVED -> SETTLED
Invalid transitions must throw BusinessException.

Roles: ROLE_CLAIMANT, ROLE_OFFICER, ROLE_MANAGER

APIs:
- Claimant: POST /api/claims, GET /api/claims/{id}, GET /api/claims
- Officer: PUT /api/claims/{id}/assign, /review, /request-info, /approve, /reject, /settle
- Manager: GET /api/dashboard/workload, /liability, /performance

Validation: policyNumber mandatory, claimantName mandatory, amountClaimed > 0,
incidentDate cannot be a future date, description minimum 20 characters.

Exception handling: GlobalExceptionHandler, BusinessException, ResourceNotFoundException,
ValidationException, using RFC 7807 Problem Details.

Security: JWT authentication, Spring Security, role-based access.
Swagger: enable OpenAPI and provide API documentation.
Database: PostgreSQL, schema created automatically through JPA, with an application.yml.

Deliverables: pom.xml, entities, repositories, DTOs, services, controllers, security,
JWT implementation, exception handling, Swagger config, README, Postman collection.

Follow SOLID, clean code, layered architecture, constructor injection, proper logging,
and Java 21 best practices.
```

**Result:** Full project: entities, workflow rules, APIs, JWT security, error handling, Swagger, README and the first
Postman collection.

**Decision: ACCEPTED** as a baseline only. The AI had no Maven in its environment, so nothing was compiled until Prompt 5.

**Verification:** deferred to Prompts 2 and 5.

### Prompt 2: Unit tests

**Prompt**
```text
Generate complete JUnit 5 and Mockito test cases for the Claims Management Application.
Coverage target: 90%+.
Generate tests for ClaimService: createClaim(), assignClaim(), requestMoreInfo(),
approveClaim(), rejectClaim(), settleClaim().
```

**Result:** `ClaimServiceTest`, plus tests for the dashboard, authentication, JWT, validators, exception handler,
controllers and an end-to-end workflow test.

**Decision: ACCEPTED** after the AI reviewed its own tests and fixed a Mockito mistake before the first run (mocks
created inside a `thenReturn(...)` call cause `UnfinishedStubbingException`).

**Verification:** verified build in the reference project: 153 tests passed, 99.4% line coverage (JaCoCo gate at 90%).

### Prompt 3: Move the project and switch to H2

**Prompt**
```text
I moved the repo to C:\project; use this location from now on.
I don't have a database installed, so make H2 the default database.
Keep the PostgreSQL configuration, but commented out.
```

**Result:** `application.yml` uses in-memory H2 by default with the PostgreSQL block commented out. The H2 profile file
was removed and the README updated.

**Decision: OVERRODE** the AI's default (PostgreSQL) because of the author's environment constraint (no local database).

**Verification:** application starts with no external database.

### Prompt 4: Compile error

**Prompt**
```text
java: java.lang.ExceptionInInitializerError
com.sun.tools.javac.code.TypeTag :: UNKNOWN
while running
```

**Result:** First fix was a Lombok upgrade in `pom.xml`.

**Decision: CHALLENGED.** The Lombok upgrade alone did not resolve the error on the author's machine.

**Verification:** see *Open items* (final resolution is not recorded here).

### Prompt 5: Maven build error in the submission repository

**Prompt**
```text
(pasted) [ERROR] Failed to execute goal ...maven-surefire-plugin:3.5.3:test ... on project claims-management
See C:\project\chubb-claims-backendService\target\surefire-reports for the individual test results.
Getting this error while trying maven clean and install.
```

**Result:** The AI read `target/surefire-reports`. Seven tests failed with "Mockito cannot mock this class": the Byte
Buddy version managed by Spring Boot 3.4.5 does not support JDK 27. It added `byte-buddy.version=1.18.14` to `pom.xml`.

**Decision: ACCEPTED**, because the cause was reproduced and the fix confirmed on the same JDK 27 that failed.

**Verification:** `mvn clean install` passed (42 tests).

---

# Part B: Design Decisions (Phase 3)

## B1. Build failure after `mvn clean install`

- **Context:** see Prompt 5.
- **Fix:** pinned `byte-buddy.version`.
- **Verified by:** re-running on the failing JDK 27, result `BUILD SUCCESS`.

## B6. Audit history and events (transactional outbox)

- **Question (from the brief):** what is synchronous and what is event-driven?
- **AI proposal:** `claim_status_history` and `outbox_events` tables written in the same transaction as the status
  change; a scheduled relay that publishes unpublished rows; a log publisher by default and an optional Kafka publisher
  (`app.events.publisher=kafka`).
- **Challenges and findings:**

  | Challenge | Outcome |
  |---|---|
  | Why not send to Kafka directly from the service? | Dual-write problem: commit and send can disagree. The outbox avoids lost or phantom events. |
  | What delivery guarantee results? | At-least-once. Every event carries an `eventId` for consumer de-duplication. |
  | Can auditing run outside a transaction? | No. `ClaimAuditRecorder.record` uses `Propagation.MANDATORY`. |
  | How is ordering kept? | The relay stops at the first failure to preserve per-claim order; the Kafka record key is the claim id. |

- **Decision: CHALLENGED, then ACCEPTED.**
- **Known limit:** Kafka is wired and unit-tested with a mocked template, but has **not** been run against a real broker.

## B7. Claim type and market

- **AI proposal:** `claimType` (MOTOR, PROPERTY) and `market` as a 2-letter code; liability broken down by both.
- **Decision: OVERRODE** a `Market` enum. The set of markets is not known, so an enum would invent business data.
- **Breaking change:** both fields are required on submission. The Postman collection and all existing tests were
  updated in the same change.

## B8. Architecture: modular monolith with REST plus outbox

- One bounded context, one transaction per claim change, within a 2-3 hour budget.
- Split candidates for the future: notifications and reporting, connected by the `ClaimStatusChanged` event.
- Full reasoning is recorded in `docs/ARCHITECTURE.md`.

## B9. Verification before trust

- **AI mistake caught:** while writing the new service tests, the AI first saved an unfinished placeholder test file and
  then rewrote it. Only a green build is counted as evidence.
- **Checks run:**
  - `mvn clean verify` on JDK 27: 82 tests, 0 failures (previously 42).
  - End-to-end replay of the Postman flow against a freshly started app: every step returned the expected status
    (200/201/400/403/409); history showed all six transitions; outbox events appeared in the log.
  - Context tests prove the publisher is `LoggingEventPublisher` by default and `KafkaEventPublisher` when switched.

---

# Part C: Known Limitations

| Area | Current state |
|---|---|
| Concurrency | No optimistic locking (`@Version`). |
| Messaging | No test against a real Kafka broker. |
| Database | `ddl-auto=update` and in-memory H2 by default; no migration tooling. |
| Secrets | Default JWT secret is for local use only. |
| Quality metrics | Code coverage is not measured in this repository (no JaCoCo plugin). |

# Part D: Open Items

These items are not yet documented and should be completed before final review:

1. **Pre-existing commits.** The repository already had commits before Phase 3 (`Initial commit` to `pagination added`).
   State which parts were AI-generated, which were written by hand, and how they relate to the Phase 1 build.
2. **Prompt 4 resolution.** Record the final fix for the `TypeTag :: UNKNOWN` compile error.
3. **Missing entries.** Prompts 6-23 and design sections B2-B5 are not yet written up in this journal.

---

# Appendix: Template for New Entries

```text
### Prompt N: <title>
**Prompt:** ...
**Result:** what the AI produced.
**Decision (ACCEPTED / CHALLENGED / OVERRODE):** ... because ...
**Verification:** build, test or manual check performed.
```

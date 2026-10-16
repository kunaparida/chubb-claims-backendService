# AI Working Journal

A running log of what I asked the AI, what it produced, and what I **accepted**, **challenged** or **overrode**, with
brief reasoning. Not polished on purpose. Prompts are listed in the order I sent them. I sign off every line in this
repository.

> **Owner note:** this journal was drafted from the full AI session transcript. The prompts and results are factual.
> The "My decision" lines record what actually happened in the conversation; add your own reasoning where it differs and
> fill the two TODO items. The panel will probe every entry.

**Legend:** ACCEPTED = used as generated · CHALLENGED = questioned or verified, then kept or changed ·
OVERRODE = I decided differently from the AI.

## How the work was organised

| Phase | Prompts | Where | Purpose |
|---|---|---|---|
| 1. Reference build | 1-11 | `C:\project\claims-management` | A first, complete Spring Boot claims system built from my architect prompt, used to learn the domain and get a working, tested baseline. |
| 2. Presentation material | 12-13 | `claims-management/docs` | Walkthrough guide, first journal draft, prompt list. |
| 3. Submission repository | 14-23 | `C:\project\chubb-claims-backendService` | The repository I am submitting. Verified against the assessment brief and extended to close its gaps. |

*TODO (owner):* the submission repository already had commits before Phase 3 (`Initial commit` ... `pagination added`).
State here which parts were AI-generated, what you changed by hand, and how that code relates to the Phase 1 build.

---

# Part A: Prompt log (everything, in order)

## Phase 1: Reference build (`claims-management`)

### Prompt 1: Build the application
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
- **Result:** the full project: entities, workflow rules, APIs, JWT security, error handling, Swagger, README and the
  first Postman collection.
- **My decision:** ACCEPTED as a baseline. Not trusted yet: the AI had no Maven in its environment, so nothing was
  compiled until Prompt 5.

### Prompt 2: Unit tests
```text
Generate complete JUnit 5 and Mockito test cases for the Claims Management Application.
Coverage target: 90%+.
Generate tests for ClaimService: createClaim(), assignClaim(), requestMoreInfo(),
approveClaim(), rejectClaim(), settleClaim().
```
- **Result:** `ClaimServiceTest` plus tests for the dashboard, auth, JWT, validators, exception handler, controllers, and
  an end-to-end workflow test. In the verified build, 153 tests passed with 99.4% line coverage (JaCoCo gate at 90%).
- **My decision:** ACCEPTED after the AI reviewed its own tests and fixed a Mockito mistake (mocks created inside a
  `thenReturn(...)` call cause `UnfinishedStubbingException`) before the first run.

### Prompt 3: Move the project and switch to H2
```text
I moved the repo to C:\project; use this location from now on.
I don't have a database installed, so make H2 the default database.
Keep the PostgreSQL configuration, but commented out.
```
- **Result:** `application.yml` uses in-memory H2 by default, PostgreSQL block commented out. The H2 profile file was removed
  and the README updated.
- **My decision:** OVERRODE the AI's default (PostgreSQL) because of my environment constraint.

### Prompt 4: Compile error
```text
java: java.lang.ExceptionInInitializerError
com.sun.tools.javac.code.TypeTag :: UNKNOWN
while running
```
- **Result:** first fix was a Lombok upgrade in `pom.xml`.
- **My decision:** CHALLENGED (see Prompt 6): the first fix alone did not solve it on my machine.


### Prompt 5: Maven build error in the submission repository
```text
(pasted) [ERROR] Failed to execute goal ...maven-surefire-plugin:3.5.3:test ... on project claims-management
See C:\project\chubb-claims-backendService\target\surefire-reports for the individual test results.
Getting this error while trying maven clean and install.
```
- **Result:** the AI read `target/surefire-reports`. Seven tests errored with "Mockito cannot mock this class": the
  Byte Buddy version managed by Spring Boot 3.4.5 does not support JDK 27. It added `byte-buddy.version=1.18.14` to
  `pom.xml`; `mvn clean install` then passed (42 tests).
- **My decision:** ACCEPTED, because it was reproduced and re-verified on the same JDK 27 that failed.


# Part B: Design decisions made in Phase 3 (Prompts 14-18)

## B1. Build failure after `mvn clean install`
See Prompt 14. Fix pinned `byte-buddy.version`. **Verified by:** re-running on the failing JDK 27 (`BUILD SUCCESS`).


## B6. Audit history and events (transactional outbox)
- **Asked (via the brief):** what is synchronous and what is event-driven.
- **AI proposed:** `claim_status_history` plus an `outbox_events` table written in the same transaction as the status
  change; a scheduled relay that publishes unpublished rows; a log publisher by default and an optional Kafka publisher
  (`app.events.publisher=kafka`).
- **CHALLENGED / checked:**
  - Why not send to Kafka directly from the service? Dual-write problem: commit and send can disagree. The outbox
    avoids lost or phantom events.
  - Delivery is **at-least-once**, so every event has an `eventId` for consumer de-duplication.
  - `ClaimAuditRecorder.record` uses `Propagation.MANDATORY`, so it cannot run outside a transaction by accident.
  - The relay stops at the first failure to preserve per-claim order; the Kafka record key is the claim id.
- **Honest limit:** Kafka is wired and unit-tested with a mocked template, but **not run against a real broker**.

## B7. Claim type and market
- **AI proposed:** `claimType` (MOTOR, PROPERTY) and `market` as a 2-letter code; liability broken down by both.
- **OVERRODE a `Market` enum:** I do not know the six markets, so an enum would invent business data.
- **Breaking change:** both fields are required on submission, so the Postman collection and all existing tests were
  updated in the same change.

## B8. Architecture: modular monolith with REST plus outbox
One bounded context, one transaction per claim change, and a 2-3 hour budget. Split candidates are notifications and
reporting, connected by the `ClaimStatusChanged` event. Recorded in `docs/ARCHITECTURE.md`.

## B9. Verification before I trust it
- **AI mistake caught:** while writing the new service tests the AI first saved an unfinished placeholder test file by
  mistake and then rewrote it. A green build is the only thing I count.
- **Checks run:**
  - `mvn clean verify` on JDK 27: 82 tests, 0 failures (was 42).
  - End-to-end replay of the Postman flow against a freshly started app: every step returned the expected status
    (200/201/400/403/409), history showed all six transitions, outbox events appeared in the log.
  - Context tests prove the publisher is `LoggingEventPublisher` by default and `KafkaEventPublisher` when switched.
- **Not done:** no optimistic locking (`@Version`), no real-broker Kafka test, `ddl-auto=update`, in-memory H2 by default,
  default JWT secret for local use only, coverage not measured in this repository.

---

## Template for new entries
```
## N. <title>
- Asked: ...
- AI did: ...
- My decision (ACCEPTED / CHALLENGED / OVERRODE): ... because ...
- Verified by: ...
```

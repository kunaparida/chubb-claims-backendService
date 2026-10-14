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

### Prompt 5: Run it yourself
```text
Can you run it yourself and make it ready?
```
- **Result:** the AI found IntelliJ's bundled Maven, built with JDK 21: 153 tests passed and the app started.
  It also found that IntelliJ's project SDK was OpenJDK 27, not 21.
- **My decision:** ACCEPTED the evidence, but the result only proved JDK 21, which is not what my IDE uses.

### Prompt 6: Still not working
```text
No, it's still not running. Make it work.
```
- **Result:** the AI reproduced the failure on JDK 27. Lombok, Byte Buddy (Mockito) and JaCoCo versions all predated that
  JDK. Upgraded Lombok to 1.18.48, Byte Buddy to 1.18.14 and JaCoCo to 0.8.15; `mvn clean verify` passed on JDK 27 and the
  packaged app started.
- **My decision:** CHALLENGED the "it works on JDK 21" answer and required a fix on the JDK my IDE really uses.

### Prompt 7: API steps and how to check the database
```text
Now it's running. Give me the steps for all the APIs and how to check in the DB.
```
- **Result:** the AI edited config to enable the H2 console and started a scripted end-to-end run.
- **My decision:** OVERRODE: I interrupted the run (see Prompt 8).

### Prompt 8: Don't modify, only tell me how
```text
No no, don't modify in H2, only how can I check?
```
- **Result:** the AI reverted its edits to `SecurityConfig` and `application.yml` and explained that an in-memory H2
  database cannot be opened from outside the app unless the console is enabled.
- **My decision:** ACCEPTED the reversion; I wanted instructions, not unrequested configuration changes.

### Prompt 9: The 401 on the H2 console
```text
(pasted the 401 "Authentication Required" response)
No, this is for http://localhost:8080/h2-console
```
- **Result:** the 401 was expected, because the console was blocked by Spring Security after the revert. The AI re-enabled
  it (`application.yml` + `SecurityConfig`) and gave the login: JDBC URL `jdbc:h2:mem:claimsdb`, user `sa`, empty password.
- **My decision:** ACCEPTED. Local development only; the console must be off outside dev.

### Prompt 10: Swagger payloads
```text
Give me all APIs' payloads and URLs so I can test directly from Swagger.
```
- **Result:** every URL with ready-to-paste JSON, grouped by claimant, officer and manager.
- **My decision:** ACCEPTED.

### Prompt 11: Sequential Postman collection
```text
Can you do one thing: give me the whole Postman collection sequentially.
```
- **Result:** 31 numbered requests with assertions in `postman/Claims-Management.postman_collection.json`.
- **My decision:** ACCEPTED; JSON validated, replay against the running app still recommended.

## Phase 2: Presentation material

### Prompt 12: Presentation, deliverables and likely questions
```text
How to give a presentation of this project and the below things; please provide a PDF or text file.
(pasted the brief's Deliverables, Walkthrough Format and Notes)
Also provide what follow-up questions they will ask.
```
- **Result:** `PRESENTATION-GUIDE.md` (plan, demo script, 30 prepared panel questions, "more time" plan, questions to ask)
  and a first draft of `AI-WORKING-JOURNAL.md`.
- **My decision:** ACCEPTED the structure. A PDF was not produced (no tooling); text files were provided instead.

### Prompt 13: All prompts, serial-wise
```text
Give me all prompts serial-wise, in a proper step-by-step way, that were used to build this application.
```
- **Result:** the numbered prompt list that this journal now expands.
- **My decision:** ACCEPTED, then asked (Prompt 22 and 23) for the journal itself to carry the full list.

## Phase 3: Submission repository (`chubb-claims-backendService`)

### Prompt 14: Maven build error in the submission repository
```text
(pasted) [ERROR] Failed to execute goal ...maven-surefire-plugin:3.5.3:test ... on project claims-management
See C:\project\chubb-claims-backendService\target\surefire-reports for the individual test results.
Getting this error while trying maven clean and install.
```
- **Result:** the AI read `target/surefire-reports`. Seven tests errored with "Mockito cannot mock this class": the
  Byte Buddy version managed by Spring Boot 3.4.5 does not support JDK 27. It added `byte-buddy.version=1.18.14` to
  `pom.xml`; `mvn clean install` then passed (42 tests).
- **My decision:** ACCEPTED, because it was reproduced and re-verified on the same JDK 27 that failed.

### Prompt 15: Verify against the assessment brief and prepare the walkthrough
```text
(attached the assessment brief PDF)
This is the assessment document. According to this we build the Claims Management Spring Boot project.
Kindly verify this and suggest whether we built the application according to the assessment document.
Also provide the Walkthrough Format text file to give the demo and walkthrough to the client.
```
- **Result:** the AI extracted the PDF text (no PDF tool was installed) and produced a gap analysis: no claimant
  "provide more information" step, no officer queue, no audit history, no Kafka/event story, no claim type or market, no
  git history discipline. It also wrote `WALKTHROUGH-FORMAT.txt`.
- **My decision:** ACCEPTED the gap list; I prioritised the gaps the brief names explicitly.
- **Correction recorded:** my prompt said "client", but the brief shows the audience is the hiring panel, so the
  walkthrough file is written for the panel.

### Prompt 16: Should we use microservices?
```text
So now clear me: should they tell us to go with microservice architecture?
```
- **Result:** No. The brief does not require microservices; it asks me to decide and justify. Modular monolith with the
  split point documented (see Part B, decision B8).
- **My decision:** ACCEPTED. I raised the question myself; the AI's answer was grounded in the brief's own wording
  (the decision and its justification are what is assessed), and the original build prompt already asked for a monolith.

### Prompt 17: What to take care of now
```text
According to the brief, what do we need to take care of now?
```
- **Result:** a prioritised list: choose one codebase, commit history, journal, then the missing features.
- **My decision:** ACCEPTED.

### Prompt 18: Implement the pending work in this repository
```text
Please keep this one chubb-claims-backendService (C:\project\chubb-claims-backendService) and
implement the pending items as per the brief. Also take care to create docs/AI-WORKING-JOURNAL.md,
and add all the files to the project as mentioned in the brief document.
```
- **Result:** built and verified (82 tests green on JDK 27, Postman flow replayed against the live app):
  claimant `provide-info`, officer queue and `pickup`, `?status=` filter, status history, `ClaimStatusChanged` event via a
  transactional outbox (log publisher by default, Kafka publisher switchable), `claimType` and `market`, liability by
  both, updated Postman collection, README, `docs/ARCHITECTURE.md`, `docs/WALKTHROUGH-FORMAT.txt` and this journal.
  Details and reasoning are in Part B.
- **My decision:** OVERRODE the earlier copy: this repository is the one I submit.

### Prompt 19: Swagger link
```text
Could you provide the Swagger link to test all the endpoints?
```
- **Result:** `http://localhost:8080/swagger-ui.html`, OpenAPI JSON at `/v3/api-docs`, and the order to test in.
- **My decision:** ACCEPTED.

### Prompt 20: H2 details and credentials
```text
What are the H2 DB details and the manager and officer login credentials?
```
- **Result:** console `http://localhost:8080/h2-console`, JDBC URL `jdbc:h2:mem:claimsdb`, user `sa`, empty password;
  `manager@claims.local` and `officer@claims.local` with `ChangeMe123!`; claimants register themselves.
- **My decision:** ACCEPTED. Local development credentials only.

### Prompt 21: How many tests
```text
How many test cases are written?
```
- **Result:** 82 tests in the submission repository (42 before the new work), listed per class. Coverage was **not**
  measured here; the 99.4% figure belongs to the Phase 1 project.
- **My decision:** ACCEPTED, and noted that coverage for this repository still needs measuring if I want to quote it.

### Prompt 22: Is the journal complete?
```text
Just clarify in the AI Working Journal file: did you mention all the details from starting to ending,
or only what you modified today?
```
- **Result:** honest answer: the first journal covered only Phase 3. It left out Phase 1 and 2.
- **My decision:** CHALLENGED the journal's completeness, which led to Prompt 23.

### Prompt 23: Regenerate the journal with every prompt
```text
Also can you add all these prompts that you used earlier, meaning re-generate the AI working journal so that
everything is properly there. (pasted the earlier prompt list)
```
- **Result:** this file.
- **My decision:** ACCEPTED.

---

# Part B: Design decisions made in Phase 3 (Prompts 14-18)

## B1. Build failure after `mvn clean install`
See Prompt 14. Fix pinned `byte-buddy.version`. **Verified by:** re-running on the failing JDK 27 (`BUILD SUCCESS`).

## B2. Comparing the app against the assessment brief
See Prompt 15. **Checked:** whether the brief forces microservices. It does not; it leaves the service boundary to me (B8).

## B3. Which codebase is the submission
`C:\project` held two copies of the project. **OVERRODE:** I chose `chubb-claims-backendService` as the single submission
because it already has the commit history and email-based accounts I want to defend.

## B4. Claimant provides additional information
- **AI proposed:** `PUT /api/claims/{id}/provide-info`, owner only, only when status is `MORE_INFO_REQUIRED`; stores the
  answer and moves the claim to `UNDER_REVIEW`.
- **Checked:** the transition table allows `SUBMITTED -> UNDER_REVIEW`, so reusing the generic transition would let a
  claimant push their own claim into review. The service checks `status == MORE_INFO_REQUIRED` explicitly (409 otherwise).
  Another claimant gets 404; an officer gets 403.
- **Open question:** should the answer go straight to `UNDER_REVIEW`, or wait for officer acknowledgement? I kept it
  simple and can explain the trade-off.

## B5. Officer queue, pick-up and status filter
- **AI proposed:** `GET /api/claims/queue` (unassigned `SUBMITTED`, oldest first), `PUT /api/claims/{id}/pickup`,
  and a `status` filter on the list.
- **CHALLENGED:** officers could not open an unassigned claim (404), so the queue would have listed claims they could not
  read. `verifyCanView` was changed: officers may read unassigned claims and their own, and still get 404 for another
  officer's claim. This is a **behaviour change** to the earlier rule, and new tests cover the three cases.
- **Why oldest first:** the fair order for claims that are waiting.

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

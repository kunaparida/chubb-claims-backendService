# Claims Management System

Monolithic REST API using Java 21, Spring Boot 3, Spring MVC, Spring Data JPA, Spring Security/JWT, Bean Validation, and H2. Errors use RFC 7807 `application/problem+json`; Swagger UI is at `/swagger-ui.html`.

## Run locally

Requirements: JDK 21 and Maven 3.9+.

```powershell
mvn spring-boot:run
```

The API listens at `http://localhost:8080`. H2 is in-memory and resets on restart. Its console is at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:claimsdb`, username `sa`, blank password).

| Role | Bootstrap email | Default local password |
|---|---|---|
| MANAGER | `manager@claims.local` | `ChangeMe123!` |
| OFFICER | `officer@claims.local` | `ChangeMe123!` |

Override the bootstrap password with `BOOTSTRAP_PASSWORD`, or disable bootstrap accounts with `BOOTSTRAP_USERS=false`. Public registration only creates CLAIMANT accounts. Set `JWT_SECRET` to a unique Base64-encoded secret with at least 32 decoded bytes and `JWT_EXPIRATION_MS` for deployment. Never use development credentials or the in-memory database in production.

## Authentication

1. `POST /api/auth/register` creates a claimant and returns a JWT.
2. `POST /api/auth/login` authenticates any role and returns a JWT.
3. Send the token on protected requests using the HTTP header `Authorization: Bearer <token>`, for example `Authorization: Bearer eyJ...`.
4. `GET /api/auth/me` returns the authenticated user's profile and ID.

Import `postman/Claims-Management.postman_collection.json` and run its requests in collection order. The auth requests capture tokens and IDs into collection variables; subsequent requests use `Authorization: Bearer {{claimantToken}}`, `Authorization: Bearer {{officerToken}}`, or `Authorization: Bearer {{managerToken}}`.

## Claim submission

`POST /api/claims` requires an authenticated claimant. All listed fields are required; claimant email must match the authenticated account, incident date cannot be in the future, and description must have at least 20 characters. `amountClaimed` must be greater than zero and have at most two decimal places.

```json
{
  "policyNumber": "POL-2026-00042",
  "claimantName": "Sample Claimant",
  "claimantEmail": "claimant@example.com",
  "incidentDate": "2026-10-10",
  "description": "Rear bumper damage from a parking collision.",
  "amountClaimed": 1250.00
}
```

The response includes the generated `claimNumber`, initial status, assigned officer (if any), and creation/update timestamps.

## API workflows

| Endpoint | Access | Purpose |
|---|---|---|
| `POST /api/claims` | CLAIMANT | Submit a claim |
| `GET /api/claims` | Any authenticated role | List own claims (claimant), assigned claims (officer), or all (manager) |
| `GET /api/claims/{id}` | Any authenticated role | Retrieve an authorized claim |
| `PUT /api/claims/{id}/assign` | OFFICER or MANAGER | Officer self-assigns an unassigned claim; manager can assign an officer with `{"officerId": 2}` |
| `PUT /api/claims/{id}/review` | Assigned OFFICER or MANAGER | Begin/resume review |
| `PUT /api/claims/{id}/request-info` | Assigned OFFICER or MANAGER | Request information with `{"note":"..."}` |
| `PUT /api/claims/{id}/approve` | Assigned OFFICER or MANAGER | Approve with decision note |
| `PUT /api/claims/{id}/reject` | Assigned OFFICER or MANAGER | Reject with decision note |
| `PUT /api/claims/{id}/settle` | Assigned OFFICER or MANAGER | Settle an approved claim with note |
| `GET /api/dashboard/workload` | MANAGER | Counts by status and unassigned/open totals |
| `GET /api/dashboard/liability` | MANAGER | Pending exposure and approved unsettled amount |
| `GET /api/dashboard/performance` | MANAGER | Assigned, active, and resolved counts per officer |

Allowed status transitions are `SUBMITTED → UNDER_REVIEW`; `UNDER_REVIEW → MORE_INFO_REQUIRED | APPROVED | REJECTED`; `MORE_INFO_REQUIRED → UNDER_REVIEW`; and `APPROVED → SETTLED`. REJECTED and SETTLED are terminal. Invalid transitions return HTTP 409. Claimants can only access their own claims; officers can access claims assigned to them; managers can access all claims. Managers assign officers using the ID returned by `GET /api/auth/me` after officer login.

## Persistence and tests

`src/main/resources/application.yml` configures H2 by default and contains a commented PostgreSQL example. PostgreSQL use requires adding the JDBC driver and configuring datasource settings. Hibernate schema generation is set to `update` for local use; use schema migrations in production.

```powershell
mvn test
```

# CLAUDE.md — Project rules

Claims-processing platform for Chubb APAC: motor and property claims across six markets.
Claimants report incidents, track claims, answer information requests and receive decisions.
Claims staff pick up, assess, settle or reject claims and see team workload and liability exposure.

**The user makes the decisions; Claude implements them.** Keep everything simple and explainable.
Do NOT add libraries, patterns, or extra services beyond what is listed here without asking first.
Do NOT modify `AI_JOURNAL.md` — it is the user's own file.

See `ARCHITECTURE.md` for the reasoning behind each rule.

## Environment
- Windows + PowerShell. Use `.\mvnw.cmd` (never assume a global `mvn`).
- Infra: `docker compose up -d` (PostgreSQL 16, single-node Kafka in KRaft mode). Backend runs on the host.
- Backend: Spring Boot 3.5.x, Java 21, Maven. Base package `com.chubb.claims`.

## Architecture
- ONE Spring Boot service (modular monolith). Modules are packages: `claim`, `notification`, `reporting`, `common`.
- Modules talk through Kafka domain events, not by reaching into each other's tables/repositories.
  `common` holds shared plumbing only — no business logic.

## REST vs Kafka
- REST for anything a user waits on: submit claim, view claim, staff queue, assign, decide, respond to info request.
- Kafka for domain events published AFTER a state change has committed:
  `ClaimSubmitted`, `ClaimStatusChanged`, `InformationRequested`, `ClaimDecided`.
- Consumers: `notification` (stores claimant notifications), `reporting` (exposure/workload figures).
- Consumers MUST be idempotent (events may be delivered more than once).
- We publish after commit for simplicity; the transactional outbox is the production approach (documented, not built).

## API contract
- Backend publishes an OpenAPI spec via springdoc (`/v3/api-docs`, UI at `/swagger-ui.html`). The frontend is built against it.

## Caching
- None, deliberately. Do not add caching. (Redis is noted as a production option for the exposure dashboard only.)

## Persistence
- PostgreSQL. Schema owned by Flyway migrations (`backend/src/main/resources/db/migration`); Hibernate `ddl-auto=validate`.
- `Claim` uses an optimistic-lock `version` column.

## Core entities
- `Policy` (seeded): policyNumber, holderName, holderEmail, market, productType (MOTOR|PROPERTY), currency, coverageLimit.
- `Claim`: claimNumber, policy, type, market, currency, incidentDate, description, estimatedAmount, reserveAmount,
  status, assignedOfficerId, createdAt, updatedAt, version.
- `ClaimStatusHistory`: claim, fromStatus, toStatus, changedBy, reason, changedAt.
- `InformationRequest`: claim, question, response, requestedBy, requestedAt, respondedAt.
- `Notification`: claimId, recipientEmail, message, createdAt.
- `StaffUser` (seeded): id, name, team.

## Claim lifecycle (rules live in ONE place in the domain, never in controllers)
```
SUBMITTED     -> UNDER_REVIEW    (officer picks it up)
UNDER_REVIEW  -> INFO_REQUESTED
INFO_REQUESTED-> UNDER_REVIEW    (claimant responds)
UNDER_REVIEW  -> APPROVED
APPROVED      -> SETTLED
UNDER_REVIEW  -> REJECTED
```
Any other transition is rejected with a clear error.

## Markets (sample; names assumed)
SG, MY, HK, TH, ID, AU — each with its own currency.

## Money
- Liability exposure = sum of `reserveAmount` on open claims (status not SETTLED/REJECTED),
  grouped by market AND currency. NEVER add amounts across currencies.

## Auth
- No real authentication (out of scope). Fake identity via headers `X-User-Id` and `X-User-Role` (CLAIMANT | STAFF),
  using seeded users.

## Testing
- JUnit 5 + Mockito (from spring-boot-starter-test). No extra test libraries without asking.

## Out of scope
Real auth/SSO, document uploads, payments, fraud checks, email delivery, caching, multi-tenancy, deployment.

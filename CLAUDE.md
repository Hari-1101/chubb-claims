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
- Swagger tags: "Claimant", "Staff". X-User-Id / X-User-Role are added to every operation (`OpenApiConfig`).

## API conventions
- Controllers are thin: validate (Bean Validation), call a service, return a DTO. No business rules in controllers.
- Request/response DTOs are Java records; never return JPA entities. Services are `@Transactional` and map
  entities to DTOs inside the transaction (open-in-view is off).
- Access control: `@RequireRole` on the controller (or method) → 403. Claimants only see their own claims
  (others' claims are 404). After assignment only the assigned officer can act on a claim (403).
- Errors are RFC 7807 ProblemDetail from `GlobalExceptionHandler`: 400 validation (with `errors` field list),
  401 identity, 403 access, 404 not found, 409 invalid transition / optimistic lock, 422 other business rules.
- CORS: only `http://localhost:4200` (property `claims.cors.allowed-origin`).

## Caching
- None, deliberately. Do not add caching. (Redis is noted as a production option for the exposure dashboard only.)

## Persistence
- PostgreSQL. Schema owned by Flyway migrations (`backend/src/main/resources/db/migration`); Hibernate `ddl-auto=validate`.
- `Claim` uses an optimistic-lock `version` column.

## Core entities
- `AppUser` (seeded, package `common`): id, name, email, role (CLAIMANT|STAFF), team (staff only).
  The id is what `X-User-Id` carries. Seeded ids: claimants `C001`–`C004`, staff `S001`–`S004`.
- `Policy` (seeded): policyNumber, holder (FK to AppUser, must be a CLAIMANT), market, productType (MOTOR|PROPERTY),
  currency, coverageLimit.
- `Claim`: claimNumber, policy, type, market, currency, incidentDate, description, estimatedAmount, reserveAmount,
  status, assignedOfficerId, createdAt, updatedAt, version.
- `ClaimStatusHistory`: claim, fromStatus, toStatus, changedBy, reason, changedAt.
- `InformationRequest`: claim, question, response, requestedBy, requestedAt, respondedAt.
- `Notification` (later step): claimId, recipientEmail, message, createdAt.

## Claim rules (all in `Claim` / `ClaimStatus`, never in controllers or services)
- Created via `Claim.submit(...)`: status SUBMITTED; estimatedAmount kept exactly as entered;
  reserveAmount = min(estimatedAmount, policy coverageLimit); type, market and currency
  copied from the policy; an initial history row (from = null, to = SUBMITTED) is written.
- incidentDate cannot be in the future ("today" = today in the claim's market time zone, see `Market`).
- reserveAmount must be >= 0 and <= policy coverageLimit (`Claim.changeReserve`).
- Claim number: `CLM-<MARKET>-<YEAR>-<6-digit seq>` from Postgres sequence `claim_number_seq` (`ClaimNumberGenerator`).
- Rule violations throw `BusinessRuleException`; invalid status moves throw `InvalidStatusTransitionException`
  (both in `common`).

## Claim lifecycle (rules live in ONE place in the domain, never in controllers)
```
SUBMITTED     -> UNDER_REVIEW    (officer picks it up)
UNDER_REVIEW  -> INFO_REQUESTED
INFO_REQUESTED-> UNDER_REVIEW    (claimant responds)
UNDER_REVIEW  -> APPROVED
APPROVED      -> SETTLED
UNDER_REVIEW  -> REJECTED
```
SETTLED and REJECTED are final. Any other transition is rejected with a clear error.
`ClaimStatus.allowedNextStatuses()` is the single source of truth; `Claim.transitionTo(newStatus, changedBy, reason)`
enforces it and writes a `ClaimStatusHistory` row.

## Markets (sample; names assumed)
SG, MY, HK, TH, ID, AU — each with its own currency.

## Money
- Liability exposure = sum of `reserveAmount` on open claims (status not SETTLED/REJECTED),
  grouped by market AND currency. NEVER add amounts across currencies.

## Auth
- No real authentication (out of scope). Fake identity via headers `X-User-Id` and `X-User-Role` (CLAIMANT | STAFF).
  `X-User-Id` must be an existing `AppUser.id`; that's how we know which claims belong to which claimant.

## Testing
- JUnit 5 + Mockito (from spring-boot-starter-test). No extra test libraries without asking.
- Domain rules are tested with plain unit tests (no Spring context, no Docker). `ClaimsApplicationTests` needs
  `docker compose up -d` and proves migrations + entity mappings line up.

## Out of scope
Real auth/SSO, document uploads, payments, fraud checks, email delivery, caching, multi-tenancy, deployment.

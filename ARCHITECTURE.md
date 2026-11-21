# Architecture

This document explains *what* we built and *why*. The short-form rules live in `CLAUDE.md`.

## 1. Overview

```
            ┌──────────────┐        REST (OpenAPI)        ┌──────────────────────────────────────────┐
 Claimant ─▶│              │ ───────────────────────────▶ │  claims service (Spring Boot, one JVM)   │
 Staff    ─▶│   Frontend   │                              │                                          │
            └──────────────┘                              │  claim ──publish──┐                      │
                                                          │                   ▼                      │
                                                          │               ┌────────┐                 │
                                                          │               │ Kafka  │                 │
                                                          │               └────────┘                 │
                                                          │          consume ▼      ▼ consume        │
                                                          │        notification   reporting          │
                                                          └──────────────┬───────────────────────────┘
                                                                         ▼
                                                                    PostgreSQL 16
```

- **Frontend** (later) talks to the backend over REST only.
- **Backend** is a single Spring Boot 3.5 / Java 21 service.
- **PostgreSQL** stores all state; schema is managed with Flyway.
- **Kafka** (single node, KRaft mode — no ZooKeeper) carries domain events between modules.

## 2. Modular monolith

One deployable service, split into modules as Java packages under `com.chubb.claims`:

| Module         | Responsibility |
|----------------|----------------|
| `claim`        | Policies, claims, lifecycle rules, information requests, staff assignment. Owns the REST API and publishes domain events. |
| `notification` | Consumes claim events and stores notifications for claimants. |
| `reporting`    | Consumes claim events and keeps liability exposure and team workload figures up to date. |
| `common`       | Shared plumbing (error handling, identity headers). No business logic. |

**Why not microservices?** At this scale microservices add deployment, monitoring, networking and data-consistency
cost with no real benefit. A single service is simpler to run, test and debug.

**How we keep the option open:** modules don't reach into each other's tables or repositories; `notification` and
`reporting` learn about changes only through Kafka events. If one ever needs to scale or be owned by another team,
it can be lifted out into its own service with the same event contract.

## 3. REST vs Kafka

**REST** is used for anything a user is waiting on and needs an immediate answer to:

- Claimant: submit claim, view own claims, respond to an information request.
- Staff: view the queue, assign (pick up) a claim, request information, approve / reject / settle.

**Kafka** is used for domain events — facts about something that has *already happened* — published only after the
state change has been committed to the database:

| Event                  | When |
|------------------------|------|
| `ClaimSubmitted`       | A claimant submits a new claim. |
| `ClaimStatusChanged`   | Any lifecycle transition. |
| `InformationRequested` | An officer asks the claimant for more information. |
| `ClaimDecided`         | A claim is approved or rejected. |

Consumers:

- `notification` — writes a `Notification` row for the claimant (e.g. "We need more information on claim CLM-…").
- `reporting` — updates exposure-by-market-and-currency and workload-per-team figures.

**Idempotency.** Kafka gives at-least-once delivery, so the same event can arrive twice (retries, rebalances).
Every consumer must be safe to run twice for the same event — e.g. by recording processed event IDs or by
writing with a natural unique key so a duplicate becomes a no-op.

### Publish-after-commit vs. transactional outbox

We publish the event **after** the database transaction commits. That is simple, and guarantees we never announce a
change that was rolled back. The trade-off: if the process crashes between commit and publish, the event is lost.

The production approach is the **transactional outbox**: write the event to an `outbox` table in the *same*
transaction as the state change, and have a separate relay publish outbox rows to Kafka (and mark them sent).
This gives "change and event both happen or neither does". We document it here but don't build it, to keep the
codebase small.

## 4. API contract

The backend generates an OpenAPI 3 spec from the controllers using springdoc:

- Spec: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

The frontend is built against this spec, so the backend is the single source of truth for the API shape.

## 5. Caching — none, deliberately

- Data volume is small; PostgreSQL with sensible indexes answers these queries quickly.
- Claim status is exactly what users check; a cache risks showing a stale status after a decision.
- Fewer moving parts to explain and operate.

If the exposure dashboard ever becomes expensive, **Redis** (or a precomputed table refreshed by the `reporting`
consumer, which we already have) is the production option.

## 6. Domain model

| Entity               | Key fields | Notes |
|----------------------|-----------|-------|
| `AppUser`            | id, name, email, role (CLAIMANT\|STAFF), team | Seeded. One table for claimants and staff; team is set for staff only (DB CHECK). Lives in `common` because identity is shared by all modules. |
| `Policy`             | policyNumber, holder → AppUser, market, productType (MOTOR\|PROPERTY), currency, coverageLimit | Seeded via Flyway. Holder must be a claimant. Currency always matches the market (DB CHECK + constructor). |
| `Claim`              | claimNumber, policy, type, market, currency, incidentDate, description, estimatedAmount, reserveAmount, status, assignedOfficerId, createdAt, updatedAt, version | `version` = optimistic lock, so two officers can't silently overwrite each other. |
| `ClaimStatusHistory` | claim, fromStatus, toStatus, changedBy, reason, changedAt | Audit trail: one row per transition, plus an initial row (from = null → SUBMITTED). |
| `InformationRequest` | claim, question, response, requestedBy, requestedAt, respondedAt | Open while `response` is empty. |
| `Notification`       | claimId, recipientEmail, message, createdAt | Later step. Written by the `notification` consumer. |

**Why one `AppUser` instead of a separate staff table?** The fake `X-User-Id` header has to map to a real user so
we can tell which claims belong to which claimant (via `Policy.holder`) and who is staff. One table with a role keeps
that lookup to a single place.

Market and currency are copied onto the claim from its policy at submission time, so reporting doesn't depend on
later policy changes.

### Schema (Flyway)

- `V1__schema.sql` — tables `app_user`, `policy`, `claim`, `claim_status_history`, `information_request`, plus the
  sequence `claim_number_seq`. Foreign keys everywhere, CHECK constraints for enum values, non-negative amounts and
  market/currency pairs. Money is `NUMERIC(15,2)`, timestamps `TIMESTAMPTZ`. Indexes on `claim(status)`,
  `claim(assigned_officer_id)`, `claim(policy_id)` and on the foreign keys of the child tables.
- `V2__seed_data.sql` — 4 claimants (`C001`–`C004`), 4 staff (`S001`–`S002` Motor, `S003`–`S004` Property) and
  8 policies covering all six markets.

The database constraints repeat the most important domain rules on purpose: the Java code gives clear error
messages, the database guarantees bad data can never be stored even if a bug slips through.

### Claim rules

All in `Claim` (and `ClaimStatus` for transitions), so they're tested without Spring or a database:

- **Creation** (`Claim.submit`): status `SUBMITTED`, type/market/currency copied from the policy. The estimate is
  stored exactly as the claimant entered it; the reserve starts at `min(estimate, coverage limit)`. A claimant may
  well estimate more than their cover; that isn't a reason to reject the claim, but our liability can't exceed it.
- **Incident date** can't be in the future. "Today" is evaluated in the claim's market time zone (`Market.zone()`),
  so a claimant in Sydney isn't rejected because the server clock is still on yesterday.
- **Reserve** must be ≥ 0 and ≤ the policy coverage limit (`Claim.changeReserve`).
- **Claim number**: `CLM-<MARKET>-<YEAR>-<6-digit sequence>`, e.g. `CLM-SG-2026-000001`. The sequence part comes
  from a single Postgres sequence (unique under concurrency, shared by all markets, never reset). A possible
  production change is a separate sequence per market (and/or per year), so each market's numbers run 000001,
  000002, … on their own; that needs a sequence per market or a counter table, so we keep one sequence for now.
- Rule violations throw `BusinessRuleException`; invalid status moves throw `InvalidStatusTransitionException`.

## 7. Claim lifecycle

```
                 officer picks up
  SUBMITTED ───────────────────────▶ UNDER_REVIEW ◀────────────┐
                                     │   │    │                │ claimant responds
                                     │   │    └──▶ INFO_REQUESTED
                                     │   │
                                     │   └──▶ REJECTED   (terminal)
                                     ▼
                                  APPROVED ──▶ SETTLED   (terminal)
```

Allowed transitions:

| From             | To               |
|------------------|------------------|
| SUBMITTED        | UNDER_REVIEW     |
| UNDER_REVIEW     | INFO_REQUESTED   |
| INFO_REQUESTED   | UNDER_REVIEW     |
| UNDER_REVIEW     | APPROVED         |
| UNDER_REVIEW     | REJECTED         |
| APPROVED         | SETTLED          |

`SETTLED` and `REJECTED` are final. Everything else is rejected with a clear error, e.g.
"Cannot move claim CLM-SG-2026-000001 from SUBMITTED to APPROVED. Allowed next statuses: [UNDER_REVIEW]."

The transition table lives in **one place**: `ClaimStatus.allowedNextStatuses()`. `Claim.transitionTo(newStatus,
changedBy, reason)` is the only way to change status; it checks the table, throws
`InvalidStatusTransitionException` for invalid moves and writes a `ClaimStatusHistory` row for valid ones.
Controllers and services never decide whether a move is allowed.

## 8. Markets and money

Six sample markets (names assumed):

| Code | Market      | Currency | Reference time zone |
|------|-------------|----------|---------------------|
| SG   | Singapore   | SGD | Asia/Singapore |
| MY   | Malaysia    | MYR | Asia/Kuala_Lumpur |
| HK   | Hong Kong   | HKD | Asia/Hong_Kong |
| TH   | Thailand    | THB | Asia/Bangkok |
| ID   | Indonesia   | IDR | Asia/Jakarta |
| AU   | Australia   | AUD | Australia/Sydney |

Defined in the `Market` enum (`common`). Indonesia and Australia span several time zones; we use one reference zone
per market for simplicity.

**Liability exposure** = sum of `reserveAmount` on open claims (status not `SETTLED` / `REJECTED`),
grouped by **market and currency**. Amounts in different currencies are **never** added together — there is no
FX conversion in scope, and a single "total" across currencies would be meaningless.

## 9. Identity (fake auth)

Real authentication is out of scope. Each request carries:

- `X-User-Id` — an `AppUser.id` (seeded: claimants `C001`–`C004`, staff `S001`–`S004`)
- `X-User-Role` — `CLAIMANT` or `STAFF` (must match the user's role)

The backend uses these to decide what the caller may see and do (claimants see only their own claims; only staff
can assign and decide). In production this would be replaced by SSO/OAuth2 tokens.

## 10. Testing

JUnit 5 + Mockito (via `spring-boot-starter-test`). Priority is unit tests on the domain rules (lifecycle
transitions, exposure grouping), then focused tests on services and controllers.

## 11. Out of scope

Real auth/SSO, document uploads, payments, fraud checks, email delivery, caching, multi-tenancy, deployment.

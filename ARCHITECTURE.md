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
| `Policy`             | policyNumber, holderName, holderEmail, market, productType (MOTOR\|PROPERTY), currency, coverageLimit | Seeded via Flyway. |
| `Claim`              | claimNumber, policy, type, market, currency, incidentDate, description, estimatedAmount, reserveAmount, status, assignedOfficerId, createdAt, updatedAt, version | `version` = optimistic lock, so two officers can't silently overwrite each other. |
| `ClaimStatusHistory` | claim, fromStatus, toStatus, changedBy, reason, changedAt | Audit trail: one row per transition. |
| `InformationRequest` | claim, question, response, requestedBy, requestedAt, respondedAt | Open while `response` is empty. |
| `Notification`       | claimId, recipientEmail, message, createdAt | Written by the `notification` consumer. |
| `StaffUser`          | id, name, team | Seeded; used for assignment and workload. |

Market and currency are copied onto the claim from its policy at submission time, so reporting doesn't depend on
later policy changes.

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

Everything else is rejected with a clear error (e.g. "Cannot move claim CLM-… from SUBMITTED to APPROVED").
The transition table lives in **one place in the domain** (the claim status / claim entity), never in controllers,
so the rule is easy to find, test and explain. Every successful transition writes a `ClaimStatusHistory` row.

## 8. Markets and money

Six sample markets (names assumed):

| Code | Market      | Currency |
|------|-------------|----------|
| SG   | Singapore   | SGD |
| MY   | Malaysia    | MYR |
| HK   | Hong Kong   | HKD |
| TH   | Thailand    | THB |
| ID   | Indonesia   | IDR |
| AU   | Australia   | AUD |

**Liability exposure** = sum of `reserveAmount` on open claims (status not `SETTLED` / `REJECTED`),
grouped by **market and currency**. Amounts in different currencies are **never** added together — there is no
FX conversion in scope, and a single "total" across currencies would be meaningless.

## 9. Identity (fake auth)

Real authentication is out of scope. Each request carries:

- `X-User-Id` — a seeded user (policy holder for claimants, `StaffUser.id` for staff)
- `X-User-Role` — `CLAIMANT` or `STAFF`

The backend uses these to decide what the caller may see and do (claimants see only their own claims; only staff
can assign and decide). In production this would be replaced by SSO/OAuth2 tokens.

## 10. Testing

JUnit 5 + Mockito (via `spring-boot-starter-test`). Priority is unit tests on the domain rules (lifecycle
transitions, exposure grouping), then focused tests on services and controllers.

## 11. Out of scope

Real auth/SSO, document uploads, payments, fraud checks, email delivery, caching, multi-tenancy, deployment.

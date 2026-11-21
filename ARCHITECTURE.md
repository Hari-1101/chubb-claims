# Architecture

This document explains *what* we built and *why*. The short-form rules live in `CLAUDE.md`.

## 1. Overview

```
            ┌──────────────┐        REST (OpenAPI)        ┌──────────────────────────────────────────┐
 Claimant ─▶│   Frontend   │ ───────────────────────────▶ │  claims service (Spring Boot, one JVM)   │
 Staff    ─▶│  (Angular)   │                              │                                          │
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

- **Frontend** (Angular 22) talks to the backend over REST only (see section 10).
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
| `common`       | Shared plumbing: identity headers, error handling, OpenAPI/Kafka config, the `ClaimEvent` contract, consumer idempotency (`ProcessedEventStore`). No business logic. |

**Why not microservices?** At this scale microservices add deployment, monitoring, networking and data-consistency
cost with no real benefit. A single service is simpler to run, test and debug.

**How we keep the option open:** modules don't reach into each other's tables or repositories; `notification` and
`reporting` learn about changes only through Kafka events. If one ever needs to scale or be owned by another team,
it can be lifted out into its own service with the same event contract.

## 3. REST vs Kafka

### What uses which, and why

| Interaction | Channel | Why |
|-------------|---------|-----|
| Submit claim, view my claims/claim detail, answer an information request | REST | The claimant is waiting for the result (claim number, validation errors). |
| Staff queue, assign, request info, change reserve, approve, reject, settle | REST | The officer needs an immediate yes/no (e.g. 409 if the transition isn't allowed). |
| Read notifications, exposure and workload reports | REST | A user is looking at a screen; reads come from the module's own tables. |
| Telling other modules "a claim changed" | Kafka (`claims.events`) | Fire-and-forget fact about the past. The claim module doesn't wait for, or know about, its consumers. |
| Creating claimant notifications | Kafka consumer (`claims-notification`) | Side effect that must not slow down or fail the officer's request. |
| Updating exposure / workload figures | Kafka consumer (`claims-reporting`) | Read model kept separately, so reports never query the claim tables and could move to their own service. |

### Event flow

```mermaid
sequenceDiagram
    autonumber
    actor User as Claimant / Officer
    participant API as claim module<br/>(REST + service)
    participant DB as PostgreSQL
    participant K as Kafka<br/>claims.events (3 partitions)
    participant N as notification module<br/>group claims-notification
    participant R as reporting module<br/>group claims-reporting

    User->>API: POST /api/... (e.g. approve)
    API->>DB: BEGIN; update claim + history; flush (version++)
    API->>API: publishEvent(ClaimEvent) (held until commit)
    API->>DB: COMMIT
    API-->>User: 200 OK (claim detail)
    API->>K: AFTER_COMMIT: send(key = claimNumber, ClaimEvent JSON)
    par independent consumers
        K->>N: ClaimEvent
        N->>DB: BEGIN; insert processed_event (skip if exists); insert notification; COMMIT
    and
        K->>R: ClaimEvent
        R->>DB: BEGIN; insert processed_event (skip if exists); upsert reporting_claim if claimVersion is newer; COMMIT
    end
```

### Events

One topic, `claims.events`, 3 partitions, created by a `NewTopic` bean. The topic name and a consumer-group suffix
are properties (`claims.events.topic`, `claims.events.group-suffix`) so integration tests can use their own
(`claims.events.test`, groups `*.test`; see section 12). The message key is the claim number, so
all events of one claim land on the same partition and are consumed in order.

| Event | Published when |
|-------|----------------|
| `ClaimSubmitted` | A claimant submits a claim |
| `ClaimAssigned` | An officer picks up a claim (→ UNDER_REVIEW) |
| `InformationRequested` | Officer asks a question (→ INFO_REQUESTED) |
| `InformationResponded` | Claimant answers (→ UNDER_REVIEW) |
| `ReserveChanged` | Officer changes the reserve |
| `ClaimApproved` / `ClaimRejected` | Decision made |
| `ClaimSettled` | Approved claim settled |

JSON envelope (`common.ClaimEvent`): `eventId` (UUID), `eventType`, `occurredAt`, `claimNumber`, `claimVersion`,
plus a snapshot of the claim after the change: `status`, `market`, `currency`, `type`, `reserveAmount`,
`assignedOfficerId`, `claimantId`, `submittedAt`. Because each event carries the full snapshot, consumers never
need to call back into the claim module. `claimVersion` is the claim's optimistic-lock version after the change,
so it increases with every change.

### Consumers

- **notification** stores a friendly message for the claimant on `ClaimSubmitted`, `InformationRequested`,
  `ClaimApproved`, `ClaimRejected` and `ClaimSettled` (e.g. "We need more information about claim
  CLM-SG-2026-000005. …"). Claimants read them via `GET /api/me/notifications`.
- **reporting** keeps its own read model, `reporting_claim` (one row per claim), built only from events. Exposure
  and workload reports are queries on that table.

### Idempotency and ordering

Kafka delivers at least once, so the same event can arrive twice (retries, rebalances, restarts).

1. **`processed_event` (eventId + consumer group).** Each consumer inserts this row with
   `ON CONFLICT DO NOTHING` *in the same transaction* as its work. If the row already exists, the event is skipped.
   If the consumer crashes mid-way, both roll back and the redelivered event is processed normally.
2. **`claimVersion` (reporting only).** A snapshot whose `claimVersion` is ≤ the row's `lastClaimVersion` is
   ignored, so a late or replayed older event can never overwrite newer data.

### Publish after commit, and the gap it leaves

Services raise a Spring application event inside their transaction; `ClaimEventPublisher` sends it to Kafka from a
`@TransactionalEventListener(phase = AFTER_COMMIT)`. So we never announce a change that was rolled back.

**The gap:** if the database commit succeeds but the Kafka send fails (Kafka down, network error, or the process
dies between commit and send), the event is lost. The claim itself is correct, but the claimant gets no
notification and the reports miss the change. We log this loudly (`EVENT LOST: …` with the event id) and fail fast
(`max.block.ms = 5000`) so the user's request isn't held up.

**Production fix: transactional outbox.** Write the event to an `outbox` table in the *same* transaction as the claim
change; a separate relay (polling, or CDC such as Debezium) publishes outbox rows to Kafka and marks them sent. Then
"claim changed" and "event recorded" happen together or not at all; delivery becomes at-least-once, which our
idempotent consumers already handle. We don't build it here to keep the codebase small.

### Eventual consistency

Notifications and reports are updated asynchronously, typically within a second of the change, but not in the same
request. Right after an action a report may briefly show the old figures. Every report includes `lastUpdated` (when
the read model last changed) so users can see how fresh it is. The claim detail itself (REST, claim tables) is
always up to date.

Claims created before the event flow existed are not in the reporting read model; a production rollout would
backfill it by replaying or seeding events. (A fresh database, e.g. after `docker compose down -v`, has no such
claims.)

## 4. API contract

The backend generates an OpenAPI 3 spec from the controllers using springdoc:

- Spec: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

The frontend is built against this spec, so the backend is the single source of truth for the API shape.

### Endpoints

| Method | Path | Role | What it does |
|--------|------|------|--------------|
| GET  | `/api/me/policies` | CLAIMANT | My policies |
| POST | `/api/claims` | CLAIMANT | Submit a claim on my own policy → 201 |
| GET  | `/api/claims/mine` | CLAIMANT | My claims, newest first |
| GET  | `/api/claims/{claimNumber}` | CLAIMANT, STAFF | Detail + status history + information requests (claimants: own only, else 404) |
| POST | `/api/claims/{claimNumber}/information-requests/{requestId}/response` | CLAIMANT | Answer → UNDER_REVIEW |
| GET  | `/api/staff/claims?status&market&type&assigned=me\|unassigned\|any&page&size` | STAFF | Work queue, oldest first, paginated; open claims only unless a status is given |
| POST | `/api/staff/claims/{claimNumber}/assign` | STAFF | Assign to me → UNDER_REVIEW |
| POST | `/api/staff/claims/{claimNumber}/information-requests` | STAFF | Ask the claimant → INFO_REQUESTED |
| PUT  | `/api/staff/claims/{claimNumber}/reserve` | STAFF | Change reserve (0 ≤ amount ≤ coverage limit, reason required, audited) |
| POST | `/api/staff/claims/{claimNumber}/approve` | STAFF | → APPROVED (reason optional) |
| POST | `/api/staff/claims/{claimNumber}/reject` | STAFF | → REJECTED (reason required) |
| POST | `/api/staff/claims/{claimNumber}/settle` | STAFF | → SETTLED |
| GET  | `/api/me/notifications` | CLAIMANT | My notifications, newest first (async, from events) |
| GET  | `/api/staff/reports/exposure` | STAFF | Open-claim exposure by market + currency (+ by type) |
| GET  | `/api/staff/reports/workload?team=` | STAFF | Per-officer workload, unassigned count, 7-day team performance |

### Layers

`Controller` (validate input, return DTO) → `Service` (`@Transactional`: load, check access, call the domain,
map to DTO) → `Claim` / `ClaimStatus` (business rules) → `Repository`. DTOs are records; entities never leave
the service layer. Mapping happens inside the transaction because open-in-view is off.

`reserveAmount` is an internal liability figure: it is omitted from every response sent to a claimant and shown
to staff. (One DTO with the field left out for claimants, rather than two parallel DTO sets.)

Swagger UI: click **Authorize** and enter `X-User-Id` and `X-User-Role` once; they are sent with every call and
kept across page reloads.

### Errors (RFC 7807 ProblemDetail)

| Status | When |
|--------|------|
| 400 | Bean Validation failures (field list in `errors`), malformed JSON, bad parameter values |
| 401 | Missing/unknown `X-User-Id`, invalid `X-User-Role`, or role doesn't match the user |
| 403 | Wrong role for the endpoint; someone else's policy; claim assigned to another officer (or not assigned yet) |
| 404 | Unknown claim/policy/request, or a claimant asking for someone else's claim |
| 409 | Invalid status transition; optimistic-lock conflict (two people changed the same claim at once) |
| 422 | Other business rules (e.g. incident date in the future, reserve above coverage, request already answered) |

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
| `ReserveChange`      | claim, oldAmount, newAmount, changedBy, reason, changedAt | Audit trail of reserve changes (reason required). |
| `Notification`       | recipientId, recipientEmail, claimNumber, message, createdAt | `notification` module, written by its Kafka consumer. Uses `claimNumber` (not a FK to `claim`) because the module only knows what events tell it; `recipientId` lets claimants list their own. |
| `ReportingClaim`     | claimNumber, status, market, currency, type, reserveAmount, assignedOfficerId, submittedAt, decidedAt, lastClaimVersion, updatedAt | `reporting` module read model, one row per claim, built only from events. No FKs to the claim tables. |

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
- `V3__events_notifications_reporting.sql` — `reserve_change` (audit), `notification`, `processed_event`
  (PK eventId + consumer group) and `reporting_claim` (read model, indexed on status, assigned officer, decided_at).

### Reports

Both come from `reporting_claim` only, and include `lastUpdated`.

- **Exposure** (`GET /api/staff/reports/exposure`): for open claims (not SETTLED/REJECTED), open claim count and
  total reserve per **market + currency**, with a breakdown by type. There is no grand total: amounts in different
  currencies are never added.
- **Workload** (`GET /api/staff/reports/workload?team=`): per officer (all staff, or one team): open claims
  assigned, count per status, oldest open claim (timestamp and age in whole days). Plus the number of open
  unassigned claims (all teams: an unassigned claim has no team yet) and team performance: claims approved/rejected
  in the last 7 days by those officers and the average hours from submission to decision for those claims.

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

How it works:

- `IdentityInterceptor` runs on every `/api/**` request: loads the `AppUser` (401 if headers are missing, the user
  is unknown, or the role doesn't match) and stores a `CurrentUser` on the request.
- Controllers receive it as a method parameter (`CurrentUser user`) via `CurrentUserArgumentResolver`.
- `@RequireRole(...)` on a controller or method restricts it to roles (403 otherwise).
- Finer rules live in the services: claimants only see their own claims (404 for others, so we don't reveal they
  exist); only the assigned officer can act on an assigned claim (403).

In production this would be replaced by SSO/OAuth2 tokens (e.g. Spring Security resource server); only the
interceptor would change.

## 10. Frontend

Angular 22 in `/frontend`: standalone components, signals, zoneless change detection, plain SCSS (no component
library). It only talks to the backend's REST API; DTO shapes are TypeScript interfaces in
`core/models/api.models.ts`, matching the OpenAPI spec.

| Folder | Contents |
|---|---|
| `core/` | Demo login and `AuthStore` (signal + sessionStorage), `authInterceptor` (adds `X-User-Id` / `X-User-Role` to `/api` calls), `roleGuard`, `errorInterceptor` + error store, API services (the only place that calls HTTP), header |
| `claimant/` | My claims, new claim, claim detail (with answer form), notifications |
| `staff/` | Work queue, staff claim detail with actions, dashboard; `claim-actions.ts` |
| `shared/` | Status badge, money pipe (always formats an amount in its own currency), error banner, timeline, info requests, claim facts, validators |

- **Errors**: the backend's ProblemDetail (title, detail, field errors) is shown in one banner. Background calls
  (notification count every 15 s, dashboard refresh every 10 s) are marked silent so they don't flood it; a 401
  sends the user back to the login page.
- **Staff actions**: which buttons appear is decided in one function, `availableActions(claim, userId)`, which
  mirrors `ClaimStatus.allowedNextStatuses()` and the "only the assigned officer" rule. It only hides buttons
  that would fail; the backend remains the authority.
- **Forms**: every form uses reactive forms with `[formGroup]` and `(ngSubmit)`. Client-side validation mirrors
  the backend's Bean Validation (required, not blank, max length, amount with 2 decimals, no future incident date).
- **Eventual consistency in the UI**: notifications and the dashboard say they update a moment after an action;
  the dashboard shows `lastUpdated`.

## 11. Running it

```
 browser ──▶ :4200 nginx (frontend) ──/api──▶ :8080 backend ──▶ postgres:5432
                                                   │
                                                   └──▶ kafka:29092 (containers) / localhost:9092 (host)
```

- `docker compose up --build` builds two images and starts four containers, in order, using healthchecks:
  Postgres and Kafka → backend (healthy when `/actuator/health` is UP) → frontend.
  - **Backend image**: multi-stage, Maven wrapper build on JDK 21, runs as a non-root user on a Java 21 JRE (alpine).
  - **Frontend image**: Node build, then nginx serves the static app (falling back to `index.html` for client-side
    routes) and proxies `/api` to the backend, so the browser sees one origin.
- Kafka has two client listeners: `kafka:29092` for containers and `localhost:9092` for a backend started on the
  host. That keeps the development workflow (`docker compose up -d postgres kafka`, then `.mvnw.cmd
  spring-boot:run` and `npm start` with `proxy.conf.json`) working next to the all-in-Docker one.
- Postgres keeps its data in the `postgres-data` volume; `docker compose down -v` resets to the seed data.
  Kafka has no volume: its topics are recreated on start (consumers are idempotent and the read model is in Postgres).

## 12. Testing

JUnit 5 + Mockito (via `spring-boot-starter-test`), Awaitility for async assertions; Vitest for the frontend.

- **Domain unit tests** (no Spring, no Docker): lifecycle transitions, claim creation, reserve rules, claim numbers.
- **Service and consumer unit tests** (Mockito): access rules, events published only on success, duplicate
  delivery ignored, stale `claimVersion` ignored, exposure never mixing currencies.
- **Integration tests** (`@SpringBootTest`, need Postgres + Kafka): `ClaimApiIntegrationTest` (REST + database,
  rolled back), `ClaimsApplicationTests` (migrations match the entities), `EventFlowIntegrationTest` (real commit
  → Kafka → both consumers; not transactional).
- **Test isolation**: every `@SpringBootTest` runs with profile `test` (`application-test.yml`): database
  `claims_test` (created by `docker/postgres/create-test-db.sql` on a fresh volume), topic `claims.events.test`,
  groups `claims-notification.test` / `claims-reporting.test`. Tests never write demo data, and a running app
  never consumes test events.
- **Frontend**: the header interceptor, the role guard, which staff action buttons appear, and that each action form
  really submits (fills in the form, clicks submit, checks the API call and the reloaded claim). The last group was
  added after a manual browser test found forms that silently did nothing.

## 13. Out of scope

Real auth/SSO, document uploads, payments, fraud checks, email delivery, caching, multi-tenancy, production
deployment (Docker Compose is for local runs and demos only).

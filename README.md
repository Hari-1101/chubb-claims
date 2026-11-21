# Chubb APAC Claims Platform

A claims-processing platform for motor and property insurance across six APAC markets (SG, MY, HK, TH, ID, AU).
Claimants report incidents, track their claims and answer questions from claims staff. Officers pick up claims,
assess them, approve, reject or settle them, and see liability exposure and team workload on a dashboard.

**Claimants can**
- see their policies and submit a claim (incident date, description, estimated amount in the policy's currency);
- follow each claim's status, with a timeline of who changed what and why;
- answer an officer's information request;
- read notifications about their claims (count shown in the header).

**Claims staff can**
- work a queue of claims, filtered by status, market, type and assignment (default: open claims), and pick one up;
- request information, change the reserve (with a reason, audited), approve, reject (reason required) and settle.
  Only the assigned officer can act, and only the actions valid for the current status are offered;
- see liability exposure per market and currency, and per-officer workload, refreshed from claim events.

## Quick start (everything in Docker)

Prerequisite: Docker Desktop.

```powershell
docker compose up --build
```

The first build takes a few minutes (Maven and npm downloads). When `claims-frontend` has started:

| What | URL |
|---|---|
| App (UI) | http://localhost:4200 |
| Swagger UI (API) | http://localhost:8080/swagger-ui.html |
| OpenAPI spec | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |

Stop with `Ctrl+C` or `docker compose down`. **Reset to a clean demo** (deletes all claims, keeps the seed data):

```powershell
docker compose down -v
docker compose up --build
```

### Demo users

There is no real login. The login page lists the seeded users; picking one sends their id and role in the
`X-User-Id` / `X-User-Role` headers. The same values work in Swagger's **Authorize** dialog.

| Id | Name | Role | Team | Policies |
|---|---|---|---|---|
| C001 | Mei Ling Tan | Claimant | — | POL-SG-MOT-001, POL-SG-PRP-001 |
| C002 | Ahmad Rahman | Claimant | — | POL-MY-MOT-001, POL-ID-PRP-001 |
| C003 | Somchai Wong | Claimant | — | POL-TH-MOT-001, POL-HK-PRP-001 |
| C004 | Olivia Hughes | Claimant | — | POL-AU-MOT-001, POL-AU-PRP-001 |
| S001 | Daniel Lim | Staff | Motor | |
| S002 | Priya Nair | Staff | Motor | |
| S003 | Kenji Ho | Staff | Property | |
| S004 | Sarah Wijaya | Staff | Property | |

The list lives in `frontend/src/app/core/auth/demo-users.ts` and must match `V2__seed_data.sql`.

## Run locally for development

Prerequisites: Java 21, Node 22.22+ / 24.15+ / 26+ (Angular 22), Docker Desktop.

```powershell
# 1. Infrastructure only (Postgres + Kafka)
docker compose up -d postgres kafka

# 2. Backend (http://localhost:8080)
cd backend
.\mvnw.cmd spring-boot:run

# 3. Frontend, in a second terminal (http://localhost:4200, live reload)
cd frontend
npm install
npm start
```

`npm start` uses `proxy.conf.json`, so `/api` calls go to the backend on port 8080. Don't run this alongside the
full Docker stack: both use ports 8080 and 4200.

## 5-minute demo

Open http://localhost:4200.

1. **Mei Ling Tan (C001)** — *New claim*: pick `POL-SG-MOT-001` (the amount field shows SGD), incident date
   yesterday, a short description, amount `4500`. Submit; you land on the claim. *Notifications* shows
   "We've received your claim…" within a few seconds.
2. **Switch user → Daniel Lim (S001)** — *Work queue*, set *Assigned* to "Unassigned", click **Assign to me** on
   the new claim. Click **Change reserve**: `4000`, reason "Workshop quote". Then **Request information**:
   "Please provide the police report number". The status becomes *Info requested*.
3. **Switch user → Mei Ling Tan (C001)** — open the claim; the question is highlighted. Answer it.
4. **Switch user → Priya Nair (S002)** — open the same claim from the queue: no action buttons, only
   "Only the assigned officer can act on this claim".
5. **Switch user → Daniel Lim (S001)** — open the claim, **Approve** it, then **Mark as settled**. Open the
   *Dashboard*: SG/SGD exposure went up after step 2 and back down after settling (it updates within ~10 seconds).
6. **Switch user → Mei Ling Tan (C001)** — the timeline shows every step; the notification count went up.

## Architecture

Details and reasoning: [ARCHITECTURE.md](ARCHITECTURE.md). Short rules for contributors: [CLAUDE.md](CLAUDE.md).

- **Modular monolith**: one Spring Boot service (Java 21) with modules as packages: `claim` (REST API, lifecycle
  rules), `notification`, `reporting`, and `common` (plumbing only). Modules don't touch each other's tables; they
  communicate through Kafka events.
- **Frontend**: Angular 22 (standalone components, signals, plain SCSS), served by nginx in Docker.
- **Storage**: PostgreSQL 16, schema owned by Flyway migrations; Hibernate only validates it.

**REST vs Kafka**

| Interaction | Channel | Why |
|---|---|---|
| Submit, view, answer, staff queue and actions, read notifications and reports | REST | A user is waiting for the result or an error. |
| "A claim changed" (`ClaimSubmitted`, `ClaimAssigned`, `InformationRequested`, `InformationResponded`, `ReserveChanged`, `ClaimApproved`, `ClaimRejected`, `ClaimSettled`) | Kafka topic `claims.events` | A fact for other modules; the claim module doesn't wait for its consumers. |
| Claimant notifications | Kafka consumer `claims-notification` | A side effect that must not slow down or fail the officer's request. |
| Exposure and workload figures | Kafka consumer `claims-reporting` | Separate read model; reports never query the claim tables. |

**Event flow**: the service changes the claim and commits; only *after* the commit is the event (key = claim
number, JSON with a snapshot of the claim and its version) sent to Kafka. Rolled-back changes are never announced.

**Read model**: `reporting` keeps its own `reporting_claim` table, built only from events. It ignores an event
whose claim version is not newer than what it has, so late or replayed events can't overwrite newer data.
Reports include `lastUpdated`, because they trail the claim by a moment (eventual consistency).

**Idempotency**: Kafka can deliver an event twice. Each consumer records `(eventId, consumer group)` in
`processed_event` in the same transaction as its work and skips events it has already processed.

## Key decisions

| Decision | Why |
|---|---|
| Spring Boot 3.5 (not 4) | A mature, widely documented line that all the chosen libraries (e.g. springdoc 2.x) support. |
| One service, modules as packages | Simple to run, test and debug at this size; modules still talk via events, so one can be split out later. |
| PostgreSQL + Flyway | Relational data with real constraints; the schema is versioned and reviewed like code. |
| No caching | Small data, and a cache could show a stale claim status right after a decision. |
| Fake header auth (`X-User-Id` / `X-User-Role`) | Real auth is out of scope; headers keep "who owns which claim" and role checks realistic. |
| Initial reserve capped at the coverage limit | Claimants may estimate more than their cover; that's no reason to reject, but our liability can't exceed it. |
| Reserve hidden from claimants | It's an internal liability figure, not a promise of payment. |
| Exposure never summed across currencies | No FX conversion in scope; a total across currencies would be meaningless. |
| Market time zones for "today" | An incident date is checked against today in the claim's market, not the server's clock. |
| Reactive forms for all claim actions | One consistent, idiomatic Angular pattern (`[formGroup]` + `(ngSubmit)`) for every form. |

## Not built (and why)

| Not built | Why |
|---|---|
| Real authentication / SSO | Out of scope; the header-based identity would be replaced by OAuth2/OIDC tokens. |
| Document uploads | Out of scope (storage, virus scanning, size limits). |
| Payments | Out of scope; "settled" is a status, no money moves. |
| Fraud checks | Out of scope; would need data and rules we don't have. |
| Email delivery | Out of scope; notifications are stored and shown in the app only. |
| Caching | Not needed at this size (see Key decisions). |
| Read/unread notifications | Not requested; the header shows the total count. |
| Reserve history screen | Changes are audited in `reserve_change`, but there is no endpoint or screen for them yet. |

## Known gaps / production next steps

- **Transactional outbox**: the event is sent after commit, so if Kafka fails right after the commit (or the
  process dies) the event is lost and only logged as `EVENT LOST`. An outbox table written in the same transaction,
  plus a relay, fixes this.
- **Dead-letter topic**: an event that keeps failing in a consumer is retried, then logged and skipped.
- **Kafka-down behaviour is not tested** (the 5-second send timeout and the `EVENT LOST` log).
- **Concurrent edits**: the optimistic-lock 409 is implemented but not covered by a test.
- **Reporting backfill**: claims created before the event flow existed are not in the reporting read model.
- **Spring Boot 4 upgrade.**
- **Real identity provider** (OAuth2/OIDC) instead of the demo headers.

## Testing

| Suite | What it covers | Run |
|---|---|---|
| Backend unit tests | Claim rules and lifecycle (`Claim`, `ClaimStatus`), services with mocks, consumer idempotency, stale-version handling, exposure never mixing currencies | part of `.\mvnw.cmd test` |
| Backend integration tests | REST API with real Postgres (access rules, reserve hidden from claimants, validation errors), and the full event flow through real Kafka (notification created, reports match the read model, duplicate delivery ignored) | `.\mvnw.cmd test` (needs `docker compose up -d postgres kafka`) |
| Frontend tests (Vitest) | The header interceptor, the role guard, which staff actions are shown, and that every claim action form actually submits and calls the API | `cd frontend; npm test -- --watch=false` |

Integration tests use their own database (`claims_test`) and Kafka topic (`claims.events.test`, consumer groups
`*.test`), so they never change the demo data or feed events to a running app. The `claims_test` database is
created when the Postgres volume is first created; with an older volume, run `docker compose down -v` once.

A UI bug was found by manual browser testing: several action forms (request information, approve, reject and the
claimant's answer) did nothing when submitted, because no Angular form directive was attached. API-level checks had
missed it. It was fixed, and tests now fill in each form and click its submit button.

## AI usage

This project was built with AI assistance: Claude for planning and design discussions, and Claude Code for
implementation, with the developer making the decisions and reviewing each step. See [AI_JOURNAL.md](AI_JOURNAL.md)
for the details of how it was used.

# Chubb APAC Claims Platform

Claims-processing platform for motor and property claims across six APAC markets (SG, MY, HK, TH, ID, AU).
Claimants report incidents, track their claims, answer information requests and receive decisions.
Claims staff pick up claims, assess them, settle or reject them, and see team workload and liability exposure.

- `backend/` — Spring Boot 3.5, Java 21, Maven (modular monolith)
- `frontend/` — Angular 22 (standalone components, signals, plain SCSS)
- `docker-compose.yml` — PostgreSQL 16 and Kafka (KRaft) for local development

See [ARCHITECTURE.md](ARCHITECTURE.md) for design details.

## Prerequisites

- Java 21
- Docker Desktop
- Node 22.22+, 24.15+ or 26+ (for the frontend; Angular 22 requirement)

## How to run

```powershell
# 1. Start infrastructure (Postgres + Kafka)
docker compose up -d

# 2. Start the backend
cd backend
.\mvnw.cmd spring-boot:run
```

- Health: http://localhost:8080/actuator/health
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs

```powershell
# 3. Start the frontend (in a second terminal)
cd frontend
npm install
npm start
```

- App: http://localhost:4200. `npm start` uses `proxy.conf.json`, so `/api` calls go to the backend on port 8080.
- Tests: `npm test -- --watch=false` (Vitest, headless). Build: `npm run build`.

Stop infrastructure with `docker compose down` (add `-v` to also delete the database volume).

## Demo users

There is no real login. The login page lists the seeded users; picking one sends their id and role in the
`X-User-Id` / `X-User-Role` headers (the same values work in Swagger's **Authorize** dialog).

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

## Key decisions

_TODO_

## Not built (and why)

Out of scope: real auth/SSO, document uploads, payments, fraud checks, email delivery, caching, multi-tenancy,
deployment.

_TODO: reasons for each._

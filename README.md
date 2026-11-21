# Chubb APAC Claims Platform

Claims-processing platform for motor and property claims across six APAC markets (SG, MY, HK, TH, ID, AU).
Claimants report incidents, track their claims, answer information requests and receive decisions.
Claims staff pick up claims, assess them, settle or reject them, and see team workload and liability exposure.

- `backend/` — Spring Boot 3.5, Java 21, Maven (modular monolith)
- `frontend/` — to be added
- `docker-compose.yml` — PostgreSQL 16 and Kafka (KRaft) for local development

See [ARCHITECTURE.md](ARCHITECTURE.md) for design details.

## Prerequisites

- Java 21
- Docker Desktop
- Node 20+ (for the frontend, later)

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

Stop infrastructure with `docker compose down` (add `-v` to also delete the database volume).

## Key decisions

_TODO_

## Not built (and why)

Out of scope: real auth/SSO, document uploads, payments, fraud checks, email delivery, caching, multi-tenancy,
deployment.

_TODO: reasons for each._

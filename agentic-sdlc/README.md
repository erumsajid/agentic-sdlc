# agentic-sdlc

Agentic SDLC orchestrator (Schwab interview assignment). Custom engine, Java 21, Spring Boot, PostgreSQL.

## Prerequisites
- JDK 21, Maven 3.9+
- Local PostgreSQL

## Database setup (once)
```sql
CREATE DATABASE orchestrator;
```
Defaults: `localhost:5432`, user `postgres`, password `postgres`.
Override with env vars `DB_USER` and `DB_PASSWORD`, or edit `orchestrator/src/main/resources/application.yml`.
Flyway creates the tables on first start.

## Run the tests (no database needed)
```bash
mvn test
```

## Start the app
```bash
mvn -pl orchestrator spring-boot:run
```

## Try a run (Phase 2: stub agents)
```bash
# start a run (returns 202 with a runId)
curl -s -X POST localhost:8080/runs \
  -H 'Content-Type: application/json' \
  -d '{"graph":"greenfield-skeleton","requirement":"Build the core URL shortener API"}'

# status of the run and each node
curl -s localhost:8080/runs/<runId>

# full audit trail
curl -s localhost:8080/runs/<runId>/timeline
```

## Current scope
Phase 2 engine: runs graph nodes with persisted state and an audit trail. Independent nodes
whose dependencies have succeeded run concurrently; downstream join nodes wait for every
dependency. The `greenfield-skeleton` graph forks after design into implementation and test
planning, then joins at release readiness.

Not yet implemented: human approval handling, retry/fallback/rollback, resume after restart.

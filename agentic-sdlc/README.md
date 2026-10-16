# agentic-sdlc

Agentic SDLC orchestrator (Schwab interview assignment). Custom engine, Java 21, Spring Boot, PostgreSQL.

## Prerequisites
- JDK 21 (JDK, not only a JRE)
- Apache Maven 3.9+
- PostgreSQL 14+ server
- Postman Desktop or Postman web to import the collections (optional)
- PostgreSQL command-line tools (`psql` and `createdb`) or pgAdmin for database setup

## Setup guide

Follow [`doc/setup/README.md`](doc/setup/README.md) for required software, PostgreSQL database creation, service startup, and Postman collection import steps.

## Run the tests (no database needed)
```bash
mvn test
```

## Start the app
```bash
mvn -pl orchestrator spring-boot:run
```

## URL shortener service (step 6)

The URL shortener runs separately from the orchestrator on port 8081 and uses the same PostgreSQL database by default, with its tables and Flyway history isolated in the `URL_SHORTENER` schema. Flyway creates the schema and its tables on startup; only the `orchestrator` database itself needs to exist first.

```bash
mvn -pl shortener-service spring-boot:run
```

Create a short URL (optional custom alias and expiry):
```bash
curl -i -X POST localhost:8081/api/urls \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/a-long-page","customAlias":"demo-link"}'
```

Visit `http://localhost:8081/demo-link` to redirect. Each successful redirect records a click, timestamp and referrer host (when provided). Read totals, referrers and daily click counts with:
```bash
curl -s localhost:8081/api/urls/demo-link/analytics
```

The service only accepts absolute HTTP/HTTPS target URLs, rejects embedded URL credentials, returns 404 for unknown or expired codes, and returns 409 when an alias is already taken. Configure the DB with `SHORTENER_DB_URL`, `SHORTENER_DB_USER`, and `SHORTENER_DB_PASSWORD`; use `SHORTENER_PORT` and `SHORTENER_PUBLIC_BASE_URL` when deploying elsewhere.

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
Workflow engine: persists node state and an audit trail; runs independent ready nodes
concurrently and waits for all dependencies at joins. The `greenfield-skeleton` graph pauses
at the design approval checkpoint, then forks into implementation and test planning before
joining at release readiness.

## Governance and replanning

Approve or reject a pending checkpoint (approval also resumes the run):
```bash
curl -s -X POST localhost:8080/runs/<runId>/nodes/DESIGN/approval \
  -H 'Content-Type: application/json' \
  -d '{"approved":true,"actor":"reviewer","reason":"Design reviewed"}'
```

The run timeline records approval decisions, retries, rollback, safe-stop and replanning.
Retryable agent and exit-gate failures retry up to the node's `maxRetries`; rejected/failed/blocked nodes
can also be retried explicitly at `/runs/<runId>/nodes/<nodeId>/retry`. Safe-stop a running
or approval-waiting run with `POST /runs/<runId>/safe-stop`.

Artifacts are immutable versions with an `active` version and input lineage. Inspect them at
`GET /runs/<runId>/artifacts`. Rollback an output with
`POST /runs/<runId>/nodes/<nodeId>/rollback`; this deactivates the node's current artifacts,
restores prior versions when available, and marks succeeded descendants stale. Re-run an
updated node and its downstream dependents with
`POST /runs/<runId>/nodes/<nodeId>/replan`.

## Metrics

After restarting the app, Prometheus-format metrics are available at
`http://localhost:8080/actuator/prometheus`. The main workflow metrics are:

- `orchestrator_runs_started_total` and `orchestrator_runs_finished_total` by graph and outcome
- `orchestrator_run_duration_seconds` by graph and outcome
- `orchestrator_node_attempts_total` and `orchestrator_node_attempt_duration_seconds` by node, agent and outcome
- `orchestrator_node_transitions_total` by node and resulting state
- `orchestrator_gates_evaluated_total` by gate type and outcome
- `orchestrator_retries_total`, `orchestrator_approvals_total`, `orchestrator_rollbacks_total`,
  `orchestrator_safe_stops_total` and `orchestrator_replans_total`
- `orchestrator_fork_batch_size` and `orchestrator_artifacts_created_total`

The endpoint also exposes the standard `health` and `info` Actuator endpoints. Metrics use
graph/node/state labels and omit run IDs and requirement text.

## LLM-backed agents (step 7)

The orchestrator currently uses deterministic stub agents; LLM mode is explicitly disabled in `orchestrator/src/main/resources/application.yml`. To enable an OpenAI Chat Completions-compatible provider later, change `agent.llm.enabled` to `true`, set `LLM_MODEL` and `LLM_BASE_URL`, and set `LLM_API_KEY` for providers that require a key, then restart the orchestrator. For example, PowerShell:

```powershell
$env:LLM_BASE_URL = "https://api.openai.com/v1"
$env:LLM_MODEL = "gpt-4o-mini"
$env:LLM_API_KEY = "<your-key>"
mvn -pl orchestrator spring-boot:run
```

The LLM agent takes precedence over the stub and returns structured JSON artifacts for each graph role. It has no tool execution or repository write access. Credential-like requirements or input artifacts are blocked before they are sent to the provider. Provider failures use the workflow's bounded retry behavior; they do not silently fall back to stub output. Only send workflow content to a provider approved for that data. Keep API keys in environment variables, not source files or requirements.

## Scenarios, tests, and documentation (step 8)

Three workflow graphs are available: `greenfield-skeleton`, `brownfield-modernization`, and `ambiguous-requirement`. Run tests from the repository root with `mvn test` (no database required for unit tests). The architecture documentation set is in [`doc/architecture/`](doc/architecture/), including the [full overview](doc/architecture/architecture-overview.md), [sequence diagrams](doc/architecture/sequence-diagrams.md), [decision tables](doc/architecture/decision-table.md), and [scenario/setup/validation guide](doc/architecture/architecture-and-scenarios.md).

Importable Postman collections are in [`doc/`](doc/): the workflow verification collection for steps 1-5 uses `http://localhost:8080`, and [`doc/url-shortener.postman_collection.json`](doc/url-shortener.postman_collection.json) tests the shortener at `http://localhost:8081`.

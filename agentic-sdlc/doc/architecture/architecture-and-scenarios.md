# Architecture, scenarios, and validation

## Architecture overview

The repository contains two Spring Boot services in one Maven build:

- **Orchestrator** (`orchestrator`, port 8080) owns workflow graphs, runs, node execution, approval decisions, audit events, artifacts, lineage, and workflow metrics.
- **URL shortener** (`shortener-service`, port 8081) owns URL creation, redirects, expiry, and click analytics. It stores its tables and Flyway history in the PostgreSQL `URL_SHORTENER` schema.

The orchestrator loads every `graphs/*.yaml` definition at startup. Each run pins the selected graph name and version, persists node states and audit events, and dispatches nodes whose dependencies have succeeded. Outputs are immutable versioned artifacts; their lineage identifies the exact input artifact IDs, versions, and hashes. Gates run before or after agent work. A human-approval gate holds outputs until approval, then the engine resumes eligible nodes. Independent nodes run concurrently and join only after every declared dependency succeeds.

Agents implement the `Agent` interface. Deterministic stubs are the default. An optional OpenAI Chat Completions-compatible agent can be enabled in configuration; it returns schema-constrained artifact content and has no tool or repository access. Provider calls use bounded timeouts and workflow retries. Credential-pattern input is blocked before it is sent. Workflow action and duration metrics are exported at `/actuator/prometheus`.

The shortener API is independent of the workflow engine. It validates HTTP/HTTPS targets, stores unique codes, returns temporary redirects, records click timestamps and referrer hosts, and exposes aggregated click data.

## The three scenarios

### Greenfield: `greenfield-skeleton`

Use when there is no existing system to preserve. Requirements lead to an approved design, then implementation and test planning run in parallel and join at release readiness.

```bash
curl -s -X POST localhost:8080/runs -H 'Content-Type: application/json' \
  -d '{"graph":"greenfield-skeleton","requirement":"Build a URL shortener with expiring links and click analytics"}'
```

Use the returned `runId` to poll `GET /runs/{runId}`. When `DESIGN` reaches `AWAITING_APPROVAL`, approve it and then inspect the timeline and artifacts:

```bash
curl -s -X POST localhost:8080/runs/{runId}/nodes/DESIGN/approval \
  -H 'Content-Type: application/json' \
  -d '{"approved":true,"actor":"reviewer","reason":"Greenfield design reviewed"}'
curl -s localhost:8080/runs/{runId}/timeline
curl -s localhost:8080/runs/{runId}/artifacts
```

### Brownfield: `brownfield-modernization`

Use when an existing service, deployment, or data model constrains the change. The graph starts with discovery and a component map, produces an impact analysis, and requires design approval. Implementation, regression-test planning, and security review then run as parallel branches; release readiness joins all three.

```bash
curl -s -X POST localhost:8080/runs -H 'Content-Type: application/json' \
  -d '{"graph":"brownfield-modernization","requirement":"Add expiring short links to our existing API while preserving current redirects and database compatibility"}'
```

For meaningful brownfield results, include the existing architecture, interfaces, migrations, and compatibility constraints in the requirement or connected input artifacts. The current prototype has no repository-inspection tool, so the discovery agent only knows context supplied to the run.

### Ambiguous requirement: `ambiguous-requirement`

Use when important scope choices are missing. The planner produces a clarification brief and proposed assumptions. The run pauses for a human to accept or reject those assumptions before requirements work continues. Design also has its own approval checkpoint. Implementation and test planning then fork and join.

```bash
curl -s -X POST localhost:8080/runs -H 'Content-Type: application/json' \
  -d '{"graph":"ambiguous-requirement","requirement":"Make sharing links easier and improve the experience"}'
```

Poll `GET /runs/{runId}` until the status is `WAITING_APPROVAL`, then read `GET /runs/{runId}/artifacts`. Review the `CLARIFICATION_BRIEF` and `ASSUMPTIONS` before approving the first checkpoint:

```bash
curl -s -X POST localhost:8080/runs/{runId}/nodes/CLARIFY_SCOPE/approval \
  -H 'Content-Type: application/json' \
  -d '{"approved":true,"actor":"reviewer","reason":"Assumptions reviewed"}'
```

The run later pauses at `DESIGN`; review and approve that checkpoint using the greenfield approval request above. Rejecting either checkpoint blocks the run; it does not collect a free-form answer from the requester. Start a revised run with clarified requirements when the proposed assumptions are wrong.

## Setup and operation

Prerequisites: JDK 21, Maven 3.9+, and PostgreSQL. Create the database used by the application, then configure the credentials in `orchestrator/src/main/resources/application.yml` (defaults in the current local configuration are user `postgres`, password `root`). For the shortener, create its isolated schema:

```sql
CREATE SCHEMA URL_SHORTENER;
```

Start each service in a separate terminal:

```bash
mvn -pl orchestrator spring-boot:run
mvn -pl shortener-service spring-boot:run
```

Run unit tests from the repository root:

```bash
mvn test
```

Tests do not require a running PostgreSQL instance. The workflow scenario tests load and validate all three graph definitions, and shortener service tests mock persistence. The importable Postman collections are in `doc/`.

## Key risks and tradeoffs

| Risk or tradeoff | Current handling | Remaining limitation |
| --- | --- | --- |
| A model may produce incorrect or fabricated artifacts | Structured output, artifact gates, human design approvals, explicit instruction not to claim unperformed checks | Model output still needs reviewer scrutiny; the stub path is deterministic but not substantive |
| Workflow content may be sensitive | Credential-pattern inputs are blocked before LLM submission; API keys come from environment configuration | Pattern checks do not detect every form of sensitive data; do not enable an unapproved provider for confidential content |
| A brownfield discovery can be incomplete | The graph records current-state and impact artifacts before approval | There is no repository or infrastructure inspection integration in this prototype |
| Ambiguity can be mistaken for an assumption | The graph pauses for human approval of proposed assumptions | Approval is binary and does not collect clarification text; revised runs are needed for changed scope |
| Database outage interrupts both services | PostgreSQL persists workflow state, audit and artifacts; shortener links and clicks | No high-availability database, backup automation, or disaster-recovery workflow is configured |
| Analytics storage grows with every click | Click rows have URL/time indexes and can be aggregated by day/referrer | Retention, partitioning, bot filtering, and privacy controls need production policy decisions |
| Redirect requests write synchronously | Click event and count update occur in the request transaction | This favors simple consistency over high-throughput asynchronous analytics |
| LLM provider errors delay or fail a node | Timeouts and bounded retries; no silent stub fallback | No circuit breaker, provider failover, or token/cost budget is implemented |

## Validation status

The scenario and shortener unit tests are included in the Maven build. They were not executable in the implementation environment because Maven was unavailable (`mvn` was not recognized). Run `mvn test` from the repository root before relying on this validation. Database-backed service startup and live Postman requests also require a local PostgreSQL instance and have not been confirmed here.

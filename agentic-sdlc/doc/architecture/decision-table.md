# Decision Tables

## Scenario and graph selection

| Situation | Graph | First control point | Parallel work | Completion condition |
| --- | --- | --- | --- | --- |
| New capability with no legacy constraints | `greenfield-skeleton` | Design approval | Implementation + test planning | Release readiness succeeds after both branches |
| Change to an existing service or data model | `brownfield-modernization` | Discovery and impact analysis, then design approval | Implementation + regression planning + security review | Release readiness succeeds after all three branches |
| Request leaves material scope choices unresolved | `ambiguous-requirement` | Human approval of clarification brief and assumptions | Implementation + test planning after approved requirements/design | Release readiness succeeds after both branches |
| Proposed assumptions are wrong | Any applicable scenario, usually `ambiguous-requirement` | Reject approval or safe-stop | None until scope is revised | Start a new run with clarified requirement; rejected run remains auditable |

## Gate outcomes

| Gate result | Node behavior | Artifact behavior | Run-level effect |
| --- | --- | --- | --- |
| Entry gate PASS/WARN | Agent may run | No outputs yet | Continue |
| Entry gate BLOCK/FAIL | Node becomes blocked/failed before agent execution | No outputs published | Other independent eligible work may finish; run ends BLOCKED/FAILED as applicable |
| Exit gate PASS/WARN | Node may succeed | Declared outputs are published as immutable artifact versions | Dependencies can become eligible |
| Exit gate HUMAN_APPROVAL | Node becomes `AWAITING_APPROVAL` | Outputs are held as pending and are not active artifacts | Run becomes `WAITING_APPROVAL`; downstream nodes remain pending |
| Approval accepted | Waiting node succeeds | Pending outputs are published with hashes and lineage | Engine resumes ready nodes |
| Approval rejected | Node becomes `REJECTED` | Pending outputs are discarded | Run becomes blocked until explicit retry/replan or a new run |
| Exit gate BLOCK/FAIL | Node becomes blocked/failed | Outputs are not published | Retry may occur for retryable failure; policy block requires correction |
| Unknown policy/schema/quality rule | Fail closed | Outputs are not published | Node cannot pass until graph/rule is corrected |

## Retry and operator controls

| Event / precondition | Decision | State/data effect | Audit and metric effect |
| --- | --- | --- | --- |
| Retryable agent or exit-gate failure and attempts remain | Retry automatically | Attempt increments; node returns to RUNNING | Retry reason, node attempt outcome and duration recorded |
| Retry budget exhausted | Stop automatic attempts | Node remains failed; no invalid output is published | Failed outcome recorded |
| Explicit retry on failed, rejected, blocked, or safe-stopped node | Accept if transition/state checks pass | Reset eligible node and resume execution | Actor/reason and retry counter recorded |
| Safe-stop while RUNNING or WAITING_APPROVAL | Accept | Stop flag set; pending/ready/approval-waiting nodes stop; already in-flight call may finish before engine notices | Actor/reason and safe-stop counter recorded |
| Rollback a SUCCEEDED node in a terminal run | Accept | Current outputs deactivated; previous artifact versions restored when available; successful descendants marked stale | Rollback audit event and counter recorded |
| Replan a SUCCEEDED or ROLLED_BACK node in a terminal run | Accept if affected nodes are in valid states | Target and downstream nodes reset in topological order and executed again | Replan actor/reason and counter recorded |
| Rollback/replan while run is active or awaiting approval | Reject | No state mutation | Conflict response; no successful control event |

## Agent selection and LLM data handling

| Configuration / condition | Selected agent | External data transfer | Failure behavior |
| --- | --- | --- | --- |
| `agent.llm.enabled: false` (current setting) | `StubAgent` | None | Deterministic stub artifacts proceed through gates |
| LLM enabled and provider configured | `OpenAiCompatibleLlmAgent` takes precedence | Requirement plus declared input artifacts are sent to configured provider | HTTP/timeout/shape failures surface as agent failures and use node retry budget; no silent stub fallback |
| Credential pattern found in requirement or inputs | LLM request is blocked | No prompt is sent | Agent failure; current implementation may consume retry attempts |
| LLM returns malformed JSON, extra/missing keys, or blank artifact | Reject output | Response is not persisted as a workflow artifact | Agent failure and bounded retry |
| LLM-generated content fails an exit gate or policy | Reject output | Failed content is not published as active artifact | Gate outcome is audited; retry only where configured |

## URL shortener API decisions

| Request condition | Response | Persistence effect |
| --- | --- | --- |
| Valid absolute HTTP/HTTPS URL, free generated/custom code, future or absent expiry | `201 Created` and `Location` | Create link in `URL_SHORTENER.short_url` |
| Invalid/missing target, unsupported scheme, embedded credentials, malformed alias, or non-future expiry | `400 Bad Request` | No link created |
| Custom alias already used, including concurrent uniqueness conflict | `409 Conflict` | Existing link unchanged |
| GET existing unexpired code | `302 Found` with target in `Location` | Atomically increment counter and insert click event |
| GET unknown or expired code | `404 Not Found` | No click recorded |
| Analytics for existing code | `200 OK` with totals, referrer hosts, daily counts | Read-only aggregation |
| Analytics for unknown code | `404 Not Found` | No change |

## Deliberate prototype tradeoffs

| Decision | Why this prototype chose it | Revisit when |
| --- | --- | --- |
| Synchronous click writes | Keeps redirect and analytics count straightforward and transactional | Redirect volume makes database latency a bottleneck |
| PostgreSQL for workflow and shortener | Familiar durable store with Flyway migrations; shortener uses its own schema | Services need independent scaling, ownership, or availability targets |
| Binary approval endpoints | Auditable approve/reject with small API surface | Review needs revisions, comments, multi-party quorum, or delegated ownership |
| Stub agents by default | Local workflows can run without credentials, provider costs, or external data transfer | Provider approval, secrets management, and operational budgets are configured |
| Pattern-based secret screening | Prevents common credential-shaped prompts from leaving the process | Broader classification, tenant controls, or regulated data require stronger DLP controls |

# PRD: Clearfolio Viewer Unified Document Preview (Internal)

Date: 2026-02-23
Last updated: 2026-02-23
Owner: Product Manager
Sources: `docs/architecture.md`, `docs/trd-integrated-document-viewer-platform.md`, `docs/prd-integrated-document-viewer-platform.md`, `docs/engineering/acceptance-criteria.md`, `docs/workflow/one-day-delivery-plan.md`, `docs/diagrams/*`, `AGENTS.md`

## 1) Problem / Context

- Internal document preview is fragmented across file types and client surfaces (desktop browser, Power Platform embedded views, mobile/tablet webviews).
- Existing flows frequently rely on synchronous work or inconsistent viewer entrypoints, increasing user wait time and operational risk.
- Security controls for risky file types exist conceptually, but enforcement, exceptions, and auditability must be standardized for a unified viewer.

## 2) Goals

- Provide a single, predictable entrypoint for preview: a stable viewer URL that works across desktop/tablet/phone.
- Use the documented delivery chain so downstream platforms can embed reliably:
  - `existing WAS -> preview service -> gateway -> Power Platform -> mobile/tablet`
- Keep request handling non-blocking (reactive runtime preferred) and keep conversion/preview preparation out of the request thread.
- Establish a minimal but production-safe security posture: S2S authentication, a clear token model, auditable access, and browser-level controls (CSP).
- Meet mandatory engineering acceptance criteria gates (coverage/docstrings/warnings/deprecations) and support a one-day delivery motion with security validation.

## 3) Non-Goals (MVP)

- Document editing, annotation, comments, or collaboration.
- OCR, translation, or content extraction APIs.
- Real-time push updates (WebSocket/SSE) for job status; polling is sufficient for MVP.
- Full enterprise RBAC matrix beyond the minimum needed for secure S2S + user-context propagation.
- Durable, cross-region object storage and retention automation (allowed later).

## 4) Users / Stakeholders

- End user (employee): opens a document from the existing WAS and expects a fast, consistent preview.
- Mobile/tablet user: opens the same document inside Power Platform (embedded webview) with touch-friendly UX.
- Platform owner (Power Platform team): needs stable URLs, predictable auth, and low-support integration.
- Security reviewer: needs enforceable policies (blocked types), audit logging, token hygiene, and web security headers.
- Operator/SRE: needs visibility into queue pressure, retries/DLQ, errors, and SLOs.

## 5) Scope

### 5.1 MVP capabilities

- Unified preview entrypoint: `GET /viewer/{docId}` (and compatibility aliases if required by integration constraints).
- Async conversion/preview preparation with explicit job lifecycle (`SUBMITTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`) and a polling status API.
- Lightweight event queue (bounded) to offload conversion work from the request path.
- Policy enforcement for risky formats (block-by-default for configured extensions) with an auditable exception lane.
- Viewer bootstrap response contains deterministic adapter metadata (`sourceExtension`, `rendererAdapter`) to select the correct downstream renderer.
- Mobile/tablet-safe viewer shell behavior: no horizontal scroll on phone, explicit loading/empty/error states.

### 5.2 Supported file types

The product goal is “unified preview,” but MVP must start with a safe, supportable subset.

MVP (must):

- `pdf`
- Images: `png`, `jpg`/`jpeg`
- Plain text: `txt`

MVP (conditional / behind converter availability):

- Office formats: `docx`, `pptx`, `xlsx` (preview via conversion to a safe render target such as PDF)

Blocked by default (MVP):

- `hwp`, `hwpx` (policy exception lane only; see Security)

Later (examples; not exhaustive):

- `doc`, `ppt`, `xls`
- `html` (only if sanitized and rendered in a safe sandbox)
- Archives (`zip`) (only as a container, not rendered directly)
- Media types (`mp4`, `mp3`) (preview as player, not “document”)
- CAD/engineering formats (requires separate renderer strategy)

### 5.3 Integration boundaries (MVP)

- Existing WAS: remains the primary system of record for user intent and document selection.
- Preview service (Clearfolio Viewer): owns the preview lifecycle, viewer URL, and security controls for preview.
- Gateway: Azure On-premise Gateway is treated as a constrained networking boundary (latency/headers/protocol constraints must be assumed).
- Power Platform: embeds viewer URL in mobile/tablet experience; must support touch-first navigation.

## 6) User Journeys

### 6.1 Desktop (browser; launched from existing WAS)

1. User clicks “Preview” on a document in the existing WAS.
2. WAS calls preview service to create/lookup a preview job and returns a viewer URL.
3. Browser opens viewer URL.
4. Viewer shows:
   - Loading state while job is `SUBMITTED`/`PROCESSING`.
   - Preview when `SUCCEEDED`.
   - Clear failure state when `FAILED` (with a safe retry path if allowed).
   - Policy block messaging for blocked formats (with guidance, not raw policy tokens).

### 6.2 Tablet (Power Platform embedded)

1. User opens a record in Power Platform.
2. Embedded preview component launches viewer URL through gateway.
3. Viewer renders with tablet breakpoints (768–1024px), touch-first controls, and minimal chrome.
4. Viewer consistently handles loading/error/empty states without “system popup” UI.

### 6.3 Phone (Power Platform embedded)

1. User taps “Preview” from a phone form.
2. Viewer opens in an embedded webview.
3. Phone-specific UX constraints:
   - No horizontal scroll (<768px).
   - Single-column layout; controls are large enough for touch.
   - Clear back navigation to Power Platform context.

## 7) UX Consistency Requirements

- Viewer layout and states are consistent across entrypoints (desktop/tablet/phone): loading, empty/no-access, blocked-by-policy, conversion-failed, and success.
- UI/UX Standard (Ver.3.0, 2022-11-21) applies for web-based internal systems (checklist: `docs/ui/ux-ver3-checklist.md`):
  - Breakpoints: PC (>=1024), Tablet (768–1024), Phone (<768).
  - No horizontal scroll at recommended resolutions.
  - Required fields (when present) marked with `*`.
  - Buttons use consistent verb labels and ordering.
  - Base color token: Hyosung Blue `#034ea2` (unless a different brand context is explicitly required).

## 8) Functional Requirements

- FR-01 Viewer entrypoint: `GET /viewer/{docId}` is canonical and stable.
- FR-02 Viewer bootstrap contract: when `SUCCEEDED`, return a bootstrap payload with deterministic `sourceExtension` and `rendererAdapter` for downstream render selection.
- FR-03 Status polling: provide a status endpoint for `docId`/`jobId` with lifecycle state and retry metadata.
- FR-04 Non-blocking submission: submission endpoints return quickly (202) and never run conversion inline.
- FR-05 Idempotency/dedupe: duplicate submissions produce stable job identity (or stable mapping) rather than duplicate expensive work.
- FR-06 Retry/DLQ: background failures follow a bounded retry policy and produce a dead-lettered terminal state; operator-triggered retry exists for dead-lettered jobs only.
- FR-07 Policy enforcement:
  - Normal lane: supported formats proceed.
  - Blocked lane: blocked extensions fail fast with a structured response.
  - Exception lane: blocked-format processing allowed only with explicit approval metadata; all exceptions are auditable.

### 8.1 API surface (MVP-level contract)

The exact route names can vary by integration constraints, but the following semantics must exist:

- Submit/create preview job: returns `202 Accepted` with `jobId` and a `statusUrl`.
- Poll status: returns lifecycle state + retry/dead-letter metadata.
- Viewer entrypoint: returns either a ready bootstrap payload (success) or a clear non-ready/failed/policy response without leaking intermediate artifacts.

Viewer embedding requirement:

- The viewer URL must be embeddable by Power Platform (subject to CSP `frame-ancestors` allowlist).

## 9) Non-Functional Requirements

### 9.1 Performance / SLO targets

- SLO (service): 99.9% monthly availability for viewer entrypoint and status APIs.
- Viewer entry latency:
  - `GET /viewer/{docId}` P95 <= 250ms for non-terminal (not-ready) responses.
  - `GET /viewer/{docId}` P95 <= 500ms for ready bootstrap responses (excluding downstream renderer fetch).
- Submission latency:
  - `POST submit` P95 <= 250ms (excluding raw upload I/O time).
- Preview readiness (MVP target; format-dependent):
  - P50 submit -> first preview frame <= 3s for `pdf`.
  - P95 submit -> status-visible <= 8s.

### 9.2 Non-blocking web runtime

- Use a non-blocking runtime for web request handling (reactive preferred; avoid Servlet/MVC for the preview service).
- Any blocking operations (file I/O, conversion runtime, network calls) are isolated in worker/executor contexts.

### 9.3 Queue/worker behavior

- Lightweight event queue is bounded to protect the service under load.
- Queue work is asynchronous; request/response flows must not wait for job completion.
- Retry policy is explicit (attempt limit + backoff) and supports dead-lettering.

## 10) Security & Privacy Requirements

### 10.1 Authentication / Authorization (S2S + user context)

- S2S authentication is mandatory for calls from existing WAS to preview service.
- Token model (MVP):
  - A short-lived service-to-service token (e.g., signed JWT) identifies the caller (WAS) and allowed scopes.
  - A user-context claim set is propagated to bind preview access to an end user (or a delegated “service user”) for audit.
  - Viewer URL access is authorized by a short-lived viewer session token or equivalent capability token.

Minimum claims/scopes (MVP intent):

- Caller identity: `iss`/`sub` (service id), environment, and audience bound to preview service.
- User context: stable user identifier and tenant/org context (no raw PII required).
- Scope: `preview:read` and `preview:create` (and `preview:override_policy` only for exception lane).

### 10.2 Token hygiene

- Tokens are never written to logs in raw form.
- When an approval token is required (exception lane), logs store only a fingerprint (hash) sufficient for audit correlation.

### 10.3 Audit logging

- Emit structured audit events for:
  - preview session creation
  - viewer access (success/fail)
  - blocked-format attempts
  - exception lane approvals (including approver id, token fingerprint, and rationale id if available)
  - operator-triggered retries

### 10.4 Browser security headers / CSP

- Viewer responses enforce:
  - Content Security Policy (CSP) with an allowlist approach.
  - `X-Content-Type-Options: nosniff`.
  - `Referrer-Policy` set to a restrictive value.
  - `frame-ancestors` policy compatible with Power Platform embedding (explicitly allow only known embed origins).

CSP baseline (MVP direction, to be finalized during implementation):

- `default-src 'none'`
- `script-src 'self'` (add hashes/nonces if inline scripts are required)
- `style-src 'self'` (avoid `unsafe-inline` unless required by the viewer renderer)
- `img-src 'self' blob: data:` (only if the renderer requires)
- `connect-src 'self'`
- `object-src 'none'`

### 10.5 Data handling and retention (MVP)

- Store only the minimum metadata required for preview lifecycle and auditing.
- Any persisted strings are sanitized at the persistence boundary to avoid DB/runtime errors (including removal of NUL `\u0000` characters).
- Define retention defaults for preview artifacts and logs (short-lived by default), with an explicit later policy for durable retention.

Privacy stance (MVP):

- Treat document content as sensitive; do not log document content or filenames unless explicitly required for operations.
- Encrypt in transit for all hops; treat gateway traversal as untrusted transport.
- Prefer encryption at rest for any persisted preview artifacts when durable storage is introduced.

## 11) Operational Requirements

- Health endpoints for readiness/liveness.
- Observability baseline:
  - Metrics: request rates/latency, queue depth, worker lag, retry counts, dead-lettered counts.
  - Tracing: correlation/trace id on API responses and logs.
  - Logs: structured JSON with redaction rules.
- Rate limiting/backpressure:
  - Protect submission and viewer endpoints from overload.
  - Prefer bounded queues and fast failover to clear status responses.
- Runbooks:
  - DLQ triage and replay
  - blocked-format exception investigation
  - incident response for viewer unavailability

Release hygiene requirements:

- Configuration is externalized (no secrets in repo); environment variables or secret manager integration used for tokens/keys.
- Key rotation for signing/verification material is supported (at minimum: overlap window for old/new keys).

## 12) Acceptance Criteria (Mandatory)

This PRD inherits the exact mandatory acceptance criteria list and must remain aligned to the canonical policy:

Reference: `docs/engineering/acceptance-criteria.md`.

### Mandatory AC list (exact)

1. coverage
2. docstring
3. non-blocking web
4. lightweight queue
5. warning 0
6. deprecated 0
7. 1-day schedule+security verification

### Quality gates (merge gates)

- `mvn -DskipTests compile` passes with warning/deprecated budget = 0.
- `mvn test` passes.
- JaCoCo coverage remains 100% line/branch for production package.
- JavaDoc gate passes with no warnings/errors (`mvn -q -DskipTests javadoc:javadoc`).
- Markdown lint passes for changed docs.
- Security validation evidence is captured (SAST/code-scanning checks) for one-day delivery runs.

## 13) One-Day Delivery Plan (<=24h)

The one-day delivery motion is the default execution path for this internal capability.

- Workflow: `docs/workflow/one-day-delivery-plan.md`
- Detailed schedule and commands: `docs/plans/2026-02-23-clearfolio-viewer-mvp-1day-delivery-plan.md`

Minimum one-day deliverables:

- Passing merge gates (compile/test/javadoc/coverage).
- Security validation evidence captured (SAST + code scanning).
- A stable viewer entrypoint and predictable states (loading/error/success) across desktop/tablet/phone.

## 14) Success Metrics

- Preview entry reliability: >= 99% viewer bootstrap success for ready documents.
- Reduced wait: >= 80% of “Preview” clicks return a usable loading UI within 1s.
- Supported-format success rate: >= 95% (format-specific rollups tracked).
- Operational clarity: DLQ spikes are detected and alerted within 10 minutes.

## Decisions / Assumptions

- Decision: Viewer entrypoint is canonical and stable (`/viewer/{docId}`), enabling Power Platform embedding.
- Assumption: Existing WAS remains the authority for user entitlement decisions; preview service enforces session/token constraints and does not become the system of record.
- Assumption: MVP uses a lightweight bounded queue/executor; durable queue substitution is a planned hardening step.
- Assumption: `hwp`/`hwpx` are blocked by default and require an auditable exception lane.

## Unknowns / Risks

- Risk: Power Platform embedding constraints (origins/iframes/webview behavior) may require CSP/frame-ancestor adjustments and a documented allowlist rollout process.
- Risk: Office formats (`docx`/`pptx`/`xlsx`) preview quality depends on converter availability; failures could impact perceived “unified” promise if not clearly messaged.
- Risk: Gateway-induced header/proxy limitations can constrain token propagation; mitigation is short-lived viewer session tokens and minimized header set.
- Risk: Strict no-warnings/no-deprecations gates can slow dependency upgrades; mitigate with explicit upgrade windows and pre-merge checks.
- Risk: Exception lane governance (who can approve, how approvals are issued) can expand scope; mitigate by treating policy token issuance as external and logging only fingerprint + approver id.

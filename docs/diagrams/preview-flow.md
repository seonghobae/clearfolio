# Preview Flow UML (component + sequence)

This document tracks the `GET /viewer/{docId}` contract currently implemented in the backend (with aliases `/api/v1/viewer/{docId}` and `/api/v1/convert/viewer/{docId}`) and the planned S2S orchestration extension.

S2S chain (delivery context): `Clearfolio Viewer <-> internal WAS -> Azure On-premise Gateway -> Power Platform -> mobile/tablet`.
Current implementation in this repo covers the viewer API/state gate; downstream internal WAS and gateway integration are planned.

## Component diagram

```mermaid
flowchart LR
  Mobile[Mobile/Tablet Client]
  PP[Power Platform]
  Gateway[Azure On-premise Gateway]
  Was[internal WAS]
  Viewer[Clearfolio Viewer API\n/viewer/{docId} + aliases]
  Controller[ConversionController]
  Service[DocumentConversionService]
  Repo[InMemoryConversionJobRepository]
  PreviewCtl[PreviewOrchestrator in internal WAS (planned)]
  PreviewService[Preview Service / S2S API (planned)]
  Artifact[(Artifact Store / path for output)]
  AuthN[(AuthN/AuthZ + audit + trace)]

  Mobile --> PP
  PP --> Gateway
  Gateway --> Was
  Was <--> Viewer
  Viewer --> Controller
  Viewer --> AuthN
  Controller --> Service
  Service --> Repo
  Was --> PreviewCtl
  Repo --> Was
  PreviewCtl --> PreviewService
  PreviewService --> Artifact

  classDef planned stroke-dasharray: 4 4,stroke:#808080,color:#808080;
  class PP,Gateway,Was,PreviewCtl,PreviewService,AuthN planned;
```

## Sequence diagram

```mermaid
sequenceDiagram
  autonumber
  participant M as Mobile/Tablet
  participant PP as Power Platform
  participant G as Azure On-premise Gateway
  participant W as internal WAS
  participant V as Clearfolio Viewer (/viewer/{docId})
  participant Ctl as ConversionController
  participant Svc as DocumentConversionService
  participant Repo as InMemoryConversionJobRepository
  participant O as PreviewOrchestrator in WAS (planned)
  participant PS as Preview Service (planned)
  participant EH as ApiExceptionHandler

  Note over PP,G: External hops shown for integration context; Clearfolio-side API behavior is the implemented scope in this repo.

  M->>PP: open preview(docId)
  PP->>G: forward preview request
  G->>W: S2S request
  W->>V: GET /viewer/{docId}
  Note over V: Alias routes: `/api/v1/viewer/{docId}`, `/api/v1/convert/viewer/{docId}`
  V->>Ctl: GET /viewer/{docId}
  Ctl->>Svc: getJob(docId)
  Svc->>Repo: findById(docId)

  alt job not found
    Repo-->>Ctl: empty
    Ctl-->>EH: ResponseStatusException(404)
    EH-->>W: 404 {errorCode: NOT_FOUND, code: NOT_FOUND, message, details, traceId}

  else job found
    Repo-->>Svc: ConversionJob
    Svc-->>Ctl: job snapshot

    alt job.status = FAILED
      Ctl-->>W: 409 + ApiErrorResponse { errorCode: CONFLICT, code: CONFLICT, message, details, traceId }

    else job.status in SUBMITTED or PROCESSING
      Ctl-->>W: 409 + retry guidance

    else job.status = SUCCEEDED
      Ctl-->>W: 200 with viewer bootstrap payload from ConversionJob
      note right of W: planned: prepareViewerSession(docId, outputPath)

      opt planned: preview service session path
        W->>O: prepareViewerSession(docId, outputPath)
        O->>PS: createViewerSession(docId)

        alt preview service returns session
          PS-->>O: token + expiresAt + sessionMetadata
          O-->>M: 200 with token and viewer bootstrap payload

        else preview service timeout/500
          O-->>M: 503 + ApiErrorResponse { errorCode, code, message, details, traceId }

        else unauthorized at preview service
          O-->>M: 401 + ApiErrorResponse { errorCode, code, message, details, traceId } with retryability hint
        end
      end
    end
  end
```

## Exception paths covered

- Missing job and conversion record (404).
- Job not ready for bootstrap (SUBMITTED/PROCESSING) or failed conversion result (FAILED).
- Failed conversion with explicit reason; retry-exhausted failures remain `FAILED` with `deadLettered=true`.
- Preview service timeout or authorization failure with traceable error path (planned extension).
- HWP/HWPX and manual policy path: preview path only allowed after successful exception policy resolution (planned extension).

## File-level evidence pointers

- Viewer route and state-gated contract: `src/main/java/com/clearfolio/viewer/controller/ConversionController.java`
- Job lifecycle fields used by preview response: `src/main/java/com/clearfolio/viewer/model/ConversionJob.java`
- Current evidence snapshot index: `docs/qa/evidence/LATEST.md`

# Preview Flow UML (component + sequence)

This document tracks the `GET /viewer/{docId}` contract currently implemented in the backend (with aliases `/api/v1/viewer/{docId}` and `/api/v1/convert/viewer/{docId}`) and the planned S2S orchestration extension.

## Component diagram

```mermaid
flowchart LR
  Client[Client / Viewer Shell]
  Viewer[Viewer API / /viewer/{docId}, /api/v1/viewer/{docId}, /api/v1/convert/viewer/{docId}]
  Controller[ConversionController]
  Service[DocumentConversionService]
  Repo[InMemoryConversionJobRepository]
  PreviewCtl[PreviewOrchestrator (planned)]
  PreviewService[Preview Service / S2S API (planned)]
  Artifact[(Artifact Store / path for output)]
  AuthN[(AuthN/AuthZ + audit + trace)]

  Client --> Viewer
  Viewer --> Controller
  Viewer --> AuthN
  Controller --> Service
  Service --> Repo
  Repo --> PreviewCtl
  PreviewCtl --> PreviewService
  PreviewService --> Artifact

  classDef planned stroke-dasharray: 4 4,stroke:#808080,color:#808080;
  class PreviewCtl,PreviewService,AuthN planned;
```

## Sequence diagram

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant V as ViewerShell (/viewer/{docId})
  participant Ctl as ConversionController
  participant Svc as DocumentConversionService
  participant Repo as InMemoryConversionJobRepository
  participant O as PreviewOrchestrator (planned)
  participant PS as Preview Service (planned)
  participant EH as ApiExceptionHandler

  C->>V: GET /viewer/{docId}
  Note over V: Alias routes: `/api/v1/viewer/{docId}`, `/api/v1/convert/viewer/{docId}`
   V->>Ctl: GET /viewer/{docId}
  Ctl->>Svc: getJob(docId)
  Svc->>Repo: findById(docId)

  alt job not found
    Repo-->>Ctl: empty
    Ctl-->>EH: ResponseStatusException(404)
    EH-->>C: 404 {errorCode: NOT_FOUND, code: NOT_FOUND, message, details, traceId}

  else job found
    Repo-->>Svc: ConversionJob
    Svc-->>Ctl: job snapshot

    alt job.status in FAILED or DEAD_LETTERED
      Ctl-->>V: 409 + ApiErrorResponse { errorCode: CONFLICT, code: CONFLICT, message, details, traceId } for viewer entry point (message includes terminal status)

    else job.status in SUBMITTED or PROCESSING
      Ctl-->>V: 409 + retry guidance

    else job.status = SUCCEEDED
      Ctl-->>V: 200 with viewer bootstrap payload from ConversionJob
      note right of V: planned: prepareViewerSession(docId, outputPath)

      opt planned: preview service session path
        V->>O: prepareViewerSession(docId, outputPath)
        O->>PS: createViewerSession(docId)

        alt preview service returns session
          PS-->>O: token + expiresAt + sessionMetadata
          O-->>V: 200 with token and viewer bootstrap payload

        else preview service timeout/500
          O-->>V: 503 + ApiErrorResponse { errorCode, code, message, details, traceId }

        else unauthorized at preview service
          O-->>V: 401 + ApiErrorResponse { errorCode, code, message, details, traceId } with retryability hint
        end
      end
    end
  end
```

## Exception paths covered

- Missing job and conversion record (404).
- Job not ready for bootstrap (SUBMITTED/PROCESSING) or failed conversion result (FAILED).
- Failed conversion with explicit reason.
- `DEAD_LETTERED` shares the same 409 terminal path with clearer status message.
- Preview service timeout or authorization failure with traceable error path (planned extension).
- HWP/HWPX and manual policy path: preview path only allowed after successful exception policy resolution (planned extension).

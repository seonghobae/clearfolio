# Status Flow UML (component + sequence)

This document covers `GET /api/v1/convert/jobs/{jobId}` behavior for success and error branches.

## Component diagram

```mermaid
flowchart LR
  Client[Client]
  Controller[ConversionController]
  Service[DocumentConversionService]
  Repository[InMemoryConversionJobRepository]
  Job[ConversionJob state machine]
  Error[Framework response + ApiExceptionHandler]

  Client --> Controller
  Controller --> Service
  Service --> Repository
  Repository --> Job
  Controller --> Error
```

## Sequence diagram

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant Ctl as ConversionController
  participant Svc as DocumentConversionService
  participant Repo as InMemoryConversionJobRepository
  participant EH as ApiExceptionHandler

  C->>Ctl: GET /api/v1/convert/jobs/{jobId}
  Ctl->>Svc: getJob(jobId)
  Svc->>Repo: findById(jobId)

  alt job exists
    Repo-->>Svc: ConversionJob (status snapshot)
    alt status = SUBMITTED
      Svc-->>Ctl: convert to ConversionJobStatusResponse
      Ctl-->>C: 200 SUBMITTED with submitted timestamp
    else status = PROCESSING
      Svc-->>Ctl: convert to ConversionJobStatusResponse
      Ctl-->>C: 200 PROCESSING with startedAt
    else status = SUCCEEDED
      Svc-->>Ctl: convert to ConversionJobStatusResponse
      Ctl-->>C: 200 SUCCEEDED with output path
    else status = FAILED
      Svc-->>Ctl: convert to ConversionJobStatusResponse
      Ctl-->>C: 200 FAILED with reason (retry metadata included: attemptCount/maxAttempts/retryAt/deadLettered)
    else status = DEAD_LETTERED
      Svc-->>Ctl: convert to ConversionJobStatusResponse
      Ctl-->>C: 200 DEAD_LETTERED with reason (retry exhausted and terminal)
    end

  else job not found
    Repo-->>Svc: empty
    Svc-->>Ctl: Optional.empty()
    Ctl->>EH: throw ResponseStatusException(404)
    EH-->>C: 404 {errorCode: NOT_FOUND, code: NOT_FOUND, message, details, traceId}
  end
```

## Exception paths covered

- 404 for missing `jobId`.
- Polling while conversion is running (PROCESSING/SUBMITTED).
- FAILED status includes stored failure message and retry metadata for UI rendering.

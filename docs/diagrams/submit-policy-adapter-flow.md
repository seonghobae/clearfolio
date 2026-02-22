# Submit Policy-Exception + Adapter Selection Flow

This UML-style diagram documents the implemented MVP flow that combines:

- blocked-format policy exception lane on submit (`POST /api/v1/convert/jobs`), and
- deterministic renderer adapter metadata on viewer bootstrap (`GET /viewer/{docId}`).

## Sequence diagram

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant Ctl as ConversionController
  participant Svc as DefaultDocumentConversionService
  participant Val as DefaultDocumentValidationService
  participant Repo as InMemoryConversionJobRepository
  participant W as DefaultConversionWorker
  participant View as ViewerBootstrapResponse
  participant EH as ApiExceptionHandler

  C->>Ctl: POST /api/v1/convert/jobs (file + optional X-Clearfolio-* headers)
  Ctl->>Svc: submit(file, PolicyOverrideRequest)
  Svc->>Val: validateOrThrow(file, overrideRequest)

  alt extension blocked (hwp/hwpx) and override=false/missing
    Val-->>Svc: UnsupportedDocumentFormatException
    Svc-->>EH: map unsupported format
    EH-->>C: 400 UNSUPPORTED_FORMAT
  else extension blocked and override=true + token + approver valid
    Val-->>Val: audit-safe log(extension, approverId, tokenFingerprint)
    Val-->>Svc: validation ok
    Svc->>Repo: findOrStoreByContentHash(job)
    Svc->>W: enqueue(jobId) when created
    Svc-->>Ctl: jobId
    Ctl-->>C: 202 Accepted
  else extension allowed
    Val-->>Svc: validation ok
    Svc->>Repo: findOrStoreByContentHash(job)
    Svc->>W: enqueue(jobId) when created
    Svc-->>Ctl: jobId
    Ctl-->>C: 202 Accepted
  end

  C->>Ctl: GET /viewer/{docId}
  Ctl->>Svc: getJob(docId)
  alt job status == SUCCEEDED
    Ctl->>View: from(job)
    View-->>Ctl: sourceExtension + rendererAdapter
    Ctl-->>C: 200 ViewerBootstrapResponse
  else job status != SUCCEEDED
    Ctl-->>EH: ResponseStatusException
    EH-->>C: 409/404 error payload
  end
```

## Deterministic adapter baseline

- `pdf -> PDF_JS`
- `doc/docx -> DOCX_PREVIEW`
- `xls/xlsx/csv/tsv -> SHEET_ADAPTER`
- `ppt/pptx -> SLIDE_ADAPTER`
- `md/txt -> TEXT_ADAPTER`
- default -> `PDF_JS`

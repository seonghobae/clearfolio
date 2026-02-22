# Submit Flow UML (component + sequence)

This document covers the upload path for `POST /api/v1/convert/jobs`, including supported-success, blocked-default, and policy-exception branches.

Implementation note: request handling is currently on Spring WebFlux (non-blocking web stance), while conversion work runs in a bounded asynchronous worker queue. Blocked-format exception lane is implemented via explicit request headers with audit-safe logging.

## Component diagram

```mermaid
flowchart TB
  Client[Client]
  WebFlux[Spring WebFlux Runtime]
  Controller[ConversionController]
  Service[DocumentConversionService]
  Validation[DocumentValidationService]
  Properties[ConversionProperties\nblocked-extensions]
  Repository[InMemoryConversionJobRepository]
  Queue[ThreadPoolTaskExecutor\nlightweight bounded queue]
  Worker[ConversionWorker / DefaultConversionWorker]
  ExceptionHandler[ApiExceptionHandler]
  OverrideHeaders[Policy Override Headers\nX-Clearfolio-*]

  Client --> WebFlux
  WebFlux --> Controller
  Controller --> Service
  Service --> Validation
  Validation --> Properties
  Validation --> Service
  Service --> Repository
  Service --> Queue
  Queue --> Worker
  Worker --> Repository
  Controller --> ExceptionHandler
  Validation --> ExceptionHandler
  Service --> ExceptionHandler
  Client --> OverrideHeaders
  OverrideHeaders --> Controller
```

## Sequence diagram

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant Ctl as ConversionController
  participant Svc as DocumentConversionService
  participant V as DefaultDocumentValidationService
  participant P as ConversionProperties
  participant Repo as InMemoryConversionJobRepository
  participant W as ConversionWorker
  participant EH as ApiExceptionHandler

  C->>Ctl: POST /api/v1/convert/jobs (multipart + optional X-Clearfolio-* headers)
  Ctl->>Svc: submit(file, policyOverrideRequest)
  Svc->>V: validateOrThrow(file, policyOverrideRequest)

  alt Empty/missing file
    V-->>Svc: IllegalArgumentException
    Svc-->>EH: map to BAD_REQUEST
    EH-->>C: 400 {errorCode: BAD_REQUEST, code: BAD_REQUEST}

  else Extension in blocked list (hwp or hwpx)
    V->>P: getBlockedExtensions()
    alt Override headers valid
      V-->>V: validate override=true + token + approver
      V-->>V: emit audit-safe log(extension, approver, tokenFingerprint)
      V-->>Svc: validation ok
    else Override missing/invalid
      V-->>Svc: UnsupportedDocumentFormatException or IllegalArgumentException
      Svc-->>EH: handleUnsupported / handleBadRequest
      EH-->>C: 400 {errorCode, code, details}
    end

  else Validation passes
    V-->>Svc: validation ok
    Svc-->>Svc: sha256(file stream)

    alt Hash calculation fails
      Svc-->>C: 500 InternalServerError
    else Hash calculated
      Svc->>Repo: findOrStoreByContentHash(job)
      Repo-->>Svc: canonical job id

      alt first submission of hash
        Svc->>W: enqueue(jobId) via bounded queue
      else duplicate hash
        Svc->>Svc: reuse canonical jobId
      end

      Svc-->>Ctl: jobId
      Ctl-->>C: 202 Accepted + jobId
    end
  end
```

## Exception paths covered

- Missing or empty file
- Empty/invalid file name extension
- HWP/HWPX blocked extension (default lane)
- HWP/HWPX approved exception lane with required override headers
- Invalid override header combinations (missing token/approver or invalid override flag)
- Hashing failure during submit (mapped to generic server error currently)
- Duplicate submission reused from canonical hash

## File-level evidence pointers

- WebFlux runtime dependency: `pom.xml`
- Submit endpoint behavior: `src/main/java/com/clearfolio/viewer/controller/ConversionController.java`
- Submit orchestration and dedupe: `src/main/java/com/clearfolio/viewer/service/DefaultDocumentConversionService.java`
- Bounded queue config: `src/main/java/com/clearfolio/viewer/config/ConversionExecutorConfig.java`
- Retry/dead-letter worker logic: `src/main/java/com/clearfolio/viewer/service/DefaultConversionWorker.java`

# OSS reference scan (2026-02-23)

Purpose: capture high-signal OSS references (conceptual patterns only) for the
Clearfolio Viewer unified document preview platform.

License policy note:

- Copyleft (GPL/AGPL/LGPL) dependencies are not permitted for direct import in
  this repo. Copyleft projects may be referenced conceptually only.

## Candidates

| Repo | License | What we borrow (concept) | Notes / cautions |
|---|---|---|---|
| `https://github.com/mozilla/pdf.js` | Apache-2.0 | The baseline for a full-featured PDF viewer shell (toolbar, thumbnails, search, render backpressure patterns). | Integration is non-trivial (assets, worker, range requests, CORS). Treat as frontend reference, not a small widget. |
| `https://github.com/filebrowser/filebrowser` | Apache-2.0 | Unified preview shell pattern: one route that chooses renderer by MIME/extension and keeps consistent actions/navigation. | Their PDF preview is not PDF.js-grade; treat as an IA/UX/layout reference, not rendering reference. |
| `https://github.com/jodconverter/jodconverter` | Apache-2.0 | Server-side conversion pattern using LibreOffice/OpenOffice: lifecycle, timeouts, restart on failure, temp isolation. | LibreOffice is heavyweight; requires strict resource/time limits and likely container isolation. |
| `https://github.com/apache/tika` | Apache-2.0 | Multi-format metadata/text extraction to support search/indexing separate from rendering. | Parsing can be expensive on hostile inputs; add timeouts/limits and keep dependencies patched. |
| `https://github.com/redisson/redisson` | Apache-2.0 | Lightweight queue/worker patterns backed by Redis/Valkey for MVP hardening (delayed queues, at-least-once semantics). | Design for idempotency and dedupe keys; plan for Redis operational dependency and lag monitoring. |

## Implementation stance in this repo

- Current codebase ships a bounded in-process queue/executor and a background
  worker simulation (MVP) under WebFlux.
- Durable queue + real converter runtime are explicitly deferred to the
  hardening phase and should reference JODConverter/LibreOffice integration.

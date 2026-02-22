# Document Flow Diagrams

This folder contains UML documentation files for the Integrated Document Viewer flows.

Legend:
- Solid component/flow: implemented in current MVP backend.
- Dashed component/flow or explicit `planned` note: documented next-step scope, not yet implemented.

- `submit-flow.md`
  - Submit API component and sequence path.
  - Covers successful submit, duplicate handling, HWP/HWPX blocked/error branches, and implemented override-header exception lane.
- `submit-policy-adapter-flow.md`
  - Combined sequence for submit policy-exception lane and viewer adapter metadata bootstrap.
  - Covers explicit override header requirements and deterministic `sourceExtension -> rendererAdapter` selection.
- `status-flow.md`
  - Status query path and status transition branches.
  - Covers `SUBMITTED` / `PROCESSING` / `SUCCEEDED` / `FAILED` and not-found.
- `preview-flow.md`
  - Implemented viewer entrypoint flow for `GET /viewer/{docId}` (state-gated bootstrap/error contract).
  - Planned follow-up: S2S preview session call, token bootstrap, and preview failure branches.

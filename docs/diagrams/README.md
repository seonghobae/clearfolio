# Document Flow Diagrams

This folder contains UML documentation files for the Integrated Document Viewer flows.

- `submit-flow.md`
  - Submit API component and sequence path.
  - Covers successful submit, duplicate handling, and HWP/HWPX blocked/error branches.
- `status-flow.md`
  - Status query path and status transition branches.
  - Covers `SUBMITTED` / `PROCESSING` / `SUCCEEDED` / `FAILED` and not-found.
- `preview-flow.md`
  - Implemented viewer entrypoint flow for `GET /viewer/{docId}` (state-gated bootstrap/error contract).
  - Planned follow-up: S2S preview session call, token bootstrap, and preview failure branches.

# GitHub Transfer Metadata

This file captures repository handoff details required before GitHub ownership transfer.

## Identity

- Repository intent: `clearfolio-viewer` (MVP backend for integrated document viewer)
- Language/runtime: Java 21, Spring Boot
- Primary package: `com.clearfolio.viewer`
- Initial artifact: `clearfolio-viewer` (Maven artifact from `pom.xml`)

## Transfer readiness checklist

- [x] Root README exists (`README.md`)
- [x] Architecture baseline exists (`docs/architecture.md`)
- [x] TRD + PRD alignment exists (`docs/trd-integrated-document-viewer-platform.md`)
- [x] UML flow documentation exists for submit/status/preview (`docs/diagrams/*-flow.md`)
- [x] Acceptance mapping updated for AC-1 ~ AC-9 in architecture/TRD docs
- [ ] License file added
- [ ] Explicit `CODEOWNERS` set to transfer team
- [ ] CI/security workflow files added for long-term governance

## Handover pointers

- Core implementation entry points: `src/main/java/com/clearfolio/viewer`
- Public API entry points: `src/main/java/com/clearfolio/viewer/controller`
- Current test surface: `src/test/java/com/clearfolio/viewer`

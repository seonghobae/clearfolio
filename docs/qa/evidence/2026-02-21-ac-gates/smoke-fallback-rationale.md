# Smoke Test Evidence (non-docker fallback)

Date: 2026-02-21

## Why fallback was used

- No dockerized runtime assets are present in this repository (`Dockerfile`, `compose.yml`, `docker-compose.yml` not found).
- Per runbook policy, smoke validation was executed on local JVM runtime as the nearest reproducible path.

## Commands and outcomes

1. Start app:
   - `mvn -q -DskipTests spring-boot:run`
   - Outcome: app started, Netty bound to `localhost:8080`.
2. Readiness check:
   - `GET /actuator/health` returned `404` (endpoint not configured in this baseline).
   - `GET /healthz` returned `200` with `{"status":"ok"}`.
3. Submit conversion job:
   - `POST /api/v1/convert/jobs` with `text/plain` sample file.
   - Outcome: `202 ACCEPTED`, response contained `jobId` and `statusUrl`.
4. Poll job status:
   - `GET /api/v1/convert/jobs/{jobId}`.
   - Outcome: `200`, terminal state reached (`SUCCEEDED`).
5. Viewer bootstrap:
   - `GET /viewer/{jobId}`.
   - Outcome: `200`, payload returned `viewerMode=PDF_JS` and converted resource path.

## Notes

- This smoke run validates the critical path: health -> submit -> status -> viewer.
- For future hardening, add an explicit dockerized smoke path and `/actuator/health` readiness endpoint.

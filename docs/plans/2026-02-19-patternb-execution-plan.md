# PatternB 실행계획 (초안)

> I'm using the writing-plans skill to create the implementation plan.

날짜: 2026-02-19

Goal: 제품 관점의 PatternB(변환 파이프라인) 구현 및 릴리스 준비

Architecture: Java backend (변환/큐/오케스트레이션) + PDF.js(프론트뷰어) + JODConverter/LibreOffice(변환 엔진) → 최종 PDF

Tech Stack: Java (Spring/Quarkus 등), PDF.js, JODConverter/LibreOffice(컨버터 컨테이너), 메시지 큐(RabbitMQ/SQS/Kafka), 오브젝트 스토리지(S3), PostgreSQL

---

## 1) Decisions / Assumptions

| Item | Decision / Assumption | Owner |
|---|---:|---|
| 아키텍처 | Java backend + PDF.js(프론트) + JODConverter/LibreOffice(변환) → PDF (PatternB) | @product-manager |
| HWP/HWPX 처리 | 기본 차단(격리). 예외 변환은 별도 격리 파이프라인에서 수동/자동 심사 후 수행 | @project-manager |
| AV 스캔 | 업로드 트리거로 AV 스캔 상태를 기록하고, 변환 전 Worker가 "clean" 상태 확인 (비동기 처리) | @project-manager |
| 비동기 처리 | 요청 경로는 Non-blocking(202 Accepted + job_id)로 설계 | @project-manager |
| 변환 캐시 | content-hash 기반 변환 캐시(중복 변환 방지) | @engineering-lead |
| 감사로그/격리 | 변환/AV/큐/인증 로그는 불변성(append-only)으로 저장, 변환 작업은 컨테이너/네임스페이스 격리 | @project-manager |
| 라이선스 정책 | GPL/AGPL/LGPL 등 copyleft 사용 금지, 화이트리스트 방식 허용 | @product-manager |
| UI/UX | UI/UX Ver3.0 준수, 편차 발생시 제품 수용 기준과 편차관리 프로세스 적용 | @product-manager |
| DB 명명 | 테이블/컬럼 모두 snake_case, 최소 두 단어(예: conversion_jobs) | @project-manager |
| 네이밍/슬러그 | 네이밍은 생성 금지. 반드시 branding-expert 검토 요청 (Top-1 및 슬러그 제안 요청) | @product-manager |

---

## 2) AC & Priority Matrix

| AC | Priority (H/M/L) | Verification / Evidence (검증방법 및 증적 위치) | Owner |
|---|---:|---|---|
| API 보안 체크리스트(인증/권한, TLS, 입력 검증, rate-limit) 적용 | H | 자동: .github/workflows/security-scan.yml 실행 (gh workflow run security-scan.yml --repo <OWNER/REPO> --ref main). 증적: actions artifact: security-scan-<run-id>.zip, 수동: OWASP ZAP 리포트 artifacts/security/zap-<id>.html | @security |
| 비동기 큐 - 요청 경로에서 동기 대기 금지(202 Accepted + job_id) | H | 검증: curl POST 요청이 202 반환, 동일 파일 2회 업로드 시 두번째는 cache hit 확인. 예: curl -i -X POST "https://<API_BASE>/convert" -H "Authorization: Bearer $TOKEN" -F "file=@sample.docx" | grep "202". 증적: artifacts/requests/<job_id>.json, logs/queue_metrics.log | @engineering-lead |
| 업로드 시 AV 스캔 + 변환 전 Worker 재확인(비동기) + 재시도 정책 및 DLQ | H | 검증: 업로드 직후 AV 상태 레코드 확인 (SELECT status FROM av_scan WHERE file_hash='...'). 재시도 테스트: 의도적 실패 파일 enqueued → worker 재시도 확인(리트라이 카운트) → DLQ 항목 존재 확인. 증적: artifacts/av/<run-id>.log, dlq/entries/<id>.json | @security |
| content-hash 기반 변환/캐시 (중복 변환 방지) | H | 검증: conversion_cache 테이블(예: conversion_cache)에서 동일 file_hash 조회. 테스트: 동일 파일 2회 변환 요청 → 두번째 변환은 캐시 사용(변환 컨테이너 미기동). 증적: SELECT * FROM conversion_cache WHERE file_hash='<hash>'; artifacts/cache/<hash>.meta | @engineering-lead |
| 변환 작업 격리(컨테이너/네임스페이스) 및 리소스 제한 | H | 검증: 변환은 container runtime 상에서 실행(예: k8s pod/namespace). 증적: kubectl get pods -n conversion-<id>, container logs 저장: artifacts/containers/<id>.log. 보안: seccomp/AppArmor 프로파일 적용 확인 | @infra |
| 감사로그(변환/AV/큐/인증) 불변 저장 | H | 검증: 감사 로그는 append-only object storage 또는 DB append-only 테이블에 저장. 증적: s3://<bucket>/audit/converted_jobs.log, audit-signatures/*.sig | @project-manager |
| NUL(\u0000) 정화: 영속성 경계 1곳(Conversion persistence adapter)에서 재귀적 제거 | H | 검증: 테스트 케이스 삽입(문자열에 NUL 포함) → DB 저장 후 SELECT으로 NUL 제거 여부 확인. 검증 SQL 예: SELECT octet_length(field) - octet_length(replace(field, E'\\x00','')) AS nul_count FROM <table> WHERE id=<id>; 증적: artifacts/db-tests/nul-sanitize-<id>.log | @engineering-lead |
| PgBouncer / PgCat 감지 및 Read-only 라우팅 검증 | M | 검증: DB 접속 시 pooler 탐지 스크립트 실행: psql -h <host> -U <user> -d <db> -c "SELECT application_name, count(*) FROM pg_stat_activity GROUP BY application_name;" (pooler 감지), k8s 환경: kubectl get svc -n db -l app=pgbouncer. 증적: artifacts/db/pooler-detection-<ts>.log | @dba |
| Dockerized smoke test 위임 및 증적 저장 (qa-engineer/system-admin) | M | 검증: qa-engineer/system-admin이 docker compose up && sleep+curl 헬스체크 후 로그를 PR 코멘트로 첨부. 권장명령(예시, PR 코멘트 증적): `docker compose up -d && sleep 10 && docker compose ps && docker compose logs --since 1m`. 증적: PR 코멘트 링크(요약 기록) | @project-manager |
| UI/UX Ver3.0 준수 및 편차관리 프로세스 | M | 검증: UI checklist(Ver3.0) 문서화 및 제품 수용 테스트. 증적: docs/ui/ux-ver3-checklist.md, Figma review 링크, approval: @product-manager 서명(이슈 코멘트) | @product-manager |
| 라이선스(허용/차단) 자동 스캔(블락) | H | 검증: .github/workflows/license-scan.yml 실행, artifact: license-report.json. CI 실패 기준: GPL/AGPL/LGPL 발견 시 빌드 차단. 증적: artifacts/license-report.json | @security |

---

## 3) 10주 마일스톤·게이트 (주 단위)

| Week | Goal | Gate Acceptance Criteria |
|---:|---|---|
| Wk1 | 킥오프, AC 확정, 아키텍처 도면 완성 | AC 표(섹션2)에 대해 @product-manager 서명, Milestone 세팅(GitHub Milestone) | 
| Wk2 | 인프라 프로토타입: 큐, 오브젝트 스토리지, DB 스키마 초안 | 큐 동작 확인(샘플 메시지), DB 스키마(PR 포함), conversion_cache 테이블 초안 머지 | 
| Wk3 | 변환 워커(컨테이너) POC: JODConverter/LibreOffice 기반 | 컨버터 컨테이너로 샘플 DOCX→PDF 변환 성공(artifacts/conv/poc-<id>.pdf) | 
| Wk4 | AV 스캔 연동 및 DLQ/재시도 정책 구현 | AV scan record 생성 확인, 실패시 DLQ 적재 확인, 재시도 정책 문서화 | 
| Wk5 | content-hash 캐시 및 중복 처리 완성 | 동일 파일 2회 업로드 테스트에서 캐시 히트 확인, DB 레코드 증적 | 
| Wk6 | UI/UX 개발(뷰어 통합: PDF.js) 및 Ver3.0 검토 | PDF.js 탭/뷰어 통합 완료, UI 체크리스트 통과(제품 서명 필요) | 
| Wk7 | 변환 작업 격리(컨테이너 정책), 감사로그 완성 | 변환은 별도 네임스페이스/컨테이너로 실행, 감사로그 저장/서명 확인 | 
| Wk8 | CI: 라이선스/보안 스캔 완비, 자동화 워크플로우 | license-scan, security-scan workflow 통과(또는 차단 기준 문서화) | 
| Wk9 | Dockerized smoke test(qa-engineer/system-admin 위임) 및 안정화 | smoke test 증적(PR 코멘트) 확보, 발견된 critical 이슈 해결 | 
| Wk10 | 릴리스 준비 및 sign-off | Release Readiness: 모든 H 우선 AC 통과, license-scan green, smoke test 증적, 롤백/운영 가이드 문서화, @project-manager(+@product-manager) 승인 | 

Release Readiness 기준(요약): 섹션2의 모든 H 우선 AC 통과 · license-scan 통과(또는 승인된 대체 계획) · Dockerized smoke test 증적 PR 코멘트 첨부 · DB 마이그레이션 롤백 플랜 존재 · 감사로그 접근성 확인

---

## 4) RACI

| Task | R | A | C | I |
|---|---|---|---|---|
| AC 정의 및 제품 수용 기준 최종화 | @product-manager | @product-manager | @engineering-lead, @project-manager | Stakeholders |
| 일정·리소스·릴리스 프로세스 수립 | @project-manager | @project-manager | @product-manager, @engineering-lead | Stakeholders |
| API/큐 기본 구현 | @engineering-team | @project-manager | @product-manager, @dba | @qa-engineer |
| 변환 워커(컨테이너) 구현 | @engineering-team | @project-manager | @infra, @security | @product-manager |
| AV 스캔 통합 및 DLQ | @security | @project-manager | @engineering-team | @product-manager |
| content-hash 캐시 및 DB 스키마 | @dba/@engineering-team | @project-manager | @engineering-lead | @product-manager |
| 라이선스 정책(화이트리스트) 수립 및 CI 구현 | @security | @product-manager | @legal, @engineering-lead | @project-manager |
| UI/UX 구현 및 Ver3.0 검토/승인 | @frontend | @product-manager | @ux-ui | @project-manager |
| Dockerized smoke test 위임·증적 수집 | @qa-engineer / @system-admin | @project-manager | @engineering-team | @product-manager |
| 브랜딩/네이밍 검토(슬러그 제안 요청) | @product-manager | @product-manager | @branding-expert | @project-manager |

명확한 경계: @product-manager는 제품 요구·수용(AC)·UI/UX 승인 책임자(A). @project-manager는 일정·리소스·릴리스 준비(A) 책임자이며 smoke test 위임 및 증적 수집 책임을 가진다.

---

## 5) Risk Register (Top 10~12)

| ID | Risk | Impact | Likelihood | Mitigation | Owner | Evidence |
|---|---|---:|---:|---|---|---|
| R1 | 허용 불가 copyleft 라이브러리(예: GPL/AGPL/LGPL) 발견 | High | Medium | CI license-scan 차단, 대체 라이브러리 제안(상용/퍼미시브) | @product-manager | artifacts/license-report.json |
| R2 | HWP/HWPX 변환 불가(레거시 포맷) | High | Medium | 기본 차단·격리, 별도 POC/유료 변환 서비스/수동 파이프라인 마련 | @project-manager | issue #<HWP-POC> |
| R3 | AV 스캔 오탐/오류로 정상 파일 차단 | Medium | Medium | 이중검사, 허용된 false-positive 처리 프로세스, DLQ/수동 검토 | @security | artifacts/av/<id>.log |
| R4 | 변환 워커 과부하/스케일 이슈 | High | Medium | autoscaling 정책, 리소스 제한, 큐 백프레셔 · 모니터링 | @infra | metrics/worker_cpu_usage/*.png |
| R5 | NUL 정화로 인한 데이터 훼손 | High | Low | 원본 파일(오브젝트 스토리지) 보존, NUL 정화는 텍스트 필드에만 적용, 검증 케이스 | @engineering-lead | artifacts/db-tests/nul-sanitize-*.log |
| R6 | PgBouncer/PgCat 호환성 문제(트랜잭션 pooling) | High | Medium | Pooler 감지 스크립트, 트랜잭션 짧게 유지, SKIP LOCKED 사용 검토 | @dba | artifacts/db/pooler-detection-<ts>.log |
| R7 | 감사로그 무결성/위·변조 위험 | High | Low | 서명(해시) 저장, WORM 객체 스토리지, 접근 제어 | @project-manager | s3://<bucket>/audit/*.log |
| R8 | 이미지/컨테이너 보안 취약점 | Medium | Medium | 이미지 스캐너(CVE) 도입, SBOM 생성 | @infra | artifacts/scan/image-scan-<id>.json |
| R9 | Dockerized smoke test 실패로 릴리스 지연 | Medium | Medium | 조기 실행(개발 브랜치), qa-engineer/system-admin 위임 및 증적 수집 | @project-manager | PR comments (smoke test) |
| R10 | 변환 성능 저하(대형 파일, 다중 페이지) | High | Medium | 성능 경계 평가, EXPLAIN/프로파일링, 리소스 샤딩 | @engineering-lead | artifacts/perf/*.json |
| R11 | 개인정보/민감정보 처리(법적 리스크) | High | Low | PII 탐지, 마스킹/암호화 정책, 법무 검토 | @product-manager | docs/privacy/PII-policy.md |
| R12 | DB 마이그레이션 실패로 인한 롤백 불가 | High | Low | 롤백 스크립트 및 검증, 무중단 마이그레이션 전략 문서화 | @dba | migrations/manual/rollback.md |

---

## 6) gh 중심 즉시 실행 명령 (정확히 5개)

| Command | Purpose |
|---|---|
| gh issue create --repo <OWNER/REPO> --title "Request: branding-expert review for PatternB naming" --body "Please review naming and provide Top-1 recommendation + suggested slug. DO NOT auto-generate slug in this plan." --label branding --assignee <BRANDING_EXPERT_GH_HANDLE> | 네이밍/슬러그 검토 요청 (branding-expert 할당) |
| gh issue create --repo <OWNER/REPO> --title "Run CI: license-scan for PatternB (block copyleft)" --body "Run license-scan workflow and attach artifacts. Block builds on GPL/AGPL/LGPL detection." --label security,license --assignee <SECURITY_TEAM_HANDLE> | 라이선스 스캔 이슈 생성 (CI 트리거 요청) |
| gh pr create --repo <OWNER/REPO> --title "docs: add PatternB execution plan" --body "Path: docs/plans/2026-02-19-patternb-execution-plan.md" --head "patternb/plan-2026-02-19" --base main --draft | 계획 문서 PR 생성 (브랜치/PR 생성 후 증적 수집) |
| gh issue create --repo <OWNER/REPO> --title "QA: Run dockerized smoke-tests for PatternB" --body "Run: docker compose up -d && sleep 10 && docker compose ps && docker compose logs --since 1m. Attach PR comment with logs." --label qa,smoke --assignee <QA_ENGINEER_GH_HANDLE> --assignee <SYSTEM_ADMIN_GH_HANDLE> | QA/system-admin에게 dockerized smoke test 위임 요청 |
| gh workflow run license-scan.yml --repo <OWNER/REPO> --ref main | license-scan 워크플로 수동 실행(command to produce immediate license report) |

---

## 7) Unknowns / Residual Risks

| Unknown | Resolution command (비대화형) |
|---|---|
| production DB 접속 정보(연결 문자열/읽기 전용 엔드포인트 유무) | git ls-files | grep -E "(prod|production).*(credentials|env|.secrets)" || kubectl get secret -n production <db-secret> -o yaml (권한필요) |
| JODConverter / LibreOffice / PDF.js의 최종 라이선스(프로덕션 적용 가능 여부) | curl -s https://raw.githubusercontent.com/<repo_owner>/<repo>/main/LICENSE | head -n 20 (또는 gh api repos/<owner>/<repo> -q .license) |
| HWP/HWPX 자동 변환 가능성(외부 상용 서비스 필요 여부) | Run POC: docker run --rm -v $(pwd):/work libreoffice-docker libreoffice --headless --convert-to pdf /work/sample.hwp (권한/환경 필요) |
| repo 내부에 Docker/Compose 존재 여부 자동 감지 | git ls-files | grep -E "(^|/)docker-compose.yml$|(^|/)Dockerfile$|(^|/)docker/" |
| PgBouncer/PgCat 존재 여부 및 모드 | psql "host=<db_host> user=<user> dbname=<db>" -c "SELECT application_name, count(*) FROM pg_stat_activity GROUP BY application_name;" |

---

Subagents used:

- branding-expert: 네이밍/슬러그 Top-1 추천 요청 및 검토
- qa-engineer: Dockerized smoke test 실행 및 PR 코멘트 증적 수집(위임 담당)
- system-admin: Docker 인프라 기동/로그 수집 보조

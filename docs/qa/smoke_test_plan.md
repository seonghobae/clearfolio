# Smoke Test Plan (MVP Backend)

작성자: qa-engineer (하위 에이전트)
생성일: 2026-02-19

## 요약

현재 저장소는 백엔드 API-only MVP 스코프로 운영됩니다.
이 문서는 `health`, `POST /api/v1/convert/jobs`, `GET /api/v1/convert/jobs/{jobId}`,
`GET /viewer/{docId}`(alias: `/api/v1/viewer/{docId}`,
`/api/v1/convert/viewer/{docId}`) 중심의 스모크 체크를 다룹니다.
정적 자산 캐시, PostgreSQL EXPLAIN 수집, Compose 기반 배포 smoke는 현재 범위 밖입니다.

## 범위 및 가정

- 테스트 환경: 로컬 JVM 실행 (`mvn spring-boot:run` 또는 패키지 실행)
- 기본 URL: `http://localhost:8080`
- 저장소: In-memory 작업 저장(`InMemoryConversionJobRepository`)
- 백그라운드 처리: 비동기 Worker 시뮬레이션 (`DefaultConversionWorker`)
- 정적 자산/DB 검증은 현재 단계에서 제외

## 목표 (Acceptance Criteria)

1. 애플리케이션 기동 후 `/healthz`가 `200`과 `{"status":"ok"}`를 반환한다.
2. `POST /api/v1/convert/jobs`가 multipart 업로드에 대해 `202`와
   `jobId`, `status`, `statusUrl`을 반환한다.
3. 동일 파일 중복 제출 시 같은 `jobId`가 재사용되어 dedupe 동작을 확인할 수 있다.
4. `hwp`, `hwpx` 업로드가 `400`으로 거부되고 구조화 에러 스키마(`errorCode`, `message`, `traceId`, `details`, 호환 `code`)를 반환한다.
5. `GET /api/v1/convert/jobs/{jobId}`가 없는 id에 `404`를, 존재하는 id에 상태 정보를 반환한다.
6. 동일 요청에서 `SUBMITTED` → `PROCESSING` → `SUCCEEDED` 상태 전이가 관찰된다.
7. NUL 바이트가 포함된 입력에서 5xx나 프로세스 다운이 발생하지 않는다.
8. `GET /viewer/{docId}`(`/api/v1/viewer/{docId}`, `/api/v1/convert/viewer/{docId}`)에서
   `SUCCEEDED`는 `200 + bootstrap payload`를, `SUBMITTED`/`PROCESSING`/`FAILED`/`DEAD_LETTERED`는
   `409`를, 없는 `docId`는 `404`를 반환한다.
9. 검증 명령 실행 시 warning/deprecated 출력이 0건이어야 한다.

## Go / No-Go 기준

- No-Go: 위 목표 중 1개 이상 실패, 또는 Sev0/Sev1 수준의 실패 재현.
- Go: 위 항목 1~8 모두 통과, 로그 수집이 가능한 경우.

## 자동화 검증 명령(권장)

```bash
mvn test

# warning/deprecated fail-fast gate
mvn -q -DskipTests compile

mvn spring-boot:run > logs/app.stdout.log 2>&1 &
APP_PID=$!
mkdir -p logs test-results

sleep 4
curl -sS --max-time 5 http://localhost:8080/healthz | jq .

cat > /tmp/smoke-sample.txt <<'EOF'
smoke sample document
EOF

SUBMIT_RESPONSE=$(curl -sS -X POST \
  -F "file=@/tmp/smoke-sample.txt;type=text/plain" \
  http://localhost:8080/api/v1/convert/jobs)
echo "$SUBMIT_RESPONSE" | tee test-results/submit-response.json
JOB_ID=$(echo "$SUBMIT_RESPONSE" | jq -r '.jobId')
STATUS_URL=$(echo "$SUBMIT_RESPONSE" | jq -r '.statusUrl')

curl -sS --max-time 5 "http://localhost:8080${STATUS_URL}" | jq .

for _ in 1 2 3 4 5; do
  curl -sS --max-time 5 "http://localhost:8080${STATUS_URL}" | tee -a test-results/status-poll.jsonl
  sleep 1
done

# Viewer /viewer contract checks (MVP)
# 1) 상태 미완료 시에는 409 (초기/중간 조회)
for _ in 1 2 3 4 5; do
  for ENDPOINT in \
    "/viewer/${JOB_ID}" \
    "/api/v1/viewer/${JOB_ID}" \
    "/api/v1/convert/viewer/${JOB_ID}"; do
    HTTP_CODE=$(curl -sS \
      -o test-results/viewer-poll-${_}.json \
      -w "%{http_code}" --max-time 5 "http://localhost:8080${ENDPOINT}")
    echo "${ENDPOINT} -> ${HTTP_CODE}" | tee -a test-results/viewer-poll.jsonl
  done
  sleep 1
done

# 2) SUCCEEDED 전이 후 성공 페이로드 확인
for _ in $(seq 1 20); do
  STATUS_PAYLOAD=$(curl -sS --max-time 5 "http://localhost:8080${STATUS_URL}")
  STATUS_VALUE=$(echo "$STATUS_PAYLOAD" | jq -r '.status')
  if [ "$STATUS_VALUE" = "SUCCEEDED" ]; then
    echo "status=SUCCEEDED";
    for ENDPOINT in \
      "/viewer/${JOB_ID}" \
      "/api/v1/viewer/${JOB_ID}" \
      "/api/v1/convert/viewer/${JOB_ID}"; do
      echo "Checking ${ENDPOINT} success payload"
      curl -sS --max-time 5 "http://localhost:8080${ENDPOINT}" | tee -a test-results/viewer-success.jsonl
    done
    break
  elif [ "$STATUS_VALUE" = "FAILED" ]; then
    echo "status=FAILED";
    break
  else
    echo "wait: ${STATUS_VALUE}"
    sleep 1
  fi
done

# 3) 없는 docId는 404
curl -i -sS \
    --max-time 5 \
    "http://localhost:8080/viewer/00000000-0000-0000-0000-000000000000" \
    | tee test-results/viewer-not-found.json

SUBMIT_RESPONSE_2=$(curl -sS -X POST \
  -F "file=@/tmp/smoke-sample.txt;type=text/plain" \
  http://localhost:8080/api/v1/convert/jobs)
echo "$SUBMIT_RESPONSE_2" | tee test-results/submit-duplicate.json
JOB_ID_2=$(echo "$SUBMIT_RESPONSE_2" | jq -r '.jobId')

if [ "$JOB_ID" = "$JOB_ID_2" ]; then
  echo "PASS: duplicate jobId reuse"
else
  echo "FAIL: duplicate returned different jobId"
fi

printf "blocked" > /tmp/smoke-blocked.hwp
curl -i -sS -X POST \
  -F "file=@/tmp/smoke-blocked.hwp;type=application/octet-stream" \
  http://localhost:8080/api/v1/convert/jobs | tee test-results/blocked-extension.json

python3 - <<'PY'
from pathlib import Path
Path('/tmp/smoke-nul.bin').write_bytes(b'data-with-' + b'\x00' + b'nul')
PY
curl -i -sS -X POST \
  -F "file=@/tmp/smoke-nul.bin;type=application/octet-stream" \
  http://localhost:8080/api/v1/convert/jobs | tee test-results/nul-response.json

kill "$APP_PID" 2>/dev/null || true
```

## 수동 검증 체크리스트

1. 애플리케이션 로그(`logs/app.stdout.log`)에서 업로드/상태 전이가 예외 없이 진행되는지 확인한다.
2. 테스트 후 `jobId` 조회가 일정 시간 내 `SUCCEEDED`로 끝나는지 확인한다.
3. 중복 제출 재사용이 문서화된 dedupe 기대값과 일치하는지 확인한다.
4. 금지 확장자 거절 응답에 구조화 에러 스키마(`errorCode/message/traceId/details` + 호환 `code`)가 포함되는지 확인한다.

## NUL 테스트 케이스

### TC-NUL-01 — 파일 바디 raw NUL

- 설명: 바디에 `0x00` 바이트가 포함된 파일 업로드가 서버를 중단시키지 않는지 확인
- 기대: `202` 또는 `400`은 허용, 핵심 조건은 `5xx` 또는 프로세스 비정상 종료 미발생

### TC-NUL-02 — NUL 포함 문자열 인코딩

- 설명: 애플리케이션 내 문자열 정화 경로에서 `\u0000` 패턴이 제거되는지 확인
- 방법: 코드/운영 테스트 또는 API 응답 문자열 확인으로 확인
- 기대: 응답 문자열에서 NUL 인코딩이 그대로 남지 않음

## dedupe 테스트 케이스

### TC-DEDUPE-01 — 동일 파일 제출 재사용

- 방법: 동일 파일을 연속 2회 제출
- 기대: 같은 `jobId` 반환

### TC-DEDUPE-02 — 상태 동작 확인

- 방법: 제출 후 즉시 status API 조회 후 수초 단위 재조회
- 기대: `SUBMITTED` → `PROCESSING` → `SUCCEEDED`

## PostgreSQL EXPLAIN 항목

현재 구현은 DB를 사용하지 않으므로 `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`는 실행하지 않습니다.
대체 증적은 `test-results/status-poll.jsonl`,
`test-results/submit-response.json`, `logs/app.stdout.log`에 남긴 상태 전환 로그입니다.

## 결과 템플릿

- 테스트 요약: PASS/FAIL
- 환경: Java/Maven 버전, 실행 포트, OS
- 재현 절차: 위 명령 블록 사용
- 기대 vs 실제
- 관찰된 로그/응답 경로: `test-results/*`, `logs/*`, `evidence/smoke/20260220_0624/*`

## 변경 파일(이 QA 업데이트에서 수정된 파일)

| File | Change | Intent | Why | Risk/Notes |
|---|---:|---|---|---|
| smoke_test_plan.md | update | viewer/state smoke 정합성 | 문구 정렬 | 컴포즈 항목 제외 |

## 참고

본 계획은 현재 MVP의 최소 가시성 확보용입니다.
Docker/Compose, 실제 DB, 그리고 artifact 캐시 검증은 추후 실행 플랜 7단계(운영 하드닝)에 맞춰 별도 문서로 추가하면 됩니다.

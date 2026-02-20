# Acceptance Evidence Checklist

This checklist captures mandatory/optional acceptance evidence for current MVP release gates.

## Mandatory gates

1. Test coverage 100%
   - Command:
     - `mvn -q -Djacoco.includes=com.clearfolio.viewer.* org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.13:report`
   - Evidence:
     - `target/site/jacoco/jacoco.csv`
     - `line_missed=0`, `branch_missed=0`

2. Docstring 100%
   - Scope:
     - Public production symbols under `src/main/java`.
   - Evidence:
     - JavaDoc comments present for public class/interface/record/enum and public methods/constructors.

3. Non-blocking web path
   - Criteria:
     - Submit/status/viewer handlers return quickly without inline conversion.
   - Evidence:
     - `src/main/java/com/clearfolio/viewer/controller/ConversionController.java`
     - `src/main/java/com/clearfolio/viewer/service/DefaultDocumentConversionService.java`
     - `src/main/java/com/clearfolio/viewer/service/DefaultConversionWorker.java`

4. Lightweight event queue
   - Criteria:
     - Bounded executor + retry scheduling + dead-letter fallback.
   - Evidence:
     - `src/main/java/com/clearfolio/viewer/config/ConversionExecutorConfig.java`
     - `src/main/java/com/clearfolio/viewer/model/ConversionJob.java`
     - `src/main/java/com/clearfolio/viewer/service/DefaultConversionWorker.java`

5. Warning count 0
   - Command:
     - `mvn -q -DskipTests compile`
   - Gate:
     - Build fails on warnings via `-Xlint:all -Werror`.

6. Deprecated count 0
   - Command:
     - `mvn -q -DskipTests compile`
   - Gate:
     - Deprecated usage treated as warning and blocked by `-Werror`.

7. One-day delivery schedule + security verification
   - Schedule artifact:
     - `docs/plans/2026-02-20-24h-customer-delivery-plan.md`
   - Security verification commands:
     - `semgrep --config auto --error --json --output target/semgrep.json src/main/java`
     - `gh api "/repos/HYOSUNG-ITX-AI-Business-Department/clearfolio-viewer/code-scanning/analyses?pr=1"`
     - `gh api "/repos/HYOSUNG-ITX-AI-Business-Department/clearfolio-viewer/code-scanning/alerts?pr=1&state=open"`
   - Gate:
     - Security command outputs are recorded and attached to PR evidence comment before delivery decision.

## Optional tracks

1. Client DB pooler (when DB path is enabled)
   - Confirm detection/fallback logic and read-only route policy.

2. PostgreSQL 17
   - Integration validation against PostgreSQL 17 before production enablement.

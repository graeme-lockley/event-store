# Pipeline Requirements

## Purpose

This document defines the requirements for the CI/CD pipeline to ensure progressive improvement in code quality over time. The pipeline must enforce that test coverage and code quality metrics can only improve or remain stable, preventing regression.

## Scope

This document applies to:
- GitHub Actions CI pipeline (`.github/workflows/ci.yml`)
- Kotlin/JVM modules: `event-store` and `event-store-integration`
- Build configuration files (`build.gradle.kts`)

## Requirements

### REQ-1: Test Execution

**REQ-1.1:** The pipeline MUST execute unit tests for the `event-store` module.

**REQ-1.2:** The pipeline MUST execute integration tests for the `event-store-integration` module.

**REQ-1.3:** Test failures MUST cause the pipeline to fail.

**REQ-1.4:** Test reports (JUnit XML) MUST be uploaded as artifacts with 30-day retention.

**REQ-1.5:** Test logs MUST be uploaded as artifacts with 30-day retention.

**REQ-1.6:** Integration test results MUST be displayed in the pipeline output.

### REQ-2: Test Coverage Measurement

**REQ-2.1:** The pipeline MUST use JaCoCo (Java Code Coverage) for test coverage measurement.

**REQ-2.2:** JaCoCo version MUST be 0.8.11 or later.

**REQ-2.3:** Coverage reports MUST be generated automatically after test execution.

**REQ-2.4:** Coverage reports MUST be generated in XML format for programmatic parsing.

**REQ-2.5:** Coverage reports MUST be generated in HTML format for developer viewing.

**REQ-2.6:** Coverage MUST be measured for the following metrics:
- Line coverage
- Branch coverage
- Instruction coverage
- Method coverage (optional, for reporting)

**REQ-2.7:** Coverage MUST be tracked separately for each module:
- `event-store`
- `event-store-integration`

**REQ-2.8:** Coverage reports (HTML) MUST be uploaded as artifacts with 30-day retention.

**REQ-2.9:** Coverage reports MUST be generated for all Kotlin source files, excluding:
- Build directories (`**/build/**`)
- Generated code (`**/generated/**`)

### REQ-3: Code Linting

**REQ-3.1:** The pipeline MUST use ktlint for Kotlin code style checking.

**REQ-3.2:** ktlint version MUST be 1.1.1 or later.

**REQ-3.3:** Linting MUST be executed before test execution (can run in parallel).

**REQ-3.4:** Linting violations MUST cause the pipeline to fail.

**REQ-3.5:** Linting MUST check all Kotlin source files, excluding:
- Build directories (`**/build/**`)
- Generated code (`**/generated/**`)

**REQ-3.6:** The number of linting violations MUST be tracked and reported.

**REQ-3.7:** ktlint MUST be configured with:
- Verbose output enabled
- Console output enabled
- Failures not ignored
- Experimental rules disabled

### REQ-4: Quality Baseline Tracking

**REQ-4.1:** Quality metrics MUST be stored in a baseline file: `.quality-baseline.json`

**REQ-4.2:** The baseline file MUST be committed to the repository.

**REQ-4.3:** The baseline file MUST store the following metrics:
- Last update timestamp (ISO 8601 format)
- Coverage percentages per module (line, branch, instruction)
- Total number of linting violations

**REQ-4.4:** Baseline file structure MUST follow this schema:
```json
{
  "lastUpdated": "YYYY-MM-DDTHH:mm:ssZ",
  "coverage": {
    "event-store": {
      "line": <number>,
      "branch": <number>,
      "instruction": <number>
    },
    "event-store-integration": {
      "line": <number>,
      "branch": <number>,
      "instruction": <number>
    }
  },
  "linting": {
    "violations": <number>
  }
}
```

**REQ-4.5:** The baseline MUST be initialized with current metrics from the codebase at implementation time.

### REQ-5: Progressive Quality Gates

**REQ-5.1:** The pipeline MUST compare current quality metrics against the baseline.

**REQ-5.2:** Coverage regression rule: Current coverage MUST be greater than or equal to baseline coverage for each metric (line, branch, instruction) and each module.

**REQ-5.3:** Linting regression rule: Current linting violations MUST be less than or equal to baseline violations.

**REQ-5.4:** If any quality metric regresses (coverage decreases or violations increase), the pipeline MUST fail.

**REQ-5.5:** Quality gate comparison MUST be performed after test execution and coverage report generation.

**REQ-5.6:** Quality gate failures MUST provide clear error messages indicating:
- Which metric regressed
- Current value vs. baseline value
- Required action to fix

### REQ-6: Baseline Updates

**REQ-6.1:** When quality metrics improve (coverage increases or violations decrease), the baseline MUST be updated automatically.

**REQ-6.2:** Baseline updates MUST only occur on the `main` branch.

**REQ-6.3:** Baseline updates MUST include a commit with message: `chore: update quality baseline [coverage: X% → Y%, linting: A → B violations]`

**REQ-6.4:** Baseline updates MUST update the `lastUpdated` timestamp.

**REQ-6.5:** Baseline updates MUST be committed and pushed automatically (requires appropriate GitHub Actions permissions).

**REQ-6.6:** Baseline updates MUST NOT occur on pull requests (only on main branch merges).

### REQ-7: Coverage Thresholds

**REQ-7.1:** Initial minimum coverage threshold MUST be set to 0.00% (no minimum enforced at start).

**REQ-7.2:** Long-term target coverage for `event-store` module: 80% (as documented in `event-store/docs/PLAN.md`).

**REQ-7.3:** Long-term target coverage for `event-store-integration` module: 50% (integration tests focus on end-to-end flows, not comprehensive coverage).

**REQ-7.4:** Coverage thresholds MAY be increased over time as baseline improves, but MUST NOT be decreased.

**REQ-7.5:** Coverage thresholds MUST be configurable per module.

### REQ-8: Pipeline Structure

**REQ-8.1:** The pipeline MUST run on:
- Push to `main` branch
- Pull requests to `main` branch

**REQ-8.2:** Pipeline jobs MAY run in parallel where possible (e.g., linting and testing).

**REQ-8.3:** Pipeline execution order MUST be:
1. Setup (Java, Deno, Go)
2. Code checkout
3. Linting check
4. Test execution with coverage
5. Quality gate check
6. Artifact upload
7. Baseline update (main branch only)

**REQ-8.4:** Each pipeline step MUST have clear, descriptive names.

**REQ-8.5:** Pipeline steps MUST provide appropriate error messages on failure.

### REQ-9: Artifact Management

**REQ-9.1:** Test reports (JUnit XML) MUST be uploaded as artifacts.

**REQ-9.2:** Test logs MUST be uploaded as artifacts.

**REQ-9.3:** Coverage reports (HTML) MUST be uploaded as artifacts.

**REQ-9.4:** All artifacts MUST have 30-day retention period.

**REQ-9.5:** Artifact uploads MUST not fail the pipeline if files are not found (use `if-no-files-found: ignore`).

### REQ-10: Build Configuration

**REQ-10.1:** JaCoCo plugin MUST be applied to all Kotlin subprojects.

**REQ-10.2:** ktlint plugin MUST be applied to all Kotlin subprojects.

**REQ-10.3:** Coverage reports MUST be generated automatically after test tasks complete.

**REQ-10.4:** Test tasks MUST be configured to use JUnit Platform.

**REQ-10.5:** Build configuration MUST be shared across subprojects via root `build.gradle.kts`.

### REQ-11: Quality Gate Script

**REQ-11.1:** A quality gate script MUST be created at `.github/scripts/quality-gate.sh`.

**REQ-11.2:** The script MUST parse JaCoCo XML reports to extract coverage percentages.

**REQ-11.3:** The script MUST count ktlint violations.

**REQ-11.4:** The script MUST read the baseline from `.quality-baseline.json`.

**REQ-11.5:** The script MUST compare current metrics against baseline.

**REQ-11.6:** The script MUST exit with error code 1 if quality regressed.

**REQ-11.7:** The script MUST exit with error code 0 if quality maintained or improved.

**REQ-11.8:** The script MUST update the baseline file if metrics improved (on main branch only).

**REQ-11.9:** The script MUST be executable and use bash.

### REQ-12: Developer Experience

**REQ-12.1:** Developers MUST be able to run coverage reports locally: `./gradlew test jacocoTestReport`

**REQ-12.2:** Developers MUST be able to run linting checks locally: `./gradlew ktlintCheck`

**REQ-12.3:** Developers MUST be able to auto-format code locally: `./gradlew ktlintFormat` (optional, not enforced in CI)

**REQ-12.4:** Coverage reports MUST be viewable in HTML format locally.

**REQ-12.5:** Quality gate script MUST be runnable locally for testing.

### REQ-13: Error Handling

**REQ-13.1:** If baseline file is missing, the pipeline MUST fail with clear error message.

**REQ-13.2:** If coverage reports are missing, the pipeline MUST fail with clear error message.

**REQ-13.3:** If JaCoCo XML parsing fails, the pipeline MUST fail with clear error message.

**REQ-13.4:** If baseline update fails (e.g., permission issues), the pipeline MUST log a warning but not fail (to allow manual baseline updates).

### REQ-14: Documentation

**REQ-14.1:** Pipeline requirements MUST be documented in `docs/engineering/PipelineRequirements.md`.

**REQ-14.2:** Quality gate script MUST include inline documentation.

**REQ-14.3:** Build configuration MUST include comments explaining coverage and linting setup.

**REQ-14.4:** README MUST be updated with instructions for running quality checks locally.

## Quality Metrics Thresholds

### Initial Thresholds (At Implementation)

| Metric | Module | Threshold | Notes |
|--------|--------|-----------|-------|
| Line Coverage | event-store | 0.00% | Start with current baseline |
| Branch Coverage | event-store | 0.00% | Start with current baseline |
| Instruction Coverage | event-store | 0.00% | Start with current baseline |
| Line Coverage | event-store-integration | 0.00% | Start with current baseline |
| Branch Coverage | event-store-integration | 0.00% | Start with current baseline |
| Instruction Coverage | event-store-integration | 0.00% | Start with current baseline |
| Linting Violations | All modules | Current count | Start with current baseline |

### Long-term Targets

| Metric | Module | Target | Notes |
|--------|--------|--------|-------|
| Line Coverage | event-store | 80% | As per PLAN.md |
| Branch Coverage | event-store | 75% | Slightly lower than line coverage |
| Instruction Coverage | event-store | 80% | Aligned with line coverage |
| Line Coverage | event-store-integration | 50% | Integration tests focus on flows |
| Branch Coverage | event-store-integration | 45% | Integration tests focus on flows |
| Instruction Coverage | event-store-integration | 50% | Integration tests focus on flows |
| Linting Violations | All modules | 0 | Zero tolerance for style violations |

### Progressive Improvement Rules

1. **Coverage:** Once baseline is established, coverage can only increase or stay the same. Decreases are blocked.

2. **Linting:** Once baseline is established, violations can only decrease or stay the same. Increases are blocked.

3. **Baseline Updates:** When metrics improve, baseline is automatically updated on main branch.

4. **Threshold Increases:** Manual threshold increases can be made, but decreases are not allowed.

## Non-Functional Requirements

### Performance

- Pipeline execution time MUST not increase by more than 5 minutes due to coverage and linting checks.
- Coverage report generation MUST complete within 2 minutes for typical codebase size.
- Linting checks MUST complete within 1 minute for typical codebase size.

### Reliability

- Quality gate script MUST handle edge cases (missing files, malformed JSON, etc.).
- Pipeline MUST be resilient to transient failures (network, file system, etc.).
- Baseline updates MUST be idempotent.

### Maintainability

- Quality gate script MUST be well-documented and maintainable.
- Build configuration MUST be clear and understandable.
- Pipeline steps MUST be modular and testable.

## Compliance

All requirements in this document MUST be implemented and verified. The pipeline MUST fail if any requirement is not met.

## Change Management

Changes to these requirements MUST be:
1. Documented in this file
2. Reviewed and approved
3. Reflected in implementation
4. Tested before deployment

---

**Document Version:** 1.0  
**Last Updated:** 2026-01-24  
**Status:** Active

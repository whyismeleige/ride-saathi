# V1 release integration — 2026-10-01

`v1-release` combines the completed `chore/backend-v1-stabilization` work,
merged into `feat/backend-v1`, with `feat/v1-ui-redesign`. Both merges completed
without conflicts. Backend and Android trees match their respective source
branches, and all three source branches are ancestors of the release branch.

## Validation

- Backend: Ruff passed; **129 tests passed**, including PostgreSQL persistence.
  Fresh migrations, downgrade/upgrade and metadata parity checks passed.
  See [backend stabilization evidence](architecture/backend-stabilization-validation.md).
- Android: debug and QA unit-test tasks passed (57 tests per variant,
  no failures); debug/QA APK builds and QA instrumentation APK compilation passed.
- Debug/QA lint passed. Existing lint warnings remain.
- Compose screenshot validation passed (13 checks; Gradle reused up-to-date
  results for the unchanged Android inputs).
- Map-preview JavaScript regression test passed.
- `git diff --check` and source-branch ancestry/tree comparisons passed.

## Remaining environment/device checks

Release APK assembly was attempted but stops at `processReleaseGoogleServices`:
the release-specific Firebase `google-services.json` is absent. Supply the
appropriate configuration in `android/app/src/release/google-services.json`
or `android/app/google-services.json` before building the release APK.
Debug/QA configuration was not substituted for production configuration.

No connected-device tests or live provider calls were run. Speech, permissions,
location, map loading and Uber handoff still need the hands-on checks described
in [pilot testing](PILOT_TESTING.md).

These are local branch merges; no branches were pushed or deployed.

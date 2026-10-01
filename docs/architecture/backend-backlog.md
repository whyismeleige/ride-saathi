# Backend backlog (post-stabilization)

Only concrete work remaining after the `chore/backend-v1-stabilization` pass.
Items already fixed there (migration parity, persistence tests, OAuth removal,
lint/CI, docs alignment, request-id validation, readiness, shared maps client,
minimal rate limiting) are intentionally NOT listed.

## V1 next

- Onboarding endpoints: create/update user profile, preferred language, and
  `onboarding_completed` transitions honoring `ck_users_completed_name_present`.
- Saved-place CRUD: list/create/update/deactivate Home/work/custom places;
  enforce the one-active-Home index and `(user_id, label)` uniqueness in
  service errors.
- Destination aliases: create/list aliases per place; normalize consistently
  with the `normalized_alias` contract.
- Destination resolution + correction services: persist
  `destination_resolutions` (bounded ≤5 candidates, confidence 0–1) and at most
  one `destination_correction` per resolution; wire `was_confirmed` /
  `was_corrected` transitions.
- Ride session service: intent/session state machine over `RideSessionStatus`
  up to `ready_for_handoff`; snapshot pickup/destination; record
  `SessionEvent` rows (append-only) and one `HandoffEvent` per session.
- Deep-link handoff builder: preferred provider/product selection and Uber
  deep-link construction (no booking APIs, no webhooks, no OAuth).
- First-party authentication (minimal): only when a real client need exists;
  keep the boundary small and do not reintroduce Uber OAuth/credential state.
- Per-feature repositories beside the modules that use them (no generic
  repository framework without a use case).

## Pre-public-beta hardening

- Reverse-proxy throttling in front of `/v1/places/autocomplete` (the in-app
  window is single-instance only).
- Decided `TRUST_PROXY_HEADERS` posture per deployment; default stays `false`.
- Production secret handling review: `OLA_MAPS_API_KEY`, `DATABASE_URL` via
  platform secrets; confirm no dev defaults leak into prod.
- Log review: confirm no queries, addresses, coordinates, tokens, URLs,
  transcripts, or candidate payloads in any new feature's logs.
- Backup/restore runbook for PostgreSQL; migration run as a deployment step
  (never from every worker).
- Load test of the maps proxy through the rate window; tune
  `MAPS_RATE_LIMIT_PER_MINUTE` / `MAPS_RATE_LIMIT_WINDOW_SECONDS`.

## Post-V1 / scale-triggered work

- Destination ranking engine over accumulated resolution/correction memory.
- Streaming STT partials and richer destination interpretation only if measured
  latency/quality requires them. Bounded Azure STT and structured LLM extraction
  are implemented; see [Azure voice pipeline](../AZURE_VOICE_PIPELINE.md).
- Push notifications behind a capability-specific protocol; wait for real
  requirements before creating it.
- Redis (or equivalent) ONLY when a concrete scale trigger demands it —
  never solely for V1 rate limiting.
- Workers/brokers, microservice extraction, Kubernetes, or service mesh ONLY
  when real scaling or ownership needs justify them.
- Verified provider booking-visibility integration ONLY if the product decides
  Ride Saathi must know post-handoff outcomes; until then the backend MUST NOT
  claim a ride was booked.

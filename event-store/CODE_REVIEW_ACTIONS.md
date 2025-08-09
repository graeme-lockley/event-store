## Event Store package — Code Review Action Plan

This document lists prioritized actions to address defects, inconsistencies,
design quality, coding style, and improvements in the `./event-store` package.
Each item includes clear instructions and acceptance criteria.

### Current status

-
  1. [x] Fix server startup (TopicManager instantiation)
-
  2. [x] Remove redundant dispatcher pre-check (avoid double I/O)
-
  4. [x] Tighten router param typing; remove `any` casts
-
  5. [x] Unify behavior for empty event batches
-
  6. [x] Avoid double validation in `storeEvents`
-
  7. [x] Replace UUID generator with crypto-secure function
-
  9. [x] Improve event retrieval performance (date-filter traversal)
- [~] 12) Standardize API error responses and startup logs (startup logs done;
  error structure not yet standardized)
-
  8. [ ] Strengthen JSON typing and AJV validator types
-
  10. [ ] Add basic concurrency control for sequence updates
-
  11. [ ] Harden `registerConsumer` validation
-
  13. [ ] Add cancellation support to client event streaming
-
  14. [ ] Add payload size limits and basic rate limiting
-
  15. [ ] Testing additions and updates

### 1) Fix server startup (TopicManager instantiation)

- **Files**: `event-store/mod.ts`, `event-store/core/topics.ts`
- **Steps**:
  - In `mod.ts`, replace construction with the async factory: use
    `const topicManager = await TopicManager.create();` instead of
    `new TopicManager()`.
  - Ensure top-level `await` is allowed (it already is for `app.listen`).
- **Acceptance**:
  - `deno run -A mod.ts` compiles and starts without constructor access errors.
  - Client integration test boots server successfully.
- **Status**: Completed

### 2) Remove redundant dispatcher pre-check (avoid double I/O)

- **Files**: `event-store/core/dispatcher.ts`
- **Steps**:
  - In `checkAndDeliverEvents`, remove the loop that calls
    `eventManager.getEvents` per consumer to detect “new events”.
  - Either:
    - Always call `await consumerManager.nudgeConsumersForTopic(topic)` each
      tick; or
    - Cache last known latest event id per topic and compare it to
      `await eventManager.getLatestEventId(topic)` once per interval before
      nudging.
- **Acceptance**:
  - Deliveries still occur when events are published.
  - Fewer filesystem reads per interval (measurable via logs or simple
    benchmark).
- **Status**: Completed

### 3) Add retry/backoff instead of unregistering consumers on first failure

- **Files**: `event-store/core/consumers.ts`
- **Steps**:
  - Track per-consumer delivery state (attempt count, next retry time)
    in-memory.
  - On delivery failure, schedule exponential backoff (e.g., 1s, 2s, 4s, cap at
    1m, max N attempts before unregistering or pausing).
  - Log outcomes; do not immediately unregister on the first failure.
- **Acceptance**:
  - Intermittent delivery failures no longer remove consumers permanently.
  - After transient errors, deliveries resume automatically.
- **Status**: Pending

### 4) Tighten router param typing; remove `any` casts

- **Files**: `event-store/api/routes.ts`
- **Steps**:
  - Use router context generics or narrow types so `ctx.params.topic` and
    `ctx.params.id` are typed (remove `(ctx as any)`).
  - Keep handlers typed with consistent request/response shapes.
- **Acceptance**:
  - No TypeScript `any` casts for route params.
  - Type checks pass; behavior unchanged.
- **Status**: Completed

### 5) Unify behavior for empty event batches

- **Files**: `event-store/core/events.ts`, tests as needed
- **Steps**:
  - Decide on consistent behavior. Recommended: reject empty arrays (align with
    API).
  - In `storeEvents`, throw on `eventRequests.length === 0`.
  - Update or add tests to reflect the unified behavior.
- **Acceptance**:
  - Internal and API behavior match for empty arrays (both reject).
- **Status**: Completed

### 6) Avoid double validation in `storeEvents`

- **Files**: `event-store/core/events.ts`
- **Steps**:
  - Perform one pass to validate all events.
  - Extract a `storeEventUnsafe` that assumes validation is done, and call it in
    a second pass to persist events.
- **Acceptance**:
  - Validation is executed once per event; logic still rejects invalid batches.
- **Status**: Completed

### 7) Replace UUID generator with crypto-secure function

- **Files**: `event-store/deps.ts`, `event-store/core/consumers.ts`
- **Steps**:
  - Implement `uuidv4()` using `crypto.randomUUID()`.
  - Ensure consumers use the updated function; no behavior change expected
    beyond randomness quality.
- **Acceptance**:
  - IDs generated are UUIDv4 and cryptographically strong.
- **Status**: Completed

### 8) Strengthen JSON typing and AJV validator types

- **Files**: `event-store/types.ts`, `event-store/utils/validate.ts`
- **Steps**:
  - Introduce
    `type JSONValue = string | number | boolean | null | { [key: string]: JSONValue } | JSONValue[];`.
  - Change `Event.payload` and `EventRequest.payload` to `JSONValue`.
  - Change `Schema.properties` to `Record<string, unknown>` (or a dedicated
    schema type if preferred).
  - In `SchemaValidator`, type the validators map as
    `Map<string, ValidateFunction>` and type inputs as `Schema[]` and
    `unknown | JSONValue`.
- **Acceptance**:
  - Fewer `any` usages; compile-time type-safety improved.
- **Status**: Pending

### 9) Improve event retrieval performance

- **Files**: `event-store/core/events.ts`
- **Steps**:
  - When `query.date` is provided, restrict traversal to
    `join(dataDir, topic, query.date)` instead of walking the full topic tree.
  - Consider creating/maintaining a per-topic/date index file (e.g., JSONL
    manifest) to list event IDs and metadata for faster reads.
  - Optionally group events into chunk files to reduce file-count overhead.
- **Acceptance**:
  - `getEvents` performance improves noticeably for large topics, especially
    with date filters.
- **Status**: Completed (restricted traversal when `date` is provided)

### 10) Add basic concurrency control for sequence updates

- **Files**: `event-store/core/topics.ts`
- **Steps**:
  - Introduce an in-memory per-topic mutex around `getNextEventId` and
    `updateSequence` to avoid lost updates.
  - Optionally implement optimistic concurrency (verify sequence before write)
    or atomic write via temp file + rename.
- **Acceptance**:
  - Parallel publishes to the same topic do not produce duplicate or skipped
    sequence numbers in tests.
- **Status**: Pending

### 11) Harden `registerConsumer` validation

- **Files**: `event-store/core/consumers.ts`, `event-store/api/routes.ts`
  (ensure parity)
- **Steps**:
  - Validate `registration.callback` is a non-empty string and a valid URL.
  - Validate `registration.topics` is a non-empty object when used via API;
    allow empty for internal if desired, but be explicit.
  - Optionally add an (opt-in) callback health probe at registration.
- **Acceptance**:
  - Invalid registrations are rejected with clear errors.
- **Status**: Pending

### 12) Standardize API error responses and startup logs

- **Files**: `event-store/api/routes.ts`, `event-store/mod.ts`
- **Steps**:
  - Use a consistent error structure `{ error: string, code?: string }` across
    handlers.
  - In `mod.ts`, log all endpoints including `GET /topics` and
    `GET /topics/:topic`, and briefly note environment variables (`PORT`,
    `DATA_DIR`, `CONFIG_DIR`).
- **Acceptance**:
  - Errors look uniform; startup logs enumerate all endpoints.
- **Status**: Partially completed (startup logs updated; error response
  standardization pending)

### 13) Add cancellation support to client event streaming

- **Files**: `event-store/client.ts`
- **Steps**:
  - Extend `streamEvents` options to accept an `AbortSignal`.
  - Break the polling loop when `signal.aborted` is true and handle cleanup.
- **Acceptance**:
  - Streaming can be cancelled by callers without lingering loops.
- **Status**: Pending

### 14) Add payload size limits and basic rate limiting

- **Files**: `event-store/mod.ts` (middleware), `event-store/api/routes.ts`
- **Steps**:
  - Enforce max body size for `POST /topics` and `POST /events` (e.g., via Oak’s
    body limit or manual read/guard).
  - Optionally add a simple in-memory rate limiter keyed by IP/route; make
    limits configurable.
- **Acceptance**:
  - Oversized requests and abusive rates are handled gracefully with 4xx
    responses.
- **Status**: Pending

### 15) Testing additions and updates

- **Files**: `event-store/tests/*`
- **Steps**:
  - Add tests for:
    - Dispatcher change (no pre-check) still triggers delivery.
    - Consumer retry/backoff (not removed on first failure).
    - Sequence updates under parallel publish (no duplicates/loss).
    - Router params are typed (no `any`).
    - `getEvents` with `date` filter uses narrower traversal.
  - Update tests if changing empty-batch semantics.
- **Acceptance**:
  - All new tests pass; existing suites remain green.
- **Status**: Pending

## Quick wins (do first)

- Fix `TopicManager.create()` usage in `mod.ts`.
- Replace UUID generator with `crypto.randomUUID()`.
- Simplify dispatcher to avoid duplicate I/O per tick.
- Tighten router param types and remove `any` casts.
- Introduce `JSONValue` and AJV `ValidateFunction` types to replace `any`.

## Post-change checklist

- All TypeScript checks pass; no new linter issues.
- All unit and integration tests pass locally.
- Manual smoke test:
  - Create topic, publish event, register consumer, observe delivery.
  - Verify `GET /topics`, `GET /topics/:topic`, `GET /topics/:topic/events`
    responses.

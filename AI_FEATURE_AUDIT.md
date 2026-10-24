# WiseKoin AI Features Audit and Roadmap

## Purpose

This document records the current state, known problems, recommended architecture, and delivery plan for WiseKoin's receipt scanning, voice transaction entry, and proposed automatic tracking from Android bank notifications.

It covers both repositories:

- Backend: `D:\Projects\WiseKoin`
- Android client: `D:\AndroidStudioProjects\WiseKoin`

The assessment is based on the repository state reviewed on October 23, 2026. Legal and store-policy notes are implementation guidance, not legal advice.

## Executive summary

Receipt and voice entry are functional assisted-entry MVPs. They upload media to Gemini, receive structured fields, allow the user to edit the result, and create a normal transaction only after confirmation. This confirmation boundary is valuable and should remain.

They are not complete production AI systems. The original implementation depended heavily on one hard-coded prompt, did not learn from corrections, had limited semantic validation, and had weak provider-failure handling. Notification-based transaction tracking and direct bank integration are not implemented.

The target design is a layered suggestion engine. AI should propose facts and categories; deterministic application code must validate ownership, type compatibility, amounts, currencies, duplicates, and whether clarification is required. Ambiguous results must remain pending instead of changing wallet balances.

## Current implementation

### Receipt scanning

Current flow:

1. Android captures or selects an image.
2. `POST /api/receipts/extract` uploads the image.
3. The backend loads the authenticated user's expense categories and calls Gemini.
4. Android displays editable merchant, category, amount, and date fields.
5. The user confirms and Android calls the normal transaction creation endpoint.

Strengths:

- The authenticated user determines the available categories.
- Gemini structured output is used rather than parsing unconstrained prose.
- The extraction endpoint does not directly mutate balances.
- Users can correct the result before saving.

### Voice entry

Current flow:

1. Android records an AAC audio stream in an M4A container.
2. `POST /api/voice/extract` uploads the recording.
3. Gemini transcribes and extracts amount, type, category, and note.
4. Android presents an editable result.
5. The user confirms and creates a normal transaction.

Strengths:

- Audio resources are released on primary cleanup paths.
- The backend accepts several common audio formats.
- Financial amounts use `BigDecimal` in the voice contract.
- The user retains control before a balance-changing operation.

### Provider

The backend currently calls the Gemini REST API directly using the model URL configured in `application.properties`. As of this audit, `gemini-3.1-flash-lite` is a stable multimodal model supporting image, audio, and structured outputs.

References:

- [Gemini 3.1 Flash-Lite](https://ai.google.dev/gemini-api/docs/models/gemini-3.1-flash-lite)
- [Gemini structured outputs](https://ai.google.dev/gemini-api/docs/generate-content/structured-output)

## Identified problems

### Contract and correctness

Implementation status is kept here as the source of truth while the work progresses:

| # | Finding | Status |
|---|---|---|
| 1 | Receipt amounts used binary floating point. | **Resolved** — backend and Android use `BigDecimal`. |
| 2 | Receipt `Unknown` was not schema-valid. | **Resolved** — `UNKNOWN` is always available and maps to a nullable category ID. |
| 3 | Voice extraction hard-coded VND and defaulted ambiguity to expense. | **Resolved** — extraction uses the user's base currency and preserves `UNKNOWN`. |
| 4 | Voice categories were not type-safe. | **Resolved** — only income/expense categories are supplied and type compatibility is post-validated. |
| 5 | AI and client flows depended on category display names. | **Resolved** — owned category IDs are the authoritative value; names are display-only. |
| 6 | Receipt extraction omitted date/time, currency, subtotal, tax, discount, tip, payment method, and field-level ambiguity. | **Resolved** — typed fields are extracted, returned, reviewed on Android, and contract-tested. |
| 7 | Voice extraction supported only one transaction and did not model relative dates or transfers. | **Resolved** — batch extraction, relative dates, transfers, sequential review, and explicit wallet selection are implemented. |
| 8 | Structured JSON validated shape but not semantic correctness. | **Resolved** — deterministic amount, precision, currency, date, category/type, transfer, and receipt-arithmetic validation runs after extraction. |
| 9 | Receipt and voice extraction did not detect duplicates before transaction creation. | **Resolved** — user-scoped, bounded duplicate candidates are returned before confirmation. |
| 10 | Category matching did not provide ranked alternatives for clarification. | **Resolved** — up to three owned, type-compatible ID candidates are ranked and displayed. |

### Adaptivity

Implementation status for the adaptive layer:

| # | Finding | Status |
|---|---|---|
| 1 | User corrections were not stored as categorization feedback. | **Resolved** — traceable suggestions and accepted/corrected confirmation outcomes are persisted without raw media, transcripts, or provider payloads. |
| 2 | There was no merchant/counterparty normalization or user-specific merchant rule table. | **Resolved** — deterministic Unicode normalization and user-scoped rules are implemented; rules are created only through an explicit remember choice. |
| 3 | Prompts were embedded in Java and not versioned independently from service code. | **Resolved** — receipt and voice prompts are versioned resources with independently configurable resource locations and version labels. |
| 4 | Prompt/model versions were not recorded with extraction results. | **Resolved** — provider, model, prompt, locale/timezone, rule, and confidence-profile metadata are stored with every suggestion. |
| 5 | There was no provider abstraction for upgrades, fallback, or deterministic test doubles. | **Resolved** — services use `AiExtractionClient`; Gemini is one implementation with configurable primary/fallback endpoints, and extraction orchestration is tested with a fake client. |
| 6 | Locale, timezone, confirmed merchant mappings, and wallet/source mappings were not supplied as controlled context. | **Resolved** — Android supplies device context, the backend validates/defaults it, and bounded owned category/wallet/rule mappings are supplied as untrusted data labels. |
| 7 | A model-generated confidence number would be uncalibrated. | **Resolved** — confidence is derived from deterministic validation signals, identifies its profile, and remains explicitly `calibrated=false` until a representative corpus validates its thresholds. |

### Reliability and security

| # | Finding | Status |
|---|---|---|
| 1 | The shared `RestClient` has no explicit connect or response timeout. | **Resolved** — connect and response timeouts are explicit, positive, environment-configurable durations. |
| 2 | There is no bounded retry policy for rate limits or transient provider failures. | **Resolved** — rate limits, provider 5xx responses, and network/time-out failures use capped exponential backoff with at most five configurable attempts; non-transient 4xx responses are not retried. |
| 3 | There is no circuit breaker or provider health state. | **Resolved** — primary and fallback endpoints have independent failure counters, open windows, single half-open probes, recovery, and queryable health state. |
| 4 | The API key is placed in the request query string instead of a request header, increasing the chance of accidental URL logging. | **Resolved** — Gemini authentication uses only `x-goog-api-key`, with an exact-URL regression test preventing query credentials. |
| 5 | Voice controller failures can log and return the provider response body. | **Resolved** — provider bodies are discarded at the integration boundary, outbound media has a redacted log representation, and clients receive only a stable safe message and code. |
| 6 | Controllers return inconsistent plain-string errors rather than the established global error shape. | **Resolved** — receipt and voice controllers delegate failures to global advice and return JSON `error`/`code` responses consistently. |
| 7 | Uploaded media is read fully into memory and base64 encoding adds further memory overhead. | **Resolved** — backend and Android stream source media instead of making a second full byte-array copy; the backend creates only the bounded base64 representation required by the provider contract. |
| 8 | MIME checks trust the client header or filename and do not verify file signatures/content. | **Resolved** — JPEG, PNG, WebP, and MP4/M4A are detected from their binary structures; a conflicting declared MIME type is rejected. |
| 9 | The 10 MB upload cap does not impose image dimensions, audio duration, or decompression limits. | **Resolved** — independently configurable 5 MB defaults, width/height and 20-megapixel limits, and an MP4 duration limit are enforced before provider calls. |
| 10 | Receipt images and voice recordings need explicit retention and deletion behavior on Android and in any future backend persistence. | **Resolved** — the backend persists no raw media, transcripts, or provider payloads; bounded fingerprint metadata expires after 30 days and cascades on account deletion; Android deletes app-owned capture/recording files on success, failure, reset, replacement, and ViewModel cleanup paths. |
| 11 | Receipt text or audio can contain adversarial instructions. Media content must always be treated as untrusted data. | **Resolved** — versioned prompts explicitly delimit media-derived text and context labels as untrusted data, provider output remains schema-constrained, and deterministic validation—not media instructions—controls accepted values. |
| 12 | Extraction operations have no idempotency key, duplicate fingerprint, or traceable suggestion identifier. | **Resolved** — optional hashed `Idempotency-Key` values, user/source/day-scoped SHA-256 media fingerprints, database uniqueness, serialized request admission, and response `request_id`/`extraction_id` values provide replay control and traceability. |

### Testing and observability

1. **Resolved:** Receipt service and HTTP contract tests are active and cover both valid and ambiguous extraction results.
2. **Resolved:** Voice tests cover currency, type/category compatibility, ambiguity, batches, relative dates, transfers, ranked candidates, and duplicates.
3. **Resolved:** Provider retries, fallback, API-key placement, safe error mapping, and request shape are exercised through deterministic fake-server tests.
4. **Partially resolved:** Focused controller JSON contract coverage exists; full authentication-filter integration coverage for these two endpoints is still recommended.
5. **Partially resolved:** Android DTO/serialization tests cover both extraction contracts; focused Receipt/Voice ViewModel tests are still recommended.
6. **Partially resolved:** A versioned synthetic/de-identified seed corpus now covers Vietnamese receipts, regional currency expressions, synthetic M4A speech (including noise and self-correction), adversarial text, and representative synthetic bank-notification templates. Consented human regional/noisy recordings are still required before acoustic release thresholds can be claimed.
7. There are no quality metrics such as exact amount accuracy, type accuracy, category top-1/top-3 accuracy, clarification rate, duplicate rate, or false auto-save rate.
8. Operational metrics must be added without logging media, transcripts, full provider payloads, account details, or secrets.

### Android UX and lifecycle

1. Receipt and voice screens contain hard-coded user-visible strings.
2. **Resolved:** Voice and receipt review display the currency returned by the authenticated extraction contract.
3. **Resolved:** Android automatically stops voice recording at 55 seconds, leaving a safety margin below the backend's configurable 60-second limit.
4. **Resolved:** App-owned receipt and voice cache files are deleted on completed, failed, cancelled/reset, replacement, capture-error, and ViewModel-cleared paths; gallery-owned content is never deleted.
5. Camera/audio permanent permission denial needs a route to system settings.
6. **Resolved:** Receipt and voice review display explicit ambiguity and possible-duplicate warnings.
7. **Resolved:** Category selection and transaction submission use stable category IDs.
8. **Resolved:** Receipt amounts preserve decimal values through extraction, display, editing, and submission.

## First five weaknesses fixed

This change implements the first five contract/correctness items:

1. Receipt amounts now use `BigDecimal` in backend and Android DTOs.
2. Receipt schemas always include the `UNKNOWN` category token, including when no expense category exists.
3. Voice prompts use the authenticated user's base currency. Invalid or ambiguous types become `UNKNOWN`; they are not defaulted to `EXPENSE`.
4. Voice only supplies income and expense categories, grouped by type. Backend post-processing rejects a category ID whose type does not match the extracted transaction type.
5. Gemini returns a stable category ID token. Backend resolves it against categories owned by the current user and returns `category_id` plus the display name. Android retains and submits the stable ID.

Additional deterministic guards included with these fixes:

- Non-positive AI amounts are rejected.
- Missing Gemini content/parts are rejected with a controlled internal error.
- Unknown or foreign category IDs become an unresolved category rather than being trusted.
- Android blocks saving voice results until the user selects a valid type and matching category.
- Decimal values are no longer truncated for display or parsing.
- Focused backend tests now cover receipt and voice category IDs, unknown results, currency-aware prompts, type mismatches, and positive amounts.
- Android DTO tests cover decimal preservation and ambiguous voice responses.

The JSON changes are additive except for the intended correction from floating-point to decimal handling:

- Receipt response adds `category_id`.
- Voice response adds `currency` and `category_id`.
- Existing `store_name`, `category`, `amount`, `type`, `note`, and `transcript` names remain available.
- Voice `type` can now be `UNKNOWN` when clarification is required.

## Remaining contract/correctness fixes implemented

Items 6–10 are implemented as additive extraction fields and deterministic post-processing:

- Receipt responses now include `currency`, `occurred_at`, `subtotal`, `tax`, `discount`, `tip`, `payment_method`, `ambiguities`, `requires_clarification`, ranked `category_candidates`, and `duplicate_candidates`.
- Receipt totals are checked against `subtotal + tax + tip - discount`. Invalid precision, negative components, impossible dates, currency mismatches, unresolved categories, and arithmetic conflicts are surfaced as field-level ambiguities.
- Voice structured output is now a batch containing up to ten transactions plus one transcript. Relative dates are resolved against the injected application `Clock`; the backend rejects impossible dates instead of trusting them.
- Voice transactions model `EXPENSE`, `INCOME`, `TRANSFER`, and `UNKNOWN`. Transfers carry source/destination wallet hints and require explicit owned-wallet selection in Android before saving.
- Existing top-level voice fields remain populated from the first transaction for backward compatibility; the additive `transactions` array is authoritative for multi-transaction recordings.
- Duplicate detection queries only the authenticated user's transactions in a bounded date window and returns candidates when amount and type match. It does not silently reject or merge a transaction.
- Category alternatives are ordered ID-based candidates. The backend removes foreign, nonexistent, type-incompatible, and duplicate IDs before returning them.
- Android displays ambiguity and duplicate warnings, orders category choices by the ranked candidates, uses extracted dates, reviews multi-transaction recordings sequentially, and supports explicit source/destination wallet selection for transfers.

Verification completed on October 23, 2026:

- Backend focused AI/transaction tests: 24 passed.
- Backend full suite: 67 passed.
- Backend Spotless formatting/check: passed.
- Android unit tests, `lintDebug`, and `assembleDebug`: passed.

## Adaptivity fixes implemented

The adaptive layer now follows the same pending-suggestion boundary as the rest of the AI flow:

- Migration `V9__add_adaptive_ai_feedback.sql` adds `ai_extractions`, append-only `ai_feedback`, and user-scoped `categorization_rules`. Suggestion ownership is enforced during confirmation and concurrent double-confirmation is guarded with a database write lock.
- Receipt and each voice transaction return an additive `extraction_id`. Android sends that ID with the normal transaction request so feedback is committed in the same transaction as the confirmed expense.
- Accepted and corrected category/type decisions are retained as feedback. A merchant rule is only created or changed when the user selects **Remember this merchant and category**; the default remains off.
- Merchant/counterparty keys are normalized deterministically across case, punctuation, whitespace, Vietnamese combining marks, and `đ`/`Đ`. A learned rule is applied only when its category still exists, belongs to the user, and matches the transaction type.
- Prompts moved to `receipt-v2.txt` and `voice-v2.txt`. Resource paths and version labels are deployment configuration, and the used prompt/provider/model versions are attached to and stored with every suggestion.
- `ReceiptService` and `VoiceService` depend on the provider-neutral `AiExtractionClient`. The Gemini implementation sends the API key in a header, supports a configured fallback endpoint/model, and returns provider metadata separately from extracted JSON.
- Android sends its BCP 47 locale and IANA timezone. Voice context includes stable IDs for owned wallets, allowing clearly spoken source/destination wallets to be preselected without trusting display-name matching.
- Confidence is an application score based on semantic ambiguities, possible duplicates, and confirmed-rule matches. It is not the model's self-reported probability. The response includes score, level, reasons, profile, and calibration state.
- Stored adaptive records intentionally exclude images, audio, transcripts, full notes, provider responses, and provider payloads.

Additive API fields introduced by this layer:

- Extraction responses: `extraction_id`, `prompt_version`, `model_version`, `provider`, `locale`, `time_zone`, `rule_applied`, and `confidence`.
- Voice transaction responses: `counterparty`, `source_wallet_id`, and `destination_wallet_id`.
- Transaction create requests: optional `aiExtractionId` and `rememberAiCategory`.
- Receipt/voice multipart requests: optional `locale` and `time_zone`; older clients continue to use configured defaults.

Adaptivity verification completed on October 23, 2026:

- Backend adaptive-focused tests: 10 passed.
- Backend clean full suite: 76 passed.
- Backend Spotless apply/check: passed.
- Android unit tests: passed, including adaptive DTO and confirmation serialization coverage.
- Android `lintDebug` and `assembleDebug`: passed.

## Reliability and security fixes implemented

The remaining media-ingestion and replay controls now sit in front of both receipt and voice provider calls:

- `AiMediaIngestionService` enforces separate image/audio byte limits while streaming, detects supported formats from binary headers, rejects declared-type mismatches, bounds image dimensions and decoded pixel count, and reads MP4 movie metadata to enforce recording duration.
- The Android receipt request body streams from its content URI. App-owned camera files and voice recordings have explicit deletion on all normal, error, reset, replacement, and ViewModel cleanup paths; Android stops recording at 55 seconds before the backend's default 60-second ceiling.
- Raw media is transient and is not added to backend persistence. `ai_ingestion_requests` contains only a one-way idempotency-key hash, media SHA-256 fingerprint, lifecycle status, and trace IDs. Rows expire after the configurable 30-day retention window and are deleted when their user is deleted.
- Receipt and voice prompts identify context labels and media-derived content as untrusted data. Structured provider output is still subjected to the deterministic ownership, amount, currency, date, category/type, transfer, duplicate, and receipt-arithmetic checks documented above.
- Clients may send `Idempotency-Key`. The backend serializes admission per authenticated user and enforces unique key and daily fingerprint constraints. A concurrent/in-flight or completed replay returns HTTP 409 with `AI_EXTRACTION_DUPLICATE` and the original `request_id` plus `extraction_id` when available. Failed or stale requests can be retried safely.
- Successful extraction responses include additive `request_id` and `extraction_id` fields. The Android repository generates a new UUID idempotency key for each user-initiated receipt or voice upload.

New operational configuration:

- `AI_IMAGE_MAX_BYTES`, `AI_IMAGE_MAX_WIDTH`, `AI_IMAGE_MAX_HEIGHT`, and `AI_IMAGE_MAX_PIXELS`
- `AI_AUDIO_MAX_BYTES` and `AI_AUDIO_MAX_DURATION`
- `AI_INGESTION_PROCESSING_TIMEOUT`, `AI_INGESTION_METADATA_RETENTION`, and `AI_INGESTION_CLEANUP_CRON`

Reliability/security verification completed on October 24, 2026:

- Focused media-ingestion tests cover streaming without `MultipartFile.getBytes()`, stable fingerprints, MIME mismatch, excessive dimensions, valid MP4/M4A duration, excessive duration, disguised audio, and excessive MP4 container nesting.
- Ingestion-request tests cover hashed keys, traceable duplicate conflicts, and retention cleanup.
- Prompt regression tests cover the untrusted-data boundary.
- Controller and exception-handler tests cover idempotency-key forwarding and the duplicate response contract.
- Backend full suite: 93 passed; backend Spotless check passed.
- Android `testDebugUnitTest`, `lintDebug`, and `assembleDebug`: passed.

## Next recommended work

With contract/correctness, adaptivity, and the listed reliability/security findings resolved, the next milestone should be measurable release readiness rather than additional prompt complexity:

1. Add authentication-filter integration tests for both extraction endpoints: missing/invalid JWT, authenticated success, user isolation, multipart rejection, and 409 replay behavior through the real security chain.
2. Add focused Android Receipt/Voice ViewModel tests for upload failure, cancellation, duplicate responses, automatic duration stop, sequential batch review, and cache-file cleanup. Move remaining AI-screen strings into resources and add the permanent-denial route to system settings.
3. **Seed corpus complete:** Maintain and expand the de-identified Vietnamese corpus under `evaluation/vietnamese-ai-corpus`; add access-controlled, consented human regional/noisy speech before setting acoustic release thresholds. Production logs and sensitive media remain prohibited as corpus sources.
4. Define release thresholds and measure exact amount/currency/type accuracy, category top-1/top-3 accuracy, clarification rate, duplicate false-positive rate, and false balance-change rate by prompt/model version.
5. Add privacy-safe operational metrics for provider latency, retries, circuit state, validation failures, ambiguity reasons, duplicate conflicts, and manual fallback. Do not label metrics with media, transcript, merchant, account, idempotency key, or user identifiers.
6. **Completed:** Pending-suggestion read/list, confirmation, rejection, and clarification APIs are user-scoped and audited. Confirmation delegates to the existing transactional creation path. Automatic transaction creation remains out of scope until the release gates below are met.

## Recommended layered suggestion engine

### Layer 1: ingestion and normalization

- Bound file size, image dimensions, and audio duration.
- Verify magic bytes/content rather than trusting extensions.
- Correct EXIF rotation and detect unreadable, dark, blurred, or cropped receipts.
- Normalize bank/merchant identifiers without retaining unnecessary raw content.
- Assign an idempotency key and source fingerprint before processing.

### Layer 2: fact extraction

Return typed facts rather than a ready-to-save transaction:

- amount as a decimal string/`BigDecimal`;
- ISO 4217 currency;
- transaction direction: `EXPENSE`, `INCOME`, `TRANSFER`, or `UNKNOWN`;
- merchant/counterparty;
- occurred-at date/time with timezone;
- source text/transcript where the user has consented to retain it;
- ambiguity reasons and missing fields;
- model and prompt version.

Receipt-specific facts should include subtotal, tax, discounts, tip, and total so the application can check arithmetic consistency.

### Layer 3: deterministic validation

- Require a positive amount with supported precision and scale.
- Validate currency against the user/wallet configuration.
- Validate every wallet and category ID as owned by the authenticated user.
- Enforce category type compatibility.
- Reconcile receipt subtotal/tax/discount/tip with the total when available.
- Reject impossible dates and cap future dates.
- Detect duplicates using source, amount, time bucket, reference hash, and merchant/counterparty.
- Never silently convert `UNKNOWN` to a balance-changing transaction type.

### Layer 4: personalized categorization

Categorize in this order:

1. Explicit user rule for a normalized merchant/counterparty.
2. Previous confirmed corrections for that user.
3. Deterministic bank/merchant mapping.
4. AI classification using only owned category IDs and minimal relevant context.
5. Clarification when no option reaches a calibrated threshold.

User feedback should create or update an explainable rule only with the user's knowledge. Fine-tuning is not required for the first adaptive version.

### Layer 5: pending suggestion and clarification

AI extraction should create a pending suggestion, not a transaction. A suggested backend shape is:

```json
{
  "id": "suggestion-id",
  "source": "RECEIPT|VOICE|BANK_NOTIFICATION",
  "amount": "125000.00",
  "currency": "VND",
  "type": "EXPENSE|INCOME|TRANSFER|UNKNOWN",
  "merchant": "Normalized merchant",
  "occurredAt": "2026-10-23T12:00:00+07:00",
  "categoryCandidates": [
    {"categoryId": 12, "score": "0.94"}
  ],
  "requiresClarification": true,
  "ambiguityReasons": ["PURPOSE_UNKNOWN"],
  "status": "PENDING"
}
```

Suggested entities/modules:

- `transaction_suggestions`: pending/confirmed/rejected/expired lifecycle and source fingerprint.
- `categorization_rules`: normalized merchant/source to owned category ID.
- `suggestion_feedback`: original proposal and user correction without storing unnecessary provider payloads.
- `AiExtractionClient`: provider-neutral extraction interface.
- Versioned prompt resources and typed provider request/response models.

Only confirmation should call the existing transactional `TransactionService`, ensuring existing wallet, goal, debt, and event behavior remains authoritative. Auto-save should be a later, explicit opt-in limited to proven rules, idempotent, auditable, and reversible.

## Android bank-notification tracking

### Feasibility

Android can receive posted-notification callbacks through `NotificationListenerService` after the user explicitly enables notification access in system settings.

Reference: [Android NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)

This feature is not currently present. The Android app has no notification listener service, `POST_NOTIFICATIONS` permission, WorkManager pipeline, Firebase Messaging dependency, bank parser registry, or notification clarification UI. The backend has no device-token, notification-ingestion, pending-suggestion, or push service.

### Recommended device flow

```text
Allowlisted bank/e-wallet notification
  -> verify source package/signing identity
  -> persist minimal event locally
  -> bank-specific deterministic parser
  -> redact account/name/reference data not needed for classification
  -> deduplicate
  -> map source account to an owned WiseKoin wallet
  -> apply user rules/history
  -> optional authenticated backend AI classification
  -> pending suggestion
  -> local actionable notification
  -> Confirm / Change / Ignore
  -> normal transaction creation endpoint
```

Use a local notification for immediate clarification on the same device. Firebase Cloud Messaging is only needed if backend processing is asynchronous, the app process cannot finish the flow reliably, or another registered device must be notified.

Example clarification:

> What was the 250,000 VND transfer to Nguyen Van A for?
>
> Dining | Shopping | Internal transfer | Review

### Required safeguards

- Explain the feature before opening Android's notification-access settings.
- Make monitoring opt-in, revocable, and limited to an allowlist of supported bank/e-wallet packages.
- Ignore OTP, authentication, security, messaging, and unrelated notifications.
- Prefer on-device deterministic parsing and redaction.
- Never upload or log all notifications.
- Store only fields required for expense tracking and delete raw notification text quickly.
- Encrypt persisted sensitive data and use HTTPS for any server processing.
- Support notification template versions per bank.
- Treat missed, disabled, hidden, or generic notifications as expected limitations.
- Handle notification updates and duplicates idempotently.
- Require users to map a bank account/source to a WiseKoin wallet.
- Model incoming funds, expenses, and internal transfers separately.

Android 15 redacts detected OTP contents from untrusted notification listeners. WiseKoin should not attempt to work around this restriction.

Reference: [Android 15 notification-listener protections](https://developer.android.com/about/versions/15/behavior-changes-all)

## Direct bank integration

Direct read-only bank integration is a better long-term source than notification parsing because it can provide normalized transaction histories, stable identifiers, reconciliation, and recovery after missed device events. It is commercially and operationally harder, but it is not inherently prohibited.

Vietnam Circular 64/2024/TT-NHNN defines customer-consent, account-list, account-information, and transaction-history Open APIs. It also requires bank/third-party contracts, purpose limitation, security commitments, and access controls. Actual availability and onboarding remain bank-specific.

References:

- [Circular 64/2024/TT-NHNN](https://congbaocdn.chinhphu.vn/CongBaoCP/VanBan/2024/12/44057/54671-1-2025301-30264-2024-tt-nhnn.pdf)
- [Vietnam Personal Data Protection Law No. 91/2025/QH15](https://vanban.chinhphu.vn/?classid=1&docid=214590&pageid=27160&typegroup=)
- [Decree 356/2025/ND-CP](https://congbao.chinhphu.vn/van-ban/nghi-dinh-so-356-2025-nd-cp-468371/61065.htm)

Notification monitoring does not avoid privacy obligations. Bank transaction content is sensitive financial data. Google Play requires prominent disclosure, affirmative consent, data minimization, secure handling, a valid privacy policy, accurate Data Safety declarations, deletion support, and accountability for third-party AI processing.

Reference: [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)

## Delivery plan

### Phase 1: harden current extraction

- [x] Complete all ten contract/correctness fixes listed above.
- [x] Add HTTP timeouts, safe provider error mapping, bounded retry, and header-based API authentication.
- [x] Verify file signatures and impose media duration/dimension limits.
- [x] Move prompts to versioned resources and introduce `AiExtractionClient`.
- [ ] Complete authentication-filter integration coverage; provider fake-server and focused controller coverage are complete.
- [x] Stop logging or returning provider payloads.

### Phase 2: suggestions and learning

- [x] Add append-only migrations for extraction feedback, categorization rules, and ingestion request metadata.
- [x] Return dates, currencies, ambiguity reasons, and candidate categories.
- [x] Add confirmation/rejection/clarification endpoints scoped to the authenticated user.
- [x] Learn explainable merchant/category rules from confirmed corrections.
- [x] Add pre-confirmation duplicate detection.
- [x] Add persisted extraction idempotency keys and source fingerprints.

### Phase 3: notification pilot

- Implement consent and notification-access onboarding.
- Add a parser registry and support a small, explicitly tested bank allowlist.
- Parse/redact locally and create pending suggestions.
- Add actionable local notifications and deep links into clarification UI.
- Measure parser coverage, false positives, duplicate rate, and missed-event behavior.

### Phase 4: bank API partnerships

- Start with read-only account and transaction-history access.
- Complete bank-specific contractual, security, consent, retention, audit, and incident-response requirements.
- Reconcile API transactions with notification-derived suggestions using bank reference IDs.
- Prefer the bank API as authoritative when both sources describe the same transaction.

## Release gates

Do not enable automatic transaction creation until all of the following are true:

- Accuracy is measured on a representative, consented test corpus.
- False balance-changing actions meet an explicitly approved threshold.
- Suggestions are idempotent and reversible.
- Ownership and wallet/category validation are enforced server-side.
- Users have explicit per-feature and per-source controls.
- Privacy policy, Data Safety disclosure, retention, deletion, and incident response are complete.
- Provider failures degrade to manual entry without losing user data.
- Android device testing covers supported OS versions, OEM background behavior, permission denial, process death, offline recovery, and large font/small-screen layouts.

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

1. User corrections are not stored as categorization feedback.
2. There is no merchant/counterparty normalization or user-specific merchant rule table.
3. The prompt is embedded in Java and is not versioned independently from service code.
4. There is no prompt/model version recorded with an extraction result.
5. There is no provider abstraction for model upgrades, fallback, or deterministic test doubles.
6. Relevant context such as locale, timezone, prior confirmed merchant mappings, and wallet/source mappings is not supplied in a controlled form.
7. A model-generated confidence number would not be calibrated. Confidence must combine measurable application signals and evaluation results.

### Reliability and security

1. The shared `RestClient` has no explicit connect or response timeout.
2. There is no bounded retry policy for rate limits or transient provider failures.
3. There is no circuit breaker or provider health state.
4. The API key is placed in the request query string instead of a request header, increasing the chance of accidental URL logging.
5. Voice controller failures can log and return the provider response body.
6. Controllers return inconsistent plain-string errors rather than the established global error shape.
7. Uploaded media is read fully into memory and base64 encoding adds further memory overhead.
8. MIME checks trust the client header or filename and do not verify file signatures/content.
9. The 10 MB upload cap does not impose image dimensions, audio duration, or decompression limits.
10. Receipt images and voice recordings need explicit retention and deletion behavior on Android and in any future backend persistence.
11. Receipt text or audio can contain adversarial instructions. Media content must always be treated as untrusted data.
12. Extraction operations have no idempotency key, duplicate fingerprint, or traceable suggestion identifier.

### Testing and observability

1. **Resolved:** Receipt service and HTTP contract tests are active and cover both valid and ambiguous extraction results.
2. **Resolved:** Voice tests cover currency, type/category compatibility, ambiguity, batches, relative dates, transfers, ranked candidates, and duplicates.
3. Provider calls are not exercised through a deterministic fake server.
4. **Partially resolved:** Focused controller JSON contract coverage exists; full authentication-filter integration coverage for these two endpoints is still recommended.
5. **Partially resolved:** Android DTO/serialization tests cover both extraction contracts; focused Receipt/Voice ViewModel tests are still recommended.
6. There is no representative evaluation corpus for Vietnamese receipts, speech, currency expressions, or bank notification templates.
7. There are no quality metrics such as exact amount accuracy, type accuracy, category top-1/top-3 accuracy, clarification rate, duplicate rate, or false auto-save rate.
8. Operational metrics must be added without logging media, transcripts, full provider payloads, account details, or secrets.

### Android UX and lifecycle

1. Receipt and voice screens contain hard-coded user-visible strings.
2. **Resolved:** Voice and receipt review display the currency returned by the authenticated extraction contract.
3. Voice recording has no maximum duration and can exceed useful/provider limits.
4. Cached media deletion is incomplete on every lifecycle and failure path.
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
- Add HTTP timeouts, safe provider error mapping, bounded retry, and header-based API authentication.
- Verify file signatures and impose media duration/dimension limits.
- Move prompts to versioned resources and introduce `AiExtractionClient`.
- Add provider fake-server and controller/security tests.
- Stop logging or returning provider payloads.

### Phase 2: suggestions and learning

- Add append-only migrations for suggestions, feedback, and categorization rules.
- [x] Return dates, currencies, ambiguity reasons, and candidate categories.
- Add confirmation/rejection/clarification endpoints scoped to the authenticated user.
- Learn explainable merchant/category rules from confirmed corrections.
- [x] Add pre-confirmation duplicate detection.
- [ ] Add persisted suggestion idempotency keys and source fingerprints.

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

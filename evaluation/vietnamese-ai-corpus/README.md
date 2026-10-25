# Vietnamese AI evaluation corpus

This directory is a privacy-safe seed corpus for WiseKoin receipt, voice, and bank-notification evaluation. Checked-in payload values are synthetic or de-identified and contain no production logs, customer media, real account identifiers, balances, references, names, phone numbers, or provider payloads.

## What is included

- Deterministically rendered Vietnamese receipt images with exact ground truth, including clean, faded, cropped, arithmetic-conflict, and adversarial-instruction cases.
- Synthetic Vietnamese M4A speech plus exact transcript sidecars covering northern, central, and southern currency expressions, relative dates, transfers, multiple transactions, and a noisy/correction scenario.
- Synthetic representative bank-notification templates for negative, adversarial, and semantic parser work.
- Public-format-derived MB, Vietcombank, and Techcombank templates. They preserve only observed labels, separators, field order, and sign conventions; every payload value is replaced. Provenance URLs and access dates live beside each case in `manifest.json`.

`PUBLIC_FORMAT_DERIVED` means the structure was observed in a public document, screenshot transcription, or open-source parser. It does **not** mean the bank certified the fixture or that the format is current. The corpus deliberately has no public-format-derived MoMo fixture yet because an exact, attributable Android notification body was not found. Add one only from a consented capture or a stable public source.

Checked-in speech is synthetic TTS. It may be used for end-to-end regression and semantic extraction tests, but **must not** be used to claim representative human speech, dialect, or acoustic-noise accuracy. Real recordings belong in an access-controlled corpus outside this repository and must use the same manifest schema with `provenance.kind=CONSENTED` and a non-empty consent record ID.

## Layout

- `manifest.json` — corpus cases and expected typed facts.
- `manifest.schema.json` — the portable manifest contract.
- `assets/receipts` — generated synthetic receipt PNGs.
- `assets/speech` — generated synthetic Vietnamese M4A recordings.
- `assets/speech-transcripts` — exact transcript sidecars.
- `assets/bank-notifications/public-derived` — de-identified format-derived fixtures with provenance in the manifest.
- `assets/bank-notifications` — synthetic notification text used for safety and negative cases.
- `source/receipts` — exact receipt text used by the deterministic renderer.
- `tools/Generate-SyntheticReceipts.ps1` — reproducible receipt renderer.
- `tools/Generate-SyntheticSpeech.ps1` — optional synthetic TTS/M4A generator; it sends only the checked-in synthetic sentences to the configured TTS service.
- `CONSENT_RECORD.example.json` — required metadata shape for separately controlled human-contributed media.
- `BANK_NOTIFICATION_CASE.example.json` — copy-ready manifest shape for a new bank/e-wallet format.

## Privacy and consent rules

1. Never copy production requests, logs, receipt images, notification text, or transcripts into this directory.
2. Synthetic cases use fictitious merchants and masked account fragments. Do not use real phone numbers, emails, names, loyalty IDs, tax IDs, transaction references, addresses, QR payloads, or full account/card numbers.
3. Human-contributed media requires explicit evaluation consent, documented allowed uses, a withdrawal path, and a retention date. Keep the media and consent record in the approved restricted store; commit only a de-identified manifest entry if policy permits.
4. Assign a random corpus subject key. Do not place the contributor's app user ID in filenames or manifests.
5. De-identification must be reviewed by a second person before a case is admitted.
6. `TRANSCRIPT_PROXY` and synthetic TTS data cannot satisfy acoustic release thresholds.

## Adding another bank or e-wallet format

1. Capture only with the account holder's explicit evaluation consent, or use a stable public source. Record the source URL/access date or restricted consent-record ID before editing the payload.
2. Replace the account/card number, balance, person or merchant name, phone, transaction/reference ID, free-text memo, amount, and timestamp. Keep only syntax needed to evaluate parsing.
3. Save one transaction per UTF-8 text asset under `assets/bank-notifications/<source-id>/`. Do not commit screenshots or raw notification exports.
4. Copy `BANK_NOTIFICATION_CASE.example.json` into `manifest.json`; give the source/package/channel/template version explicit values. Add separate cases for income, expense, grouping separators, and each materially different template version.
5. Add the verified Android package and parser version to both the Android parser registry and backend `BankNotificationSource` allowlist. A corpus case alone must never expand production access.
6. Add parser tests, run the corpus privacy test, and have a second person review the de-identification before merging.

## Validation and generation

From the backend repository root:

```powershell
.\evaluation\vietnamese-ai-corpus\tools\Generate-SyntheticReceipts.ps1
.\mvnw.cmd -Dtest=VietnameseAiCorpusTest test
```

The test checks asset and transcript presence, unique IDs, required coverage tags, provenance/de-identification flags, safe relative paths, typed monetary ground truth, and common direct-identifier patterns.

## Metric eligibility

Use `eligible_metrics` exactly as declared per case. Receipt images may participate in end-to-end receipt extraction metrics. Synthetic TTS is eligible for regression checks, not representative human-speech release thresholds. Bank templates participate only after a deterministic parser for the named source/template version exists. SMS cases describe real format families but are not evidence that the Android notification-listener integration can receive SMS. Report corpus version, parser version, and the number of eligible cases with every result.

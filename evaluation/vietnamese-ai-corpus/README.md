# Vietnamese AI evaluation corpus

This directory is a privacy-safe seed corpus for WiseKoin receipt, voice, and future bank-notification evaluation. All checked-in cases are synthetic and contain no production logs, customer media, real account identifiers, or provider payloads.

## What is included

- Deterministically rendered Vietnamese receipt images with exact ground truth, including clean, faded, cropped, arithmetic-conflict, and adversarial-instruction cases.
- Synthetic Vietnamese M4A speech plus exact transcript sidecars covering northern, central, and southern currency expressions, relative dates, transfers, multiple transactions, and a noisy/correction scenario.
- Synthetic representative bank-notification templates for semantic parser work. These are examples only and are not asserted to be current bank production templates.

Checked-in speech is synthetic TTS. It may be used for end-to-end regression and semantic extraction tests, but **must not** be used to claim representative human speech, dialect, or acoustic-noise accuracy. Real recordings belong in an access-controlled corpus outside this repository and must use the same manifest schema with `provenance.kind=CONSENTED` and a non-empty consent record ID.

## Layout

- `manifest.json` — corpus cases and expected typed facts.
- `manifest.schema.json` — the portable manifest contract.
- `assets/receipts` — generated synthetic receipt PNGs.
- `assets/speech` — generated synthetic Vietnamese M4A recordings.
- `assets/speech-transcripts` — exact transcript sidecars.
- `assets/bank-notifications` — synthetic notification text.
- `source/receipts` — exact receipt text used by the deterministic renderer.
- `tools/Generate-SyntheticReceipts.ps1` — reproducible receipt renderer.
- `tools/Generate-SyntheticSpeech.ps1` — optional synthetic TTS/M4A generator; it sends only the checked-in synthetic sentences to the configured TTS service.
- `CONSENT_RECORD.example.json` — required metadata shape for separately controlled human-contributed media.

## Privacy and consent rules

1. Never copy production requests, logs, receipt images, notification text, or transcripts into this directory.
2. Synthetic cases use fictitious merchants and masked account fragments. Do not use real phone numbers, emails, names, loyalty IDs, tax IDs, transaction references, addresses, QR payloads, or full account/card numbers.
3. Human-contributed media requires explicit evaluation consent, documented allowed uses, a withdrawal path, and a retention date. Keep the media and consent record in the approved restricted store; commit only a de-identified manifest entry if policy permits.
4. Assign a random corpus subject key. Do not place the contributor's app user ID in filenames or manifests.
5. De-identification must be reviewed by a second person before a case is admitted.
6. `TRANSCRIPT_PROXY` and synthetic TTS data cannot satisfy acoustic release thresholds.

## Validation and generation

From the backend repository root:

```powershell
.\evaluation\vietnamese-ai-corpus\tools\Generate-SyntheticReceipts.ps1
.\mvnw.cmd -Dtest=VietnameseAiCorpusTest test
```

The test checks asset and transcript presence, unique IDs, required coverage tags, provenance/de-identification flags, safe relative paths, typed monetary ground truth, and common direct-identifier patterns.

## Metric eligibility

Use `eligible_metrics` exactly as declared per case. Receipt images may participate in end-to-end receipt extraction metrics. Synthetic TTS is eligible for regression checks, not representative human-speech release thresholds. Bank templates participate only after a deterministic parser for the named synthetic template family exists. Report corpus version, prompt version, model version, and the number of eligible cases with every result.

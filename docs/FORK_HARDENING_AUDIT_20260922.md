# Fork Hardening Audit — 2026-09-22

Repository: `EmilioJI/jev-chat-jarvis`  
Upstream baseline: `jev-chat/jev-chat-jarvis@d8720521fa13aa5172a891cba95c5dd8aeb44a11`  
Working branch: `feature/hardening-v1`

## Scope

This pass intentionally preserves the upstream product model:

- Android AccessibilityService capture
- per-app adapters for WeChat / QQ / X / Feishu
- local ML Kit OCR fallback
- Jev decision route + generative reply route
- floating overlay
- user-confirmed fill only; no automatic send

The pass focuses on correctness, privacy, security, and unnecessary event load. It does **not** rebrand the app, replace Jev, or redesign the UI.

## P0 findings fixed in this branch

### 1. Stale async results could cross conversations

Before this pass, analysis for conversation A could still complete after the user switched to B and repopulate the overlay. A stale candidate's fill callback could then target the currently visible input field.

Fix:

- every analysis now carries an `AnalysisSession`
- a monotonic `analysisEpoch` invalidates prior asynchronous work
- judgment/reply callbacks publish only when the session is still current
- fill re-reads the active window before writing, and re-checks after keyboard transitions
- switching package/content invalidates pending analysis

### 2. Reply-before-judgment race

Judgment and candidate generation run concurrently. Previously, if replies completed first, `OverlayController.showReplies()` had no `lastJudgment` and silently discarded the replies.

Fix:

- candidate results are retained until the judgment has been published
- publishing is coordinated on the main thread

### 3. Conversation identity omitted the title

`ChatSnapshot.signature()` previously used only recent message side/text. Two chats with identical recent messages could collide.

Fix:

- conversation title is now part of the snapshot identity

### 4. Transient titles could inherit another chat's title

The old `lastGoodTitle` cache was package-wide. Opening chat B while its title was still “Connecting…” could inherit chat A's title and therefore the wrong contact/history/whitelist context.

Fix:

- cached titles now carry recent message evidence
- a transient title is reused only when recent message evidence overlaps

### 5. Conversation title leaked to logcat

The app stated that chat text was not logged, but the snapshot debug line printed the conversation title.

Fix:

- log only title length and message lengths/sides

### 6. Provider error bodies could be mirrored into logcat

`JudgeClient` logged `e.message`. `ApiException.message` can contain a provider response-body snippet, and a provider may echo request material.

Fix:

- log only exception class and HTTP status
- keep actionable error detail only in the user-facing UI

### 7. Accessibility service subscribed to all event types

The XML requested `typeAllMask` while the service handles only:

- `TYPE_WINDOW_STATE_CHANGED`
- `TYPE_WINDOW_CONTENT_CHANGED`
- `TYPE_VIEW_SCROLLED`

Fix:

- subscribe only to these three event classes

### 8. Authenticated model requests followed redirects by default

`HttpURLConnection` follows redirects by default. Model requests carry both a Bearer key and chat material.

Fix:

- disable automatic redirect following
- surface a 3xx response as an explicit HTTP failure

## P1 — required before treating this fork as a production app

### Secure API-key storage

Current API keys are stored in app-private `SharedPreferences`. This prevents ordinary cross-app reads but is not encrypted-at-rest key storage.

Recommended migration:

1. add an Android Keystore-backed secret store
2. encrypt judge/reply/vision keys
3. migrate legacy plaintext only after decrypt-back validation
4. preserve a recovery path if a Keystore key becomes invalid
5. never delete a legacy value until migration is verified

Do not use deprecated `EncryptedSharedPreferences` as the long-term architecture.

### Google Play AccessibilityService compliance

The current service metadata declares:

`android:isAccessibilityTool="true"`

The product is a general chat assistant, not primarily a disability-access tool. Before Google Play distribution, this needs an explicit compliance design:

- correct AccessibilityService declaration
- prominent in-app disclosure
- affirmative consent before enabling the service
- accurate Play Console declaration
- regression test whether changing the declaration affects WeChat capture behavior

This is deliberately not changed in this pass because it can alter platform/WeChat behavior and requires a distribution decision.

### Android 16 / target API 36

Current build:

- `compileSdk = 35`
- `targetSdk = 35`

For Google Play submissions after 2026-08-31, new apps and app updates must target API 36. Upgrade as a tested build-chain migration, not as a one-line edit: AGP, Gradle, compileSdk, targetSdk, foreground-service behavior, notification behavior and accessibility behavior all need verification.

### Persistent chat-history ambiguity

`KbStore.appendLog()` treats a visible screen with zero overlap against the previous recorded screen as “scrolled into old history” and skips it.

That is safe against duplicate history, but after a long absence an entirely new visible screen can also have zero overlap. This can cause genuine new messages to be skipped.

Recommended fix:

- pass a capture reason / direction hint into history recording
- incoming-message analysis may append a no-overlap screen as new
- manual historical scrolling should remain non-appending
- add deterministic sequence tests before changing this logic

### Dead / incomplete configuration paths

Verified current state:

- `Prefs.ocrEngine` exists but does not control the live OCR path
- live OCR always uses bundled ML Kit
- `VisionClient` is currently exercised by the settings connectivity test, not the capture pipeline
- `Prefs.autoSummary` exists but automatic summary generation is not wired
- `ReplyClient.summarize()` exists but is not called by the live context path

Before the next public release, either wire these features end-to-end or remove/hide the inactive controls and fields.

## P2 — product experiments, not mandatory hardening

### Jev + LLM versus one structured-output model

Do not decide this by architecture preference alone. Benchmark both pipelines on the same labeled conversation set:

A. Jev judgment + LLM drafts + Jev rank  
B. one strong LLM call returning structured judgment + three candidates  
C. one LLM generation call + lightweight reranker

Measure:

- end-to-end p50/p95 latency
- cost per analyzed message
- intent / action accuracy
- candidate preference win rate
- JSON/schema failure rate
- Chinese conversational naturalness

Keep Jev only if it demonstrates a measurable advantage.

### Adapter observability

Add a privacy-safe diagnostics screen with:

- active package
- adapter selected
- nodes scanned
- message count
- title present/absent
- OCR fallback reason
- last capture timestamp
- no message text

This will make app-version regressions much faster to diagnose.

### Test coverage

The upstream baseline has no `app/src/test` or `androidTest` suite. Highest-value tests:

- stale-session callback rejection
- transient-title cross-chat isolation
- snapshot identity collisions
- X content-description parsing
- title extraction helpers
- KB history overlap/no-overlap sequences
- HTTP retry/status/redirect behavior
- OCR text cleanup/grouping

## Verification status

Completed:

- source-level repository audit
- upstream/fork metadata verification
- branch-isolated changes
- line-by-line static review of modified paths
- privacy-log review of major logging call sites

Not completed:

- Android compilation
- unit/instrumentation tests
- APK build/signing
- emulator run
- real-device regression on WeChat / QQ / X / Feishu

Therefore this branch is **not yet release-qualified**. The next gate is a real Android build followed by focused regression testing of capture → analyze → switch-conversation → fill behavior.

## Commits in this hardening pass

- `39c01a5` — include conversation title in snapshot identity
- `520bd74` — isolate async analysis by conversation session
- `75b17f6` — keep provider error bodies out of logcat
- `b75c5d6` — subscribe only to handled accessibility events
- `5d71be4` — keep conversation titles out of logcat
- `79a8b64` — disable redirects on authenticated model requests

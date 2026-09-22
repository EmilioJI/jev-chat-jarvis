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

### Secure API-key storage — RESOLVED IN CODE / DEVICE GATE PENDING

Judge / reply / vision API keys now use an AndroidKeyStore-backed AES-256-GCM store:

- the AES key remains inside AndroidKeyStore
- SharedPreferences stores only versioned IV + ciphertext
- v1.3 plaintext keys migrate with decrypt-back verification
- migration is two-phase: ciphertext commit succeeds before plaintext deletion is attempted
- failed cleanup leaves both copies and retries later; failed encryption preserves the working plaintext fallback rather than losing the credential
- the obsolete v1.2 `openrouter_key` duplicate is removed after the v1.3 judge slot is readable
- deprecated `EncryptedSharedPreferences` / `MasterKey` APIs are not used
- settings connectivity-test scratch preferences are cleared on success, failure and early validation exits

Android compilation is gated in CI. Final status remains **runtime-pending** until the migration is exercised on a real Android device.

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

### Android 16 / target API 36 — RESOLVED IN BUILD GATE

The fork now builds against Android 16 with a pinned toolchain:

- `compileSdk = 36`
- `targetSdk = 36`
- AGP `8.10.1`
- Gradle Wrapper `8.11.1`
- JDK 17
- Build Tools `35.0.0`

Run #20 completed `:app:assembleDebug` successfully and the APK badging gate asserted both `targetSdkVersion='36'` and `compileSdkVersion='36'`.

The migration also fixed a pre-existing cross-platform Gradle issue: the legacy Windows release-signing fallback `H:/android/keys/jev-release.properties` is now evaluated only on Windows; Linux/CI uses only an explicit `JEV_KEYSTORE_PROPS`.

Runtime compatibility on Android 16 still belongs to the real-device gate, especially AccessibilityService / screenshot / overlay behavior.

### Persistent chat-history ambiguity — RESOLVED

History recording now receives an explicit `HistoryCaptureHint`:

- `CONSERVATIVE` for manual analysis, scrolling, window changes and service restore
- `NEWEST_SCREEN` only for automatic analysis caused by a real `TYPE_WINDOW_CONTENT_CHANGED` event

A zero-overlap screen is appended only with `NEWEST_SCREEN`; conservative reads still skip it. In addition, `TYPE_VIEW_SCROLLED`, window changes and service restore no longer trigger automatic model analysis.

`KbSelfCheck` now covers the sequence:

- zero overlap + conservative → no append
- zero overlap + newest → append
- subsequent one-line overlap → append only the new tail
- final order must be exactly A/B/C/D/E

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

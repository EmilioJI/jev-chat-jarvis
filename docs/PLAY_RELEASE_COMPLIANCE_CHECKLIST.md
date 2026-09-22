# Google Play Release Compliance Checklist — Jev Chat Fork

Status: pre-release working checklist, not legal advice.

This checklist reflects the current fork behavior on 2026-09-22. Re-run the
review if AccessibilityService, model routing, OCR, history, storage, analytics,
ads, or third-party SDK behavior changes.

## 1. AccessibilityService classification — DEVICE A/B COMPLETED

Product purpose: conversation analysis / reply assistance.

This is NOT primarily a disability-access tool. Google Play therefore does not
permit this product to claim `isAccessibilityTool=true` merely to obtain access
to sensitive UI data.

Real-device A/B on vivo X100 Ultra / Android 16:

- `isAccessibilityTool=true`: WeChat tree capture PASS
- `isAccessibilityTool=false`: service remained Enabled + Bound, but WeChat and
  X stopped producing usable capture diagnostics
- a normal non-sensitive third-party test app still produced diagnostics, so
  the false build itself was not dead

This result is consistent with Android API 34+ `accessibilityDataSensitive`
behavior, where sensitive events / nodes can be restricted to accessibility
tools.

Release implication:

- the current compatibility build may keep `true` for sideload/internal use
- it is **NOT Play-release compliant** for this product category
- the false experiment is closed / DO NOT MERGE because it breaks a core feature
- a Google Play candidate needs a capture architecture that does not depend on
  claiming accessibility-tool status for sensitive chat data

Do not make PR #1 Play-ready until a Play-safe capture path is implemented and
validated.

## 2. Prominent disclosure — implemented, device verification pending

The in-app disclosure must remain separate from other privacy notices and must
appear immediately before the user is sent to Android Accessibility settings.

Required facts currently disclosed:

- visible chat text / accessibility node structure / conversation title can be
  read from the active chat window
- OCR fallback may take a screenshot of the active chat window
- default ML Kit OCR is local
- remote Vision OCR is opt-in and sends only a cropped chat-content region to
  the provider configured by the user
- analysis text is sent to the judgment / reply model provider selected by the
  user
- local chat history is written only after context/history is explicitly enabled
- the app does not read chat databases
- the app never automatically presses Send

Acceptance:

- "不同意" must not open Android settings
- disclosure must be shown again when the user retries
- "同意并前往系统设置" is the affirmative consent action
- Play review video must show both consent and non-consent flows and the entire
  disclosure text, including scrolling if needed

## 3. AccessibilityService automation boundary

Keep the invariant:

- reading visible content: permitted product function subject to declaration
- analysis / candidate generation: model work, not AccessibilityService action
- filling a candidate: only after an explicit user tap
- sending: NEVER automatic
- no autonomous multi-step UI execution
- no changing system settings on the user's behalf
- no blocking uninstall / disabling permissions

Any future "auto-send", autonomous agent, or general UI-control feature requires
a fresh policy review before implementation.

## 4. Privacy policy — required

A public privacy policy must be available:

- in Play Console App content
- from inside the app
- on a stable HTTPS URL

The policy must match the actual app and at minimum explain:

### Data accessed

- visible in-app chat messages and conversation metadata through Accessibility
- optional screenshots for OCR fallback
- user-entered relationship/contact/knowledge-base information
- optional local chat history and derived contact summaries
- API credentials entered by the user
- privacy-safe local capture diagnostics metadata

### Data sent off device

Only when the relevant function is used:

- judgment prompts -> selected judgment provider
- reply prompts -> selected reply provider
- remote Vision OCR crop -> selected vision provider
- rolling summary input -> selected reply provider, only after explicit
  auto-summary opt-in

The app itself does not route model traffic through a developer-owned backend in
the current architecture.

### Local storage

- API keys: AndroidKeyStore-backed AES-GCM ciphertext
- history: local app-private files, default OFF
- knowledge/contact data: local app-private files
- diagnostics: metadata only; no title/body/OCR text
- Android app backup: disabled

### User control / deletion

Explain how users can:

- turn AccessibilityService off in Android settings
- disable history / auto-summary / remote Vision OCR
- clear knowledge base and history
- clear a single contact's history + derived summary
- clear app data / uninstall to remove remaining local data

## 5. Data Safety form — map to real behavior, not marketing wording

The following mapping should be reviewed in Play Console at submission time.

### Other in-app messages

Likely applicable.

The app reads chat messages and transmits message content to a model provider
when analysis/reply generation is invoked.

Suggested purpose:
- App functionality

Transport:
- encrypted in transit (HTTPS enforced for non-loopback endpoints)

Retention:
- the app's own model HTTP helper does not persist remote responses
- third-party provider retention is controlled by that provider/account and must
  be represented accurately in the privacy policy

"Shared" vs "collected" must be answered using the exact Play definitions and
the contractual role of each provider at submission time. Do not automatically
claim "not shared" merely because the user supplied the API key.

### Photos / screenshots

Review explicitly if remote Vision OCR remains available at release.

The remote path sends an image crop of the chat region. Even when its purpose is
OCR of message text, Play may expect image-data disclosure. Verify the current
Data Safety taxonomy in Play Console during submission.

Local ML Kit OCR does not transmit the screenshot.

### App activity / diagnostics

The capture-diagnostics page stores package/adapter/source/count/status metadata
only on device. If no analytics/crash SDK uploads it, do not claim that this
local-only metadata is collected off device.

### API keys / credentials

Keys are user-supplied secrets used only to authenticate to their configured
providers and are encrypted at rest. Re-check current Data Safety taxonomy at
submission time rather than guessing a category.

## 6. Third-party AI integrations

The developer remains responsible for User Data policy compliance when data is
sent to third-party AI services.

Before public release:

- list supported providers in the privacy policy
- explain that the user chooses/configures the provider
- state which features send data to which route
- do not silently fail over to another provider
- keep host-bound key inheritance
- keep HTTPS-only non-loopback transport
- review provider terms / retention / training settings
- avoid claiming that providers never retain or train unless verified for the
  exact account / API product being used

## 7. Play Accessibility declaration video

Record on the final Play candidate, not the debug acceptance package.

Video should show, in order:

1. launch app
2. tap Accessibility permission entry
3. show full prominent disclosure
4. tap "不同意" and demonstrate no settings permission flow occurs
5. trigger disclosure again
6. tap "同意并前往系统设置"
7. enable the service
8. open a supported chat
9. demonstrate the core assist feature
10. show that a reply is only filled after user action and is not sent
    automatically

Use captions/voice-over where the AccessibilityService use is not self-evident.

## 8. Play listing / screenshots

The listing should not imply:

- disability support if that is not the product purpose
- automatic sending
- access to private chat databases
- "100% local" processing, because judgment/reply and optional Vision use
  third-party APIs

It is accurate to say:

- default OCR is local
- model routing is user-configurable
- the user reviews and sends messages themselves
- history is opt-in and local

## 9. Current blocking release gates

Do not make PR #1 release-ready until:

- Android 16 normal acceptance package real-device PASS
- AndroidKeyStore runtime self-check PASS
- WeChat true/false Accessibility A/B has a recorded outcome
- QQ / X / Feishu regression PASS
- at least one judgment provider real-network PASS
- at least one reply provider real-network PASS
- remote Vision OCR tested if it will ship enabled
- final `isAccessibilityTool` value matches the Play declaration
- privacy policy URL is live and linked in-app
- Data Safety form is completed from observed behavior
- Accessibility declaration + review video are ready

## Official references

- Google Play: Use of AccessibilityService API
  https://support.google.com/googleplay/android-developer/answer/10964491
- Google Play: Prominent disclosure and consent best practices
  https://support.google.com/googleplay/android-developer/answer/11150561
- Android: AccessibilityService isAccessibilityTool / intro attributes
  https://developer.android.com/reference/android/R.styleable

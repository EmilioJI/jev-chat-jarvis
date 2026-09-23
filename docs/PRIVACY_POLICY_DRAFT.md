# Privacy Policy Draft — Jev Chat Assistant Fork

Last updated: 2026-09-22

> Working product draft. Replace publisher identity, contact email and public URL
> before release. Review against the final Play build and provider contracts.

## Overview

Jev Chat Assistant is a conversation-assistance app. It can read chat content
that is currently visible on the device, analyze the conversation using model
services configured by the user, and provide candidate replies. It does not
automatically send messages.

## Information the app accesses

Depending on the features you enable, the app may access:

- text and UI structure visible in the active supported chat window;
- the visible conversation title;
- a screenshot of the active chat window when OCR fallback is needed;
- relationship descriptions, notes and contacts you enter into the local
  knowledge base;
- chat history if you explicitly enable conversation history;
- API credentials that you enter for model providers;
- capture diagnostics such as app package name, adapter name, message count,
  capture source, timestamp and status.

The capture-diagnostics feature does not store conversation-title text, chat
message text, OCR text, API keys, prompts or model responses.

## AccessibilityService

The app uses Android AccessibilityService to identify the active supported chat
window and read content that is visible to you.

Before you are taken to Android's Accessibility settings, the app displays a
separate disclosure explaining what is accessed, why it is needed and how data
may be transmitted.

You may refuse the request or later disable the service in Android settings.

The app does not use AccessibilityService to automatically send a message. A
candidate reply can be inserted into an input box only after your explicit
action, and you remain responsible for reviewing and sending it.

## Model providers and data sent off device

The app supports separate judgment, reply and vision routes. You select and
configure the provider.

When you request or enable a feature that needs a remote model:

- conversation text and relevant configured context may be sent to the selected
  judgment provider;
- conversation text and relevant configured context may be sent to the selected
  reply provider;
- if you explicitly select remote Vision OCR, a cropped image of the visible
  chat-content region may be sent to the selected vision provider;
- if you explicitly enable rolling contact summaries, stored chat history may be
  sent periodically to the selected reply provider.

The current app architecture sends those requests directly from your device to
the provider endpoint you configure. It does not proxy model traffic through a
developer-operated application server.

The app does not silently switch conversation data to another model provider
when a provider fails.

Third-party providers have their own privacy, retention and account policies.
You should review the policy and settings of the provider you choose.

## Local OCR

The default OCR engine is bundled ML Kit Chinese text recognition. In local OCR
mode, screenshots are processed on the device and are not sent to a vision
model provider by the OCR feature.

Remote Vision OCR is a separate, explicit setting.

## Local history and knowledge base

Conversation history is OFF by default.

If you enable history, chat entries for contacts you have saved may be written
to the app's private local storage and used as context for later analysis.

Rolling contact summaries are also OFF by default. If enabled, the app updates a
derived summary only after enough new recorded history accumulates.

Knowledge-base notes and contact information that you create are stored in the
app's private local storage.

## API credentials

API credentials are stored using AES-GCM encryption backed by AndroidKeyStore.
The encryption key remains in AndroidKeyStore and encrypted values are stored in
the app's private preferences.

The app prevents implicit credential inheritance between different provider
hosts and requires HTTPS for non-loopback model endpoints.

Credentials are not intentionally written to application logs.

## Network security

Authenticated model endpoints must use HTTPS, except for localhost / loopback
development endpoints.

The app rejects model endpoint URLs containing embedded usernames/passwords,
URL fragments, unsupported schemes or cleartext non-loopback HTTP.

Authenticated requests do not automatically follow HTTP redirects.

## Logs and diagnostics

Application logs avoid chat message bodies and conversation-title text.

The optional in-app capture-diagnostics page stores only limited operational
metadata on the device. It does not store message text or OCR output.

## Data retention and deletion

You can control local data by:

- disabling conversation history;
- disabling rolling summaries;
- using local OCR instead of remote Vision OCR;
- clearing one contact's history, which also clears the summary derived from
  that history;
- clearing the knowledge base and history from settings;
- disabling AccessibilityService in Android settings;
- clearing the app's storage or uninstalling the app.

Android application backup is disabled for this app.

Data that has already been sent to a third-party model provider is governed by
that provider's retention and deletion controls.

## Data selling and advertising

The current fork does not contain an advertising SDK and does not implement a
mechanism for selling user chat data.

If advertising, analytics, crash-reporting or another data-collection SDK is
added later, this policy and the Play Data Safety declaration must be updated
before release.

## Children

This draft does not represent the app as a product specifically directed to
children. If the product is later directed to children, family/child privacy and
Google Play Families requirements must be reviewed separately before release.

## Changes

This policy must be updated when material data-access, model-routing, storage,
provider or SDK behavior changes.

## Contact

Publisher: [REPLACE BEFORE RELEASE]

Privacy contact: [REPLACE BEFORE RELEASE]

Public privacy-policy URL: [REPLACE BEFORE RELEASE]

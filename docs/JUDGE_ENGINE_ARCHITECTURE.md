# Judgment Engine Architecture

This fork separates **judgment/ranking** from **reply generation**.

## Why

TypeSafe's direct Jev access is early-access oriented, while OpenRouter exposes
Jev Decisions to ordinary OpenRouter API keys. The app should therefore not
depend on TypeSafe direct registration as a single point of failure.

At the same time, a generative model such as GLM-4.7 can provide a structured
fallback/alternative without pretending to speak Jev's wire protocol.

## Engines

### 1. OpenRouter Jev — default / recommended

- provider id: `openrouter`
- base: `https://openrouter.ai/api`
- endpoint: `/alpha/decisions`
- model: `typesafe/jev-1.13`
- protocol: Jev Decisions

### 2. TypeSafe direct — compatibility for existing key holders

- provider id: `typesafe`
- base: `https://api.typesafe.ai`
- endpoint: `/v1/systemone`
- model: `jev-latest`
- protocol: Jev Decisions

The UI deliberately labels this as “已有 Key”. It is not the recommended
onboarding path for a new user.

### 3. GLM-4.7 — structured LLM engine

- provider id: `glm47`
- base: `https://open.bigmodel.cn/api/paas/v4`
- endpoint: `/chat/completions`
- model: `glm-4.7`
- protocol: ordinary chat completions + strict JSON contract
- thinking: disabled for this classification/ranking workload

The GLM engine receives the **same calibrated Jev question rubric** used by the
Jev Decisions engine, then maps validated JSON back into the common
`Analysis` / `RankedReply` models.

## Common boundary

`JudgeEngine` defines:

- `judge(snapshot, relationship, context)`
- `rank(snapshot, relationship, candidates, context)`

The rest of the capture/overlay pipeline is provider-agnostic.

Current implementations:

- `JudgeClient` — Jev Decisions protocol
- `StructuredJudgeClient` — GLM-4.7 structured chat-completions protocol

`JevClient` remains as a back-compatible facade and selects the engine from
`Prefs.judgeProvider`.

## Credential isolation

A blank reply/vision key may inherit another route's key **only when the two
routes resolve to the same host**.

Examples:

- OpenRouter judge -> OpenRouter reply: inheritance allowed
- GLM judge -> GLM reply: inheritance allowed
- GLM judge -> OpenRouter reply: inheritance blocked
- TypeSafe judge -> OpenRouter reply: inheritance blocked

This prevents one provider's Bearer credential from being sent to another host.

## No automatic cross-provider failover

There is intentionally **no** automatic fallback such as:

`OpenRouter Jev failure -> silently send the chat to GLM`

Changing providers can change privacy, pricing, latency and model behavior.
Fallback across providers must be a user-visible setting or explicit future
feature, never an invisible recovery path.

## GLM validation contract

The GLM path must return all seven judgment fields:

- true intent
- danger level
- need
- should reply now
- best action
- tension resolved
- literal question

Choices are allow-listed; probabilities are clamped to 0..1; danger is clamped
to 1..9. Missing or malformed required fields fail the round instead of
silently showing partial analysis.

Ranking requires exactly three non-negative scores and normalizes them into
probabilities.

## External protocol references

- OpenRouter Jev 1.13:
  https://openrouter.ai/typesafe/jev-1.13/api
- OpenRouter Jev Decisions example:
  https://openrouter.ai/labs/jev/compile
- Zhipu GLM-4.7:
  https://docs.bigmodel.cn/cn/guide/models/text/glm-4.7
- Zhipu thinking mode:
  https://docs.bigmodel.cn/cn/guide/capabilities/thinking-mode

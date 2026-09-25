# AGENTS.md — Jev Chat Jarvis agent rules

## Authority order

1. Repository owner's explicit current objective and decisions.
2. Current repository documentation and existing privacy/account-safety boundaries.
3. Current implementation, tests, build output, and reproducible CI/device evidence.
4. This file and the shared operational template in EmilioJI/xiaoshutong-governance.
5. Agent convenience.

Repository-local rules remain executable when the shared repository is unavailable.

## Continuous goal execution

For an explicitly authorized objective, continue:
inspect -> implement -> verify -> commit/push working branch -> CI -> inspect failures -> fix -> rerun -> acceptance.

A completed subtask or failed build/test/CI run is not a stop condition. It feeds the next iteration. Do not ask the owner to type "continue" after each reversible iteration.

Focused reversible edits, commits, pushes to the authorized working branch, existing CI runs, and failure-driven fixes may proceed without repeated approval.

## Existing safety boundary

- Preserve the repository's current privacy and account-safety constraints unless the owner explicitly changes scope.
- Preserve user control over consequential actions.
- Do not commit or log API keys, tokens, signing secrets, private credentials, or private conversation data.
- Do not represent emulator evidence as proof of real third-party-app or vendor-ROM behavior.
- Security changes must not silently broaden permissions, data collection, persistence, or automation scope.

## Engineering path

Prefer GitHub -> existing GitHub Actions/self-hosted runner -> CI evidence for normal verification. Use Android Emulator for generic UI/build/deterministic flows, and real devices only when device-specific behavior is actually part of acceptance.

If .github/SELF_HOSTED_CI_BANDWIDTH_POLICY.md exists, read it before changing CI. Diagnose CI from actual run/job/step evidence before editing workflows.

## Stop conditions

Request owner input only for destructive/irreversible mutation, Git history rewrite/force-push, credential/permission/signing changes, public release not already authorized, or a material privacy/product/security decision outside existing authority.

## Evidence and completion

Distinguish BUILD_PASS, TEST_PASS, CI_PASS, EMULATOR_PASS, DEVICE_PASS, APP_INTEGRATION_PASS, and RELEASE_PASS. Unrun layers remain NOT_RUN.

The objective is complete only when all mandatory acceptance criteria pass or an owner-approved limitation is recorded. If interrupted, persist enough branch/issue/PR state for a fresh session to continue.

# Self-Hosted CI Bandwidth Policy

This repository may use GitHub-hosted runners today. If a self-hosted runner is
introduced later, the following rules are mandatory.

## Principles

1. Reuse existing runners and local toolchains before adding new runners.
2. Do not solve CI failures by repeatedly downloading Android SDK packages,
   emulator system images, Gradle distributions, Docker images, or other large
   dependencies.
3. Do not broaden automatic triggers merely to make validation easier.
4. Prefer narrow path filters, manual dispatch for expensive diagnostics, and
   `concurrency.cancel-in-progress: true`.
5. Prefer already-installed Android SDK / Build Tools / JDK / emulator assets.
6. Do not upload large artifacts on every routine run. Upload APKs, screenshots,
   emulator dumps, logs, or archives only for failures or explicit acceptance /
   delivery gates, with short retention where practical.
7. Do not register duplicate self-hosted runners for the same repository as a
   shortcut around queueing or environment problems.
8. Diagnose cache misses / repeated downloads before changing CI topology.

## Android-specific rules

- Verify required SDK/platform/build-tools exist before a job begins.
- A missing heavy dependency should fail clearly instead of automatically
  downloading gigabytes unless the repository owner explicitly approves that
  installation.
- Emulator/KVM jobs must be narrowly scoped and must not recreate emulator
  images on each run.
- Gradle Wrapper is preferred over an arbitrary machine-global Gradle version,
  but a self-hosted runner should cache the pinned distribution rather than
  redownload it repeatedly.
- Keep ABI scope as narrow as product requirements allow.

## Workflow changes

Before modifying or adding GitHub Actions / self-hosted Runner CI:

1. read this file;
2. inspect current runners, caches, triggers and installed toolchains;
3. explain any change that materially increases network usage;
4. prefer the lowest-bandwidth solution that preserves correctness.

Do not bypass this policy by adding another workflow that performs the same
heavy downloads under a different trigger.

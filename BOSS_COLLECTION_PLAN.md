# Huntmaster RuneLite Plugin: shared boss evidence collection

The owner is not expected to personally test every boss. Reference encounters validate the shared pipeline; beta players provide broader samples. The Huntmaster Discord Bot processes evidence and controls assignment credit. Observations never automatically become credited kills.

## Implemented in this stage

- Collection catalogue distinguishes public profiles, development profiles, and missing adapters. See the Huntmaster Discord Bot's BOSS_COVERAGE_CATALOG.json and BOSS_COVERAGE_AUDIT.md.
- Registered encounter capture keeps at most 64 recent primary/loot signals for 16 ticks, scoped to the same account, assignment, and boss.
- If a KC report begins after an earlier capture expired, it can include that retained earlier evidence and keep the configured post-counter window. Total report window remains capped at 128 ticks and signals at 32.
- Historical loot replay is labelled uncertain. Supporting signals do not change the local verifier. A prior expired report and recovered report can refer to the same death; reviewers must not count them as independent kills.
- Consumed history is not carried into later counter totals. Assignment interruption, logout, shutdown, consent clearing, and clock reset clear it.

## Remaining implementation sequence

1. Expand the inventory beyond the bot's current 63 entries to all supported non-raid boss/content counters. Mark disabled assignments separately from collection capability. Do not describe the current inventory as exhaustive.
2. Shared KC-message router implemented for personal counters matching the active non-raid assignment's exact canonical name (case-insensitive). Existing profiles take precedence. Missing profiles get an observation-only detector; unknown initial baselines and jumps remain evidence. Alias mappings and unusual completion formats still need explicit adapters rather than guessed NPC death rules.
3. Death and loot events matching the generic assigned boss's exact name now feed the existing bounded buffer without scene scans. NPC aliases, composite encounters, and counters with no personal message still need adapters. This is not universal verified coverage.
4. Keep registered-account/assignment scoping initially. Decide explicitly whether beta collection should cover encounters outside active assignments; do not silently collect unrelated encounters.
5. Keep structured evidence storage and sanitized Discord exports. Add sampling and unusual-case retention to avoid reporting every repeated normal encounter indefinitely.
6. Propose versioned per-boss verification rules from multiple beta samples, including missing-signal and interruption cases. Review before activation. AI analysis must not activate rules automatically.
7. Track collection enabled, samples received, rule proposed, rule enabled, and beta-confirmed separately. Preserve deferred cases for post-Hub beta testing.

## Validation

Automated tests cover delayed-counter recovery, missing baseline, bounded history, assignment isolation, and clock reset, alongside existing capture/verifier tests. Actual client behavior remains pending user confirmation. Offer to launch the development client; one existing reference boss can exercise the shared route. No requirement to kill all bosses.

## Research map update - September 17, 2026

The Huntmaster Discord Bot now has BOSS_DETECTION_MAP.json / .md covering all 63 current non-raid assignments, including disabled entries. Fresh official RuneLite source snapshots and hashes are retained in BOSS_DETECTION_SOURCE_SNAPSHOT.json; researchBossDetection.js reproduces the map. Counter categories and chat name conversions are distinguished from per-boss live message or NPC confirmation. Pending paired/activity/delve adapters remain explicit. Barrows/Moons reward collectors and separate Dagannoth Kings observation collectors are implemented and reference-confirmed; this supersedes earlier pending collection statements. Generic beta candidates may credit their first qualifying supported kill without a known baseline; existing strict profiles keep their rules. No requirement for the owner to kill each boss. Research itself does not activate missing adapters or component credit.

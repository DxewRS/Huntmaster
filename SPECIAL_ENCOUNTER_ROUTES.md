# Special encounter routes

Source review: September 17, 2026. This document distinguishes implemented beta code from in-game confirmation. The owner is not expected to kill every encounter.

| Assignment | Dedicated personal total | Processing | Live status |
|---|---|---|---|
| Grotesque Guardians | `VarPlayerID.TOTAL_GARGBOSS_KILLS` | Exact +1 completion candidate | Deferred to broader beta |
| Royal Titans | `VarPlayerID.TOTAL_ROYAL_TITAN_KILLS` | Exact +1 completion candidate | Deferred to broader beta |
| Zalcano | `VarPlayerID.TOTAL_ZALCANO_KILLS` | Exact +1 completion candidate | Deferred to broader beta |
| TzTok-Jad | `VarPlayerID.TOTAL_JAD_KILLS` | Exact +1 completion candidate | Deferred to broader beta |
| TzKal-Zuk | `VarPlayerID.TOTAL_ZUK_KILLS` | Exact +1 completion candidate | Deferred to broader beta |
| Sol Heredit | `VarPlayerID.TOTAL_SOL_KILLS` | Exact +1 completion candidate | Deferred to broader beta |
| Doom of Mokhaiotl | `VarPlayerID.DOM_LEVEL_HIGHSCORES` | Observation-only candidate metric | Metric meaning needs confirmation |

These constants were checked against [official RuneLite gamevals](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/VarPlayerID.java) and compiled against the installed API. Names identify candidate personal totals; they are not live fixtures. Source snapshots with review times and hashes are retained in the Discord Bot's `BOSS_DETECTION_SOURCE_SNAPSHOT.json`.

The plugin samples only the active assigned encounter's dedicated total, once per game tick. There are no scene scans or extra network requests per tick. The first read is a baseline, never an earned kill. A subsequent change is recorded as `completion_varp` with explicit previous/current values and a completion signal. Both signals come from the same counter observation; they are not independent corroborating evidence. Unchanged totals generate no report.

Reports keep the bot's canonical assignment name and assignment UUID. The six credit candidates use `COMPLETION`, `dedicated-total-beta-v1`, and `beta_candidate`. The bot's `dedicated-personal-total-v1` policy accepts only whitelisted encounters, complete unresolved reports, known previous totals and exactly +1. Current totals must agree across counter observations. Duplicate user/boss/total and report UUID protection uses existing transactional receipts. Cancellation/reroll assignment UUID checks and existing completion/reward processing still apply. Neither death of one paired NPC nor loot alone awards credit.

Initial reads after login, restart, account/assignment changes, and tracking pauses establish a fresh baseline. Clock resets clear sampled history. Kills while tracking is paused are not recovered from the difference at resumption. Counter loading/synchronization behavior and timing remain important broader-beta cases: automated checks do not establish how each counter is populated by the live game. No strict verification or Plugin Hub approval claim.

Doom uses `dedicated-total-observation-v1` and `evidence_only`; the bot rejects dedicated credit for it. The Wiki distinguishes ordinary delves and deep-delves counted in HiScores, but the exact candidate gameval unit and live update behavior need confirmation. Do not substitute total levels, deepest level, loot claims or every boss death. Retain this as a deferred metric-review task.

Existing Gauntlet/corrupted Gauntlet completion and Wintertodt/Tempoross activity verifiers remain in place. Their personal counter messages now also route through the shared name-aware parser, accepting supported wording variants without merging encounters. Delayed crates, reward pools, glory and individual Colosseum waves are not new encounter completions.

Barrows/Moons reward-chest beta routes are reference-confirmed once each and allow partial clears. Dagannoth Kings retain separate component counters and duplicate identities; all three collection paths and Supreme beta credit have a live reference. Future improvements should use versioned evidence review and human approval, rather than silently promoting observations into credits.

Automated checks cover initial reads, exact changes, resumption, assignment/account and clock resets, wrong metrics, wire modes, duplicate delivery, invalidated assignments and bot processing. Broader multiplayer, counter synchronization and unusual completion cases are explicitly deferred to post-Hub beta-player data. No additional owner kills requested for this pass.

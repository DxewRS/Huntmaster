# Huntmaster RuneLite Plugin

Huntmaster supplies own-account observations and boss encounter evidence to the Bosscape Huntmaster Discord Bot. The bot manages assignments, eligibility decisions, points and ranks. RuneLite observes; the bot decides.

The currently published Plugin Hub release is version 1.3 at `5798fe2361bbe639f36912ce7768e91915caa8fe`, build `collector-v2-release-1` (`ReleaseInfo.BUILD`, a release identifier rather than a Git hash). Version and build are also shown in the collapsed About Huntmaster settings section.

| Document | Covers |
| --- | --- |
| [CHANGELOG.md](CHANGELOG.md) | Release notes and version history |
| [PRIVACY.md](PRIVACY.md) | Transmitted fields and storage behavior |
| [RELEASING.md](RELEASING.md) | Verifying a candidate and submitting it |
| [DOOM_EVIDENCE.md](DOOM_EVIDENCE.md) | Doom evidence, counters and live validation |
| [ENCOUNTER_VARIANT_CONTRACT.md](ENCOUNTER_VARIANT_CONTRACT.md) | The exact bot contract for encounter variants |
| [BOSS_ARTWORK_SOURCES.json](BOSS_ARTWORK_SOURCES.json), [RANK_ARTWORK_SOURCES.json](RANK_ARTWORK_SOURCES.json) | Artwork sources and checksums |

## Getting started

Find Huntmaster through RuneLite's plugin search. Its gear/settings panel displays the Bosscape invite, community description, boss-verification purpose and a data-sharing notice. Join [Bosscape Discord](https://discord.gg/Bosscape).

Register your RuneScape name with Huntmaster in Bosscape Discord. While you remain a member, the plugin automatically matches your logged-in RSN with that registration. No private linking key is needed. Discord membership must be confirmable before the bot accepts reports. RSN matching does not cryptographically prove sender/account ownership. A rejected registration shows one login-safe notice and retries; membership/Discord failures do not grant credit.

Enabling the plugin enables its connection and evidence collection. Disable it to stop communication and new tracking. Previously saved reports remain available when re-enabled.

## Sidebar and overlay

The Current Hunt sidebar displays your server-provided assignment, a progress bar, remaining kills, the Hunt reward and a compact connection indicator, including registration and required-update guidance. It uses RuneLite's bundled fonts and packaged RuneScape game renders from the OSRS Wiki. Artwork loads locally without network requests.

The progress bar is driven only by bot-confirmed KC; a report receipt alone never advances it. Use **Open Huntmaster in Discord** to manage assignments. This only opens the existing Discord destination; it does not request, reroll or cancel an assignment.

After the server confirms completion of the assignment observed in this session, the sidebar keeps a Hunt Complete card with the actual awarded points. It clears on a new assignment, logout, world hop, account change, plugin reload or client restart. This display is memory-only, uses existing responses and requires a bot that supplies completion metadata.

The optional Your Rank card shows the current rank portrait, earned all-time points and progress within the current-to-next rank interval. Portraits are packaged locally. Rank metadata arrives through the existing assignment request at login, after a Hunt identity changes (including completion), after reconnecting and every five minutes as a fallback. Ordinary KC changes do not trigger a refresh. Missing metadata retries after a minute using the same poll; there is no extra request or direct Discord lookup. A compatible bot supplies authoritative points and thresholds. The most recent confirmed rank is cached locally for the matching RSN for up to 30 days and labelled when displaying saved information. A loading or unavailable message is shown until the first rank arrives; points are never invented. Logout/account changes clear the displayed rank. No local reward prediction or hardcoded rank thresholds are used. Highest rank shows a full bar.

Display settings include Show hunt overlay, which defaults on, and Visibility. The default After credited kill mode shows the overlay after a newly server-credited kill from the current session, then hides it after 20 minutes without credit. While Hunt active also shows progress before the session's first credited kill. Both modes display only server-confirmed progress, and hide while disconnected, on completion or on logout. Use RuneLite's overlay positioning controls to move it. These controls add no data transmission, and the sidebar adds no authorization credentials or routine polling loop of its own.

## Group recruitment alerts

Expand Raids, Wilderness Bosses, God Wars Dungeon, Other Bosses, or Skilling & Minigames in Huntmaster settings and check the activities you want. All 32 activities start unchecked, including all four raids: Chambers of Xeric, The Fractured Archive, Theatre of Blood and Tombs of Amascut. These are in-game chat alerts from the Huntmaster Discord bot's LFG / Queue system; activity selections do not affect Hunt tracking.

Enable recruitment alerts is the master switch. It defaults on to preserve existing selections and behavior, and turning it off preserves every selected activity. All selections remain opt-in and do not open repeated confirmation dialogs.

Successful queue recruitment pings, including manual Active Group pings and your own pings, produce: `Huntmaster: Someone is looking for members for Tombs of Amascut. Join through Bosscape Discord.` Multiple selected activities in one ping share one message. Polling runs every five seconds while logged in with at least one activity selected; it does not wait for combat to end. You need an RSN linked to an active Bosscape member, but no Huntmaster assignment.

Login, reconnect, account/profile changes, disabling all activity selections and the master switch start a new live-edge session; missed alerts are not replayed. Other preference edits keep the current feed cursor and duplicate history, and a pending response uses the latest choices. Failed sends, silent edits, previews and duplicate deliveries do not generate additional messages. Very old pings and groups that have stopped recruiting are omitted.

## How detection works

`BossRegistry` declares 16 bosses with dedicated detectors. Every other name in `SupportedEncounters` gets an evidence-only detector at runtime. Each detector has a type (`BossDetectorType`):

| Type | Used for | Examples |
| --- | --- | --- |
| `STANDARD_NPC` | NPC death, KC increase and NPC loot. Also the evidence-only default | Vardorvis, Vorkath, Zulrah |
| `COMPLETION` | Completions read from a dedicated total rather than an NPC death and loot sequence | Gauntlet, Zalcano, TzTok-Jad, Sol Heredit |
| `ACTIVITY` | Activity or minigame completions | Tempoross, Wintertodt |
| `SPECIAL` | Reserved for custom handling; no boss uses it today | None |

Barrows and Moons of Peril are evidence-only `STANDARD_NPC` entries that add reward-chest evidence through `ChestCompletionAdapter`.

The collector-v2 implementation reports supported encounters independently of the assigned boss, including when there is no assignment. Signals for one encounter are correlated inside a bounded capture window, then finalized into one report. Reports retain the actual encounter and its own counter; the bot decides assignment matching, harder-to-lower substitutions, credit and rewards. Unassigned observations never award task progress. No player chat text is collected.

Counter notifications use complete encounter identities and reviewed aliases (`BossCounterAliases`), including when the counter value is unreadable. A Corrupted Gauntlet or Phosani's Nightmare notification cannot also create a diagnostic for the lower variant. Independent notifications retain separate captures. Unknown notification formats are ignored rather than attributed through a partial boss-name match.

Reports include killed-component flags for Barrows and Moons of Peril alongside chest evidence. The bot decides credit: all six brothers or all three Moons must be represented for that chest.

Phosani's Nightmare has a 20-tick default capture window; The Nightmare's default window is raised to at least 24 ticks. Assignment responses may extend windows for packaged bosses using expiring data (10–128 ticks, at most 64 entries, expiry within 24 hours). Invalid or expired settings fall back to packaged defaults, and unknown future entries are ignored. The server cannot supply code, endpoints, arbitrary varps, NPC aliases or chat patterns. Longer windows delay report finalization; immediate delivery after finalization is unchanged. Supported collection stays limited to the packaged boss identities, aliases and game-state sources, so unknown game content may need a plugin release.

Exact-level Doom completion evidence and normal/awakened DT2 totals are reported as structured evidence. Delivery requires a bot advertising the matching optional capability, and older bots receive the existing payload. Doom assignments and Hunt credit remain disabled. See [Doom evidence](DOOM_EVIDENCE.md) and the [variant contract](ENCOUNTER_VARIANT_CONTRACT.md).

The bot independently checks published HiScores totals for accepted plugin credit. Ordinary progression remains immediate; unknown or delayed totals remain uncorroborated and contradictions request staff review. Ask Huntmaster staff if you need a verification record review. These checks add confidence, not cryptographic gameplay proof. High-value reward verification requires independent support or recorded staff approval under a separate reward policy.

## Connection and delivery

Public builds use https://huntmaster.bosscape.com and never fall back to a player's localhost. Development `run` uses http://127.0.0.1:8787. Redirects are disabled and requests are restricted to the packaged origin. Connection establishment is bounded at five seconds, a complete request and response at ten seconds, and responses at 64 KiB.

The plugin sends observations through `/api/runelite/observations` and reads bot decisions from `/api/runelite/verification-status`. It creates no locally verified KC requests. Saved legacy events keep their original delivery route (`/api/runelite/kc` or `/api/runelite/encounter-reports`) and IDs. Collector-v2 reports wait in the queue until the bot advertises support in assignment responses. Assignment, recruitment, account-state and collection-health requests use their own `/api/runelite/` routes, and service health uses `/health`.

While logged in, healthy connections check service health every 30 seconds and assignments every five seconds. Logged-out clients make no routine requests. Verification decisions are polled every ten seconds only with an active assignment, and account snapshots are sampled at most once per minute. Collection health is sent at most once per minute while logged in, registered and connected to a compatible bot: session counters for captured signals/reports, acknowledged deliveries, capture/delivery errors, queue size and last captured observation time. These diagnose missing delivery without awarding credit.

Finalized evidence attempts delivery immediately through the existing single-request queue. Reports that finalize while another request is in flight wait for the five-second worker; acknowledgements do not rapidly drain old backlogs. Failed reports retry after at least 60 seconds once connection and assignment state recover. Pending kills keep their event/assignment IDs and report IDs across retries and restarts, reports keep their original capture-policy revision, and temporary rate limits do not discard legacy events.

Brief connection interruptions are silent. One warning appears after five minutes. At ten minutes collection pauses new tracking while preserving saved reports, and a recovery message appears only after that deadline.

A credit report the bot rejects (400, 409 or 413), that cannot be routed, or that fails three times for any reason other than an outage or rate limit is held: it stays saved, retries hourly, no longer blocks connection recovery, and is removed only after 30 days held and 24 failed attempts, or when more than 2,000 reports or 8 MiB are held. Removed reports are copied to a backup setting first, and three backups are kept. The bot's `report_expired` answer is final and removes the report. If saved reports cannot be fully read at startup, the original saved text is copied to a backup setting first.

New collection pauses when the saved report backlog reaches 1,000 reports or 8 MiB, and resumes as delivery clears space. Already captured evidence is preserved, so those thresholds are collection limits rather than hard limits on existing saved data. Checkpoint writes drain on shutdown.

The bot allows 60 seconds for delayed evidence before warning about an unconfirmed personal counter increase. Collector v2 continues collecting after verification warnings so failures remain diagnosable; outage and backlog limits still apply. Death-only observations do not count as personal failures, and relog starts a fresh warning session. The bot rejects outdated assignments and duplicate credit.

## Data and compliance

The plugin sends your IP address through normal HTTPS traffic and submits your RSN, relevant account requirements, boss counters and bounded encounter evidence to a third-party Bosscape service not controlled or verified by RuneLite developers. The Plugin Hub descriptor carries the third-party installation warning; no separate sharing toggle or key flow is added. Unregistered accounts send a registration lookup, not accepted encounter/account reports. See [PRIVACY.md](PRIVACY.md).

The plugin reports only the local account and only reads game state. It sends no game input and adds no menu or gameplay assistance; its chat messages are local notices and recruitment alerts. Java only, with no third-party dependencies, reflection, subprocesses or runtime downloads. Network calls use RuneLite's HTTP client asynchronously. All plugin filesystem access uses RuneLite's `net.runelite.client.util.Filepath` API, and packaged resources use classpath streams. This describes the code; approval rests with Plugin Hub review.

## Development

The plugin targets Java 11 bytecode and uses a BSD-2-Clause license.

```bash
./gradlew test build
```

On Windows use `gradlew.bat`. `./gradlew run` launches a client using a local bot. `./gradlew runPublic` launches a development client against the packaged endpoint without the localhost override. Only the player can validate game behavior.

Keep RuneLite's `latest.release` dependency current: run `./gradlew --refresh-dependencies test build` before testing after a RuneLite update. Offline launches reuse cached dependencies and can run an older client, and an outdated development client can break unrelated native RuneLite features. Jagex Account users should follow [RuneLite's login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

`./gradlew verifyPackagedResources` checks the built release JAR and also runs with `check`/`build`. It verifies packaged plugin classes, the RuneLite entry point, boss/rank artwork and Java 11 bytecode without development-folder fallback. Use `-PpackagedJar=/path/to/plugin.jar` to check a separately built artifact. See [release preparation](RELEASING.md) for the standard-package/submission distinction.

The Discord bot's offline tests read some files in this repository by their paths and parse them as text, including the boss communication fixture, so keep the paths listed in [release preparation](RELEASING.md) stable.

The authoritative [Plugin Hub descriptor](https://github.com/runelite/plugin-hub/blob/master/plugins/huntmaster) identifies the published commit. This repository keeps no duplicate marker.

## Known limits

- Natural Doom/Awakened gameplay validation remains outstanding. Protocol support and automated checks do not establish live timing.
- Beta evidence support is not equivalent to strict verification for every boss.

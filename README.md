# Huntmaster RuneLite Plugin

Huntmaster supplies own-account observations and boss encounter evidence to the Bosscape Huntmaster Discord Bot. The bot manages assignments, eligibility decisions, points and ranks. The currently published Plugin Hub baseline is version 1.2 at `ccd4b442a82315da87ff57556ce8c847b0bf2ec5`. Version 1.3 below is a local release candidate, not a published Hub update. See [release notes](CHANGELOG.md), [release preparation](RELEASING.md) and the [data disclosure](PRIVACY.md).

## Using Huntmaster

### Version 1.3 release candidate

The Current Hunt sidebar uses RuneLite's bundled fonts, packaged RuneScape game
renders from the OSRS Wiki, and a progress bar driven only by bot-confirmed KC.
It shows remaining kills, the Hunt reward and actual points on confirmed completion.
Tracking Status and Copy diagnostics have been removed from the panel. Artwork
loads locally without new network requests; sources and checksums are recorded in
[BOSS_ARTWORK_SOURCES.json](BOSS_ARTWORK_SOURCES.json).

The optional Your Rank card shows the current Huntmaster rank portrait, earned
all-time points and progress within the current-to-next rank interval. Rank images
are also packaged locally; see [RANK_ARTWORK_SOURCES.json](RANK_ARTWORK_SOURCES.json).
Rank metadata is requested through the existing assignment request at login,
after a Hunt identity changes (including completion), after reconnecting and
every five minutes as a fallback. Ordinary KC changes do not trigger a refresh.
Missing metadata retries after a minute using the same poll; there is no extra
request or direct Discord lookup. A compatible bot supplies authoritative points
and thresholds. The most recent confirmed rank is cached locally for the matching
RSN, for up to 30 days, and labelled when displaying saved information. A loading
or unavailable message is shown until the first rank arrives; points are never
invented. Logout/account changes clear the displayed rank. No local reward
prediction or hardcoded rank thresholds are used. Highest rank shows a full bar.

Brief connection interruptions are silent. One warning appears after five minutes;
the existing ten-minute collection limit still pauses new tracking and preserves
saved evidence. A recovery message appears only after the ten-minute deadline.
Connection establishment remains bounded at five seconds; the complete request
and response have a ten-second bound to tolerate short server delays.

Recruitment activity preferences retain their saved keys and off defaults, with
duplicate checkbox confirmation prompts removed. The Plugin Hub installation
disclosure remains required and unchanged. This candidate has not been published;
development-client gameplay acceptance and Plugin Hub review remain separate.

The published version 1.2 introduced exact-level completion evidence, including separate levels 8, 9, 10 and higher. Version 1.3 preserves that protocol. Lower levels are reported without a credit decision. Delivery requires a compatible bot advertising `doomEvidenceVersion: 1`; Doom assignments remain disabled. See [Doom evidence and live validation](DOOM_EVIDENCE.md). Production bot protocol support is deployed; natural Doom/Awakened gameplay validation remains outstanding.

The sidebar retains a compact connection indicator, including registration and
required-update guidance. A report receipt alone never advances the KC bar.
The packaged build identifier for this candidate is `collector-v2-release-1`
(`ReleaseInfo.BUILD`), a release identifier rather than a Git hash. Version and build
are also visible in the collapsed About Huntmaster settings section.
Reports retain their original capture-policy revision across retries.

Future unknown entries in the bot's optional capture-window policy are ignored;
valid windows and capabilities for packaged bosses remain available. Existing
known-value, expiry, payload-size and minimum-protocol limits still apply. This
does not add new encounters or permit downloaded code/client reads. Unchanged
visible Hunt/rank values skip redundant sidebar repaint work, and missing image
lookups are cached within the existing bounded caches. The sidebar layout is unchanged.

The collector-v2 implementation reports supported encounters independently of the assigned boss, including when there is no assignment. Reports retain the actual encounter and its own counter; the bot decides assignment matching, harder-to-lower substitutions, credit and rewards. Unassigned observations never award task progress. No player chat text is collected. See PRIVACY.md for disclosure.

Counter notifications use complete encounter identities and reviewed aliases, including when the counter value is unreadable. A Corrupted Gauntlet or Phosani's Nightmare notification cannot also create a diagnostic for the lower variant. Independent notifications still retain separate captures. Unknown notification formats are ignored rather than attributed through a partial boss-name match.

Find Huntmaster through RuneLite's plugin search. Its gear/settings panel displays the Bosscape invite, community description and boss-verification purpose. The Huntmaster sidebar displays your server-provided assignment, progress, reward and connection status. Manage assignments using Open Huntmaster in Discord. Join [Bosscape Discord](https://discord.gg/Bosscape).

Register your RuneScape name with Huntmaster in Bosscape Discord. While you remain a member, the plugin automatically matches your logged-in RSN with that registration. No private linking key is needed. Discord membership must be confirmable before the bot accepts reports. RSN matching does not cryptographically prove sender/account ownership.

Enabling the plugin enables its connection and evidence collection. Disable it to stop communication and new tracking. Previously saved reports remain available when re-enabled. Unregistered accounts send a registration lookup, not accepted encounter/account reports. See [PRIVACY.md](PRIVACY.md) for the transmitted fields and storage behavior.

The plugin sends your IP address through normal HTTPS traffic and submits your RSN, relevant account requirements, boss counters and bounded encounter evidence to a third-party Bosscape service not controlled or verified by RuneLite developers. The submission marker includes the third-party installation warning; no separate sharing toggle or key flow is added.

## Full-clear chest evidence

Reports include killed-component flags for Barrows and Moons of Peril alongside chest evidence. Huntmaster decides credit: all six brothers or all three Moons must be represented for that chest. Eligibility and validation status are maintained by the bot. The plugin does not independently approve KC.

## Connection and reliability

Public builds use https://huntmaster.bosscape.com. The packaged release gate is enabled after successful public-client registration, assignment, persistence and completion checks; it never falls back to a player's localhost. Development `run` uses http://127.0.0.1:8787. Redirects are disabled and requests are restricted to the packaged origin. A rejected registration shows one login-safe notice and retries; membership/Discord failures do not grant credit.

The plugin sends observations through `/api/runelite/observations` and reads bot decisions from `/api/runelite/verification-status`. It does not create new locally verified KC requests. Existing saved legacy events retain their original delivery route and IDs. Phosani's Nightmare has a 20-tick default capture window; regular Nightmare has at least 24 ticks. The new collector-v2 reports wait in the queue until the bot advertises support in assignment responses. The production bot supports this protocol; natural gameplay acceptance remains a separate validation step.

Pending kills retain their event/assignment IDs across retries and restarts. During a detected connection outage, collection has a ten-minute grace period, then pauses new tracking while preserving saved reports. The bot allows 60 seconds for delayed evidence before warning about an unconfirmed personal counter increase. Collector v2 continues collecting after verification warnings so failures remain diagnosable; outage and backlog limits still apply. Death-only observations do not count as personal failures. The plugin polls decisions every ten seconds with an active assignment; relog starts a fresh warning session. The bot rejects outdated assignments and duplicate credit. Beta evidence support is not equivalent to strict verification for every boss.

Collection health is sent at most once per minute while logged in, registered and connected to a compatible bot: session counters for captured signals/reports, acknowledged deliveries, capture/delivery errors, queue size and last captured observation time. These diagnose missing delivery without awarding credit. Supported collection remains limited to the packaged boss identities, aliases and game-state sources; unknown game content may still need a plugin release.

Assignment responses may extend capture windows for packaged bosses using expiring data (10–128 ticks, at most 64 entries, expiry within 24 hours). Invalid/expired settings fall back to packaged defaults. The server cannot supply code, endpoints, arbitrary varps, NPC aliases or chat patterns. Longer windows delay report finalization; immediate delivery after finalization remains unchanged.

While logged in, healthy connections check service health every 30 seconds and assignments every five seconds. Logged-out clients make no routine requests. Bot verification decisions are polled only with an active assignment; account snapshots are sampled at most once per minute. Finalized evidence attempts delivery immediately through the existing single-request queue. Reports that finalize while another request is in flight wait for the five-second worker; acknowledgements do not rapidly drain old backlogs. Failed reports retry after at least 60 seconds once connection and assignment state recover. Responses are bounded to 64 KiB. Retries retain report IDs, and temporary rate limits do not discard legacy events.

New collection pauses when the saved report backlog reaches 1,000 reports or 8 MiB, and resumes as delivery clears space. Already captured evidence is preserved, so those thresholds are collection limits rather than hard limits on existing saved data. Checkpoint writes drain on shutdown. All plugin filesystem access uses RuneLite's `net.runelite.client.util.Filepath` API; packaged resources use classpath streams.

## Development and submission

The plugin targets Java 11 bytecode. Run `./gradlew test build` for automated checks and `./gradlew run` for a client using a local bot. On Windows use `gradlew.bat`. Keep RuneLite's `latest.release` dependency current: run `./gradlew --refresh-dependencies test build` before testing after a RuneLite update. Offline launches reuse cached dependencies and can run an older client; an outdated development client can break unrelated native RuneLite features. Jagex Account users should follow [RuneLite's login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts). Only the player can validate game behavior.

`./gradlew runPublic` launches a development client against the packaged endpoint without the localhost override, once its release gate is enabled. The plugin uses a BSD-2-Clause license.

`./gradlew verifyPackagedResources` checks the built release JAR and also runs with
`check`/`build`. It verifies packaged plugin classes, the RuneLite entry point,
boss/rank artwork and Java 11 bytecode without development-folder fallback. Use
`-PpackagedJar=/path/to/plugin.jar` to check a separately built artifact. See
[release preparation](RELEASING.md) for the standard-package/submission distinction.

The Doom/Awakened protocol is part of the published 1.2 baseline; the 1.3 UI/settings/hardening update is not yet published. Relevant encounter, account-switch, retry and old-bot protections remain covered by regression checks. Natural Doom/Awakened timing remains a separate validation limit. The authoritative [Plugin Hub descriptor](https://github.com/runelite/plugin-hub/blob/master/plugins/huntmaster) identifies the published commit. An update request binds that descriptor to the exact reviewed candidate commit; this repository keeps no duplicate marker.

The bot independently checks published HiScores totals for accepted plugin credit. Ordinary progression remains immediate; unknown/delayed totals remain uncorroborated and contradictions request staff review. Ask Huntmaster staff if you need a verification record review. These checks add confidence, not cryptographic gameplay proof. High-value reward verification requires independent support or recorded staff approval under a separate reward policy.

## Group recruitment alerts (1.1)

Expand Raids, Wilderness Bosses, God Wars Dungeon, Other Bosses, or Skilling & Minigames in Huntmaster settings and check the activities you want. All 32 activities start unchecked, including all four raids: Chambers of Xeric, The Fractured Archive, Theatre of Blood and Tombs of Amascut. These are in-game chat alerts from the Huntmaster Discord bot's LFG / Queue system; activity selections do not affect Hunt tracking.

Enable recruitment alerts is the master switch. It defaults on to preserve existing selections and behavior, and turning it off preserves every selected activity. Re-enabling starts at the live edge, without replaying missed announcements. Editing a nonempty activity selection keeps the current feed cursor and duplicate history; a pending response uses the latest choices. All selections remain opt-in and do not open repeated confirmation dialogs. Display settings are first, activity lists are alphabetized within their existing categories, About Huntmaster is collapsed, and the data-sharing notice remains visible.

Successful queue recruitment pings, including manual Active Group pings and your own pings, produce: `Huntmaster: Someone is looking for members for Tombs of Amascut. Join through Bosscape Discord.` Multiple selected activities in one ping share one message. Polling runs every five seconds while logged in with at least one activity selected; it does not wait for combat to end. You need an RSN linked to an active Bosscape member, but no Huntmaster assignment.

Login, reconnect, account/profile changes, disabling all activity selections and the recruitment master switch start a new live-edge session; missed alerts are not replayed. Other activity preference edits preserve the live feed. Failed sends, silent edits, previews and duplicate deliveries do not generate additional messages. Very old pings and groups that have stopped recruiting are omitted.

### Assignment dashboard

The sidebar reuses existing assignment requests; it adds no routine polling loop. Display settings include Show hunt overlay, which defaults on, and Visibility. The default After credited kill mode preserves existing behavior: show after a newly server-credited kill from the current session, then hide after 20 minutes without credit. While Hunt active also shows progress before the session's first credited kill. Both modes display only server-confirmed progress and hide while disconnected, on completion or logout. Use RuneLite overlay positioning controls to move it. These controls add no data transmission; the bot remains authoritative for KC and rewards. The settings update does not alter the Current Hunt/rank sidebar or add a Groups Recruiting view.

The sidebar displays server-authoritative assignment information. Use **Open Huntmaster in Discord** to manage assignments in Bosscape. This navigation only opens the existing Discord destination; it does not request, reroll or cancel an assignment. The sidebar adds no authorization credentials or additional routine polling.

After the server confirms completion of the assignment observed in this session, the sidebar retains a Hunt Complete card with the actual awarded points. It clears on a new assignment, logout, world hop, account change, plugin reload or client restart. The overlay disappears immediately on completion. This display is memory-only, uses existing responses, and requires a bot that supplies completion metadata.

## Doom and Awakened encounter update (1.2)

Structured Doom completion observations are preserved. This update also reports
normal/awakened DT2 personal completion totals with optional bounded encounterVariant
metadata. Structured reports include canonical encounter, variant and Doom facts.
Version 1.3 removes the old local Tracking Status/Copy diagnostics display cache.
See [the exact bot contract](ENCOUNTER_VARIANT_CONTRACT.md) and [Doom evidence](DOOM_EVIDENCE.md).
The production bot and published 1.2 baseline support all three optional V1 capabilities. Natural Doom/Awakened gameplay validation remains outstanding. Doom credit is
still disabled. RuneLite observes; the bot decides Hunt credit, matching and rewards.
### Doom per-level evidence

The collector also observes fixed RuneLite per-level completion
counters. It reports raw before/after values and preserves Doom's exact level
notification; it makes no Hunt qualification decision. Existing Doom evidence
version 1 and aggregate observation remain. Additional `doomCounters` serialization
requires optional `doomCounterEvidenceVersion: 1` from a capable bot; older bots
receive the existing payload. See `DOOM_EVIDENCE.md` for the complete contract,
counter IDs, duplicate identity and remaining live-gameplay checks. This source
is included in the published 1.2 protocol; the current 1.3 candidate does not enable Doom Hunt credit.

# Huntmaster RuneLite Plugin

Huntmaster supplies own-account observations and boss encounter evidence to the Bosscape Huntmaster Discord Bot. The bot manages assignments, eligibility decisions, points and ranks. This is an extended-beta project. The initial [Plugin Hub submission](https://github.com/runelite/plugin-hub/pull/16780) was merged on September 28, 2026. The local update described below has not yet been published or reviewed for Plugin Hub release.

## Using Huntmaster

The sidebar's Tracking Status shows collection readiness, connection recovery, a full queue, or a required RuneLite plugin update. Its latest-report status distinguishes queued evidence, acknowledged receipt, confirmed credit and no new credit. A receipt alone never means a kill counted. Reports from earlier sessions are not shown as new activity.

Use **Copy diagnostics** when reporting a tracking problem. It copies the packaged build identifier, collector version, current collection state, queue size, latest report ID/outcome/reason and current/report capture-policy revisions. It excludes your RSN, credentials, raw chat and request bodies. Copying only writes to your local clipboard; you decide whether to share it. Report details clear on logout, profile change or plugin shutdown. This adds no polling or gameplay controls.

The packaged build identifier for this candidate is `collector-v2-diagnostics-1` (`CollectorDiagnostics.BUILD`). It is a release identifier, not a Git hash; maintainers must bump it for the next changed release candidate. Reports also carry the policy revision captured when their observation window started, so later server setting changes do not relabel older evidence.

The pending collector-v2 update reports supported encounters independently of the assigned boss, including when there is no assignment. Reports retain the actual encounter and its own counter; the bot decides assignment matching, harder-to-lower substitutions, credit and rewards. Unassigned observations never award task progress. No player chat text is collected. See PRIVACY.md for disclosure.

Counter notifications use complete encounter identities and reviewed aliases, including when the counter value is unreadable. A Corrupted Gauntlet or Phosani's Nightmare notification cannot also create a diagnostic for the lower variant. Independent notifications still retain separate captures. Unknown notification formats are ignored rather than attributed through a partial boss-name match.

Find Huntmaster through RuneLite's plugin search. Its gear/settings panel displays the Bosscape invite, community description and boss-verification purpose. The Huntmaster sidebar displays your server-provided assignment, progress, reward and connection status. Manage assignments using Open Huntmaster in Discord. Join [Bosscape Discord](https://discord.gg/Bosscape).

Register your RuneScape name with Huntmaster in Bosscape Discord. While you remain a member, the plugin automatically matches your logged-in RSN with that registration. No private linking key is needed. Discord membership must be confirmable before the bot accepts reports. RSN matching does not cryptographically prove sender/account ownership.

Enabling the plugin enables its connection and evidence collection. Disable it to stop communication and new tracking. Previously saved reports remain available when re-enabled. Unregistered accounts send a registration lookup, not accepted encounter/account reports. See [PRIVACY.md](PRIVACY.md) for the transmitted fields and storage behavior.

The plugin sends your IP address through normal HTTPS traffic and submits your RSN, relevant account requirements, boss counters and bounded encounter evidence to a third-party Bosscape service not controlled or verified by RuneLite developers. The submission marker includes the third-party installation warning; no separate sharing toggle or key flow is added.

## Full-clear chest evidence

Reports include killed-component flags for Barrows and Moons of Peril alongside chest evidence. Huntmaster decides credit: all six brothers or all three Moons must be represented for that chest. Eligibility and validation status are maintained by the bot. The plugin does not independently approve KC.

## Connection and reliability

Public builds use https://huntmaster.bosscape.com. The packaged release gate is enabled after successful public-client registration, assignment, persistence and completion checks; it never falls back to a player's localhost. Development `run` uses http://127.0.0.1:8787. Redirects are disabled and requests are restricted to the packaged origin. A rejected registration shows one login-safe notice and retries; membership/Discord failures do not grant credit.

The plugin sends observations through `/api/runelite/observations` and reads bot decisions from `/api/runelite/verification-status`. It does not create new locally verified KC requests. Existing saved legacy events retain their original delivery route and IDs. Phosani's Nightmare has a 20-tick default capture window; regular Nightmare has at least 24 ticks. The new collector-v2 reports wait in the queue until the bot advertises support in assignment responses. This update requires the matching bot deployment and in-game acceptance before publication.

Pending kills retain their event/assignment IDs across retries and restarts. During a detected connection outage, collection has a ten-minute grace period, then pauses new tracking while preserving saved reports. The bot allows 60 seconds for delayed evidence before warning about an unconfirmed personal counter increase. Collector v2 continues collecting after verification warnings so failures remain diagnosable; outage and backlog limits still apply. Death-only observations do not count as personal failures. The plugin polls decisions every ten seconds with an active assignment; relog starts a fresh warning session. The bot rejects outdated assignments and duplicate credit. Beta evidence support is not equivalent to strict verification for every boss.

Collection health is sent at most once per minute while logged in, registered and connected to a compatible bot: session counters for captured signals/reports, acknowledged deliveries, capture/delivery errors, queue size and last captured observation time. These diagnose missing delivery without awarding credit. Supported collection remains limited to the packaged boss identities, aliases and game-state sources; unknown game content may still need a plugin release.

Assignment responses may extend capture windows for packaged bosses using expiring data (10–128 ticks, at most 64 entries, expiry within 24 hours). Invalid/expired settings fall back to packaged defaults. The server cannot supply code, endpoints, arbitrary varps, NPC aliases or chat patterns. Longer windows delay report finalization; immediate delivery after finalization remains unchanged.

While logged in, healthy connections check service health every 30 seconds and assignments every five seconds. Logged-out clients make no routine requests. Bot verification decisions are polled only with an active assignment; account snapshots are sampled at most once per minute. Finalized evidence attempts delivery immediately through the existing single-request queue. Reports that finalize while another request is in flight wait for the five-second worker; acknowledgements do not rapidly drain old backlogs. Failed reports retry after at least 60 seconds once connection and assignment state recover. Responses are bounded to 64 KiB. Retries retain report IDs, and temporary rate limits do not discard legacy events.

New collection pauses when the saved report backlog reaches 1,000 reports or 8 MiB, and resumes as delivery clears space. Already captured evidence is preserved, so those thresholds are collection limits rather than hard limits on existing saved data. Checkpoint writes drain on shutdown. All plugin filesystem access uses RuneLite's `net.runelite.client.util.Filepath` API; packaged resources use classpath streams.

## Development and submission

Use Java 11. Run `./gradlew test` for automated checks and `./gradlew run` for the local development client. Jagex Account users should follow [RuneLite's login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts). Only the player can validate game behavior.

`./gradlew runPublic` launches a development client against the packaged endpoint without the localhost override, once its release gate is enabled. The plugin uses a BSD-2-Clause license.

The collector-v2 changes are local and unpublished. Before release, test off-assignment encounters, CG during a normal Gauntlet task, mixed variant counters, unassigned collection, chest resets, logout/account changes, network recovery and older-bot queue retention. The existing [submission marker](submission/huntmaster.marker) is unchanged by this implementation pass.

The bot independently checks published HiScores totals for accepted plugin credit. Ordinary progression remains immediate; unknown/delayed totals remain uncorroborated and contradictions request staff review. In Bosscape Discord, open My Huntmaster and select **Verification Status** from the **History, verification and help** menu to view your verification record. These checks add confidence, not cryptographic gameplay proof. High-value reward verification requires independent support or recorded staff approval under a separate reward policy.

## Group recruitment alerts (1.1)

Expand Raids, Wilderness Bosses, God Wars Dungeon, Other Group Bosses, or Skilling Bosses & Activities in Huntmaster settings and check the activities you want. All 32 boxes start off: 31 current Bosscape queue activities and The Fractured Archive (planned). Recruitment alerts add no master switch, desktop notification or test button.

Successful queue recruitment pings, including manual Active Group pings and your own pings, produce: `Huntmaster: Someone is looking for members for Tombs of Amascut. Join through Bosscape Discord.` Multiple selected activities in one ping share one message. Polling runs every five seconds while logged in with at least one activity selected; it does not wait for combat to end. You need an RSN linked to an active Bosscape member, but no Huntmaster assignment.

Login, reconnect, account/profile changes and preference changes start at the live edge; missed alerts are not replayed. Failed sends, silent edits, previews and duplicate deliveries do not generate additional messages. Very old pings and groups that have stopped recruiting are omitted.

### Assignment dashboard

The sidebar reuses existing assignment requests; it adds no routine polling loop. The optional Show Assignment Progress Overlay setting defaults on and controls local rendering only, without additional data transmission. The native movable overlay displays only server-credited progress, becomes visible after a newly credited kill from the current session, and hides after 20 minutes without another credit, completion, replacement or logout. Use RuneLite overlay positioning controls to move it. The bot remains authoritative for KC and rewards.

The sidebar displays server-authoritative assignment information. Use **Open Huntmaster in Discord** to manage assignments in Bosscape. This navigation only opens the existing Discord destination; it does not request, reroll or cancel an assignment. The sidebar adds no authorization credentials or additional routine polling.

After the server confirms completion of the assignment observed in this session, the sidebar retains a Task Completed card with the actual awarded points. It clears on a new assignment, logout, world hop, account change, plugin reload or client restart. The overlay disappears immediately on completion. This display is memory-only, uses existing responses, and requires a bot that supplies completion metadata.

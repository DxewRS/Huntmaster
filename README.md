# Huntmaster RuneLite Plugin

Huntmaster supplies own-account observations and boss encounter evidence to the Bosscape Huntmaster Discord Bot. The bot manages assignments, eligibility decisions, points and ranks. This is an extended-beta project. The initial [Plugin Hub submission](https://github.com/runelite/plugin-hub/pull/16780) was merged on September 28, 2026. The local update described below has not yet been published or reviewed for Plugin Hub release.

## Using Huntmaster

Pending diagnostic update: encounter reports include bounded game-notification categories and personal counter snapshots for server-side investigation. Regular Nightmare's capture window is at least 24 ticks to include delayed evidence. The server alone decides credit; temporary boss holds may allow evidence collection while pausing credit. No player chat text is collected. See PRIVACY.md for disclosure.

Find Huntmaster through RuneLite's plugin search. Its gear/settings panel displays the Bosscape invite, community description and boss-verification purpose. The Huntmaster sidebar displays your server-provided assignment, progress, reward and connection status. Manage assignments using Open Huntmaster in Discord. Join [Bosscape Discord](https://discord.gg/Bosscape).

Register your RuneScape name with Huntmaster in Bosscape Discord. While you remain a member, the plugin automatically matches your logged-in RSN with that registration. No private linking key is needed. Discord membership must be confirmable before the bot accepts reports. RSN matching does not cryptographically prove sender/account ownership.

Enabling the plugin enables its connection and evidence collection. Disable it to stop communication and new tracking. Previously saved reports remain available when re-enabled. Unregistered accounts send a registration lookup, not accepted encounter/account reports. See [PRIVACY.md](PRIVACY.md) for the transmitted fields and storage behavior.

The plugin sends your IP address through normal HTTPS traffic and submits your RSN, relevant account requirements, boss counters and bounded encounter evidence to a third-party Bosscape service not controlled or verified by RuneLite developers. The submission marker includes the third-party installation warning; no separate sharing toggle or key flow is added.

## Pending full-clear chest update

The local, unreleased update reports the killed-component flags for Barrows and Moons of Peril alongside chest evidence. Huntmaster decides credit: all six brothers or all three Moons must be represented for that chest. The bot currently excludes both from random assignments and marks validation unavailable pending release and live reset/logout testing. Staff assignment commands remain available. The plugin does not independently approve KC.

## Connection and reliability

Public builds use https://huntmaster.bosscape.com. The packaged release gate is enabled after successful public-client registration, assignment, persistence and completion checks; it never falls back to a player's localhost. Development `run` uses http://127.0.0.1:8787. Redirects are disabled and requests are restricted to the packaged origin. A rejected registration shows one login-safe notice and retries; membership/Discord failures do not grant credit.

The pending 1.1 update sends observations to the bot through `/api/runelite/observations` and reads bot decisions from `/api/runelite/verification-status`. It does not create new locally verified KC requests. Existing saved legacy events retain their original delivery route and IDs. Phosani's Nightmare has a 20-tick capture window for delayed KC messages. This update requires the matching bot deployment and in-game acceptance before publication.

Pending kills retain their event/assignment IDs across retries and restarts. During a detected connection outage, collection has a ten-minute grace period, then pauses new tracking while preserving saved reports. The bot decides kill credit from the observations and controls verification warnings and the three-failure pause. It allows 60 seconds for delayed evidence before warning about an unconfirmed personal counter increase. Death-only observations do not count as personal failures. The plugin polls decisions every ten seconds while logged in; relog starts a fresh warning session. The bot rejects outdated assignments and duplicate credit. Beta evidence support is not equivalent to strict verification for every boss.

While logged in, healthy connections check service health every 30 seconds and assignments every five seconds. Logged-out clients make no routine requests. Bot verification decisions are polled only with an active assignment; account snapshots are sampled at most once per minute. Finalized evidence attempts delivery immediately through the existing single-request queue. Reports that finalize while another request is in flight wait for the five-second worker; acknowledgements do not rapidly drain old backlogs. Failed reports retry after at least 60 seconds once connection and assignment state recover. Responses are bounded to 64 KiB. Retries retain report IDs, and temporary rate limits do not discard legacy events.

New collection pauses when the saved report backlog reaches 1,000 reports or 8 MiB, and resumes as delivery clears space. Already captured evidence is preserved, so those thresholds are collection limits rather than hard limits on existing saved data. Checkpoint writes drain on shutdown. All plugin filesystem access uses RuneLite's `net.runelite.client.util.Filepath` API; packaged resources use classpath streams.

## Development and submission

Use Java 11. Run `./gradlew test` for automated checks and `./gradlew run` for the local development client. Jagex Account users should follow [RuneLite's login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts). Only the player can validate game behavior.

`./gradlew runPublic` launches a development client against the packaged endpoint without the localhost override, once its release gate is enabled. The plugin uses a BSD-2-Clause license.

Version 1.1 is prepared locally and has not been published. Before release, test recruitment alerts in-game: selected activities, your own and manual Active Group pings, opt-out, duplicate protection, and no replay after login/reconnect. Then commit the release and update the commit in [submission/huntmaster.marker](submission/huntmaster.marker) for Plugin Hub submission, retaining its installation warning. The marker currently references the earlier release.

The bot independently checks published HiScores totals for accepted plugin credit. Ordinary progression remains immediate; unknown/delayed totals remain uncorroborated and contradictions request staff review. In Bosscape Discord, open My Huntmaster and select **Verification Status** from the **History, verification and help** menu to view your verification record. These checks add confidence, not cryptographic gameplay proof. High-value reward verification requires independent support or recorded staff approval under a separate reward policy.

## Group recruitment alerts (1.1)

Expand Raids, Wilderness Bosses, God Wars Dungeon, Other Group Bosses, or Skilling Bosses & Activities in Huntmaster settings and check the activities you want. All 32 boxes start off: 31 current Bosscape queue activities and The Fractured Archive (planned). Recruitment alerts add no master switch, desktop notification or test button.

Successful queue recruitment pings, including manual Active Group pings and your own pings, produce: `Huntmaster: Someone is looking for members for Tombs of Amascut. Join through Bosscape Discord.` Multiple selected activities in one ping share one message. Polling runs every five seconds while logged in with at least one activity selected; it does not wait for combat to end. You need an RSN linked to an active Bosscape member, but no Huntmaster assignment.

Login, reconnect, account/profile changes and preference changes start at the live edge; missed alerts are not replayed. Failed sends, silent edits, previews and duplicate deliveries do not generate additional messages. Very old pings and groups that have stopped recruiting are omitted.

### Assignment dashboard (pending release)

The sidebar reuses existing assignment requests; it adds no routine polling loop. The optional Show Assignment Progress Overlay setting defaults on and controls local rendering only, without additional data transmission. The native movable overlay displays only server-credited progress, becomes visible after a newly credited kill from the current session, and hides after 20 minutes without another credit, completion, replacement or logout. Use RuneLite overlay positioning controls to move it. The bot remains authoritative for KC and rewards.

The sidebar displays server-authoritative assignment information. Use **Open Huntmaster in Discord** to manage assignments in Bosscape. This navigation only opens the existing Discord destination; it does not request, reroll or cancel an assignment. The sidebar adds no authorization credentials or additional routine polling.

After the server confirms completion of the assignment observed in this session, the sidebar retains a Task Completed card with the actual awarded points. It clears on a new assignment, logout, world hop, account change, plugin reload or client restart. The overlay disappears immediately on completion. This display is memory-only, uses existing responses, and requires a bot that supplies completion metadata.

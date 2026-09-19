# Huntmaster RuneLite Plugin

Huntmaster supplies own-account observations and boss encounter evidence to the Bosscape Huntmaster Discord Bot. The bot manages assignments, eligibility decisions, points and ranks. This is an extended-beta project; Plugin Hub approval remains pending.

## Using Huntmaster

Find Huntmaster through RuneLite's plugin search. Its gear/settings panel displays the Bosscape invite, community description and boss-verification purpose. Huntmaster adds no sidebar icon. Join [Bosscape Discord](https://discord.gg/Bosscape).

Register your RuneScape name with Huntmaster in Bosscape Discord. While you remain a member, the plugin automatically matches your logged-in RSN with that registration. No private linking key is needed. Discord membership must be confirmable before the bot accepts reports. RSN matching does not cryptographically prove sender/account ownership.

Enabling the plugin enables its connection and evidence collection. Disable it to stop communication and new tracking. Previously saved reports remain available when re-enabled. Unregistered accounts send a registration lookup, not accepted encounter/account reports. See [PRIVACY.md](PRIVACY.md) for the transmitted fields and storage behavior.

The plugin sends your IP address through normal HTTPS traffic and submits your RSN, relevant account requirements, boss counters and bounded encounter evidence to a third-party Bosscape service not controlled or verified by RuneLite developers. The submission marker includes an installation-warning proposal for RuneLite review; no separate sharing toggle or key flow is added.

## Connection and reliability

Public builds use https://huntmaster.bosscape.com. The packaged release gate is enabled after successful public-client registration, assignment, persistence and completion checks; it never falls back to a player's localhost. Development `run` uses http://127.0.0.1:8787. Redirects are disabled and requests are restricted to the packaged origin. A rejected registration shows one login-safe notice and retries; membership/Discord failures do not grant credit.

Pending kills retain their event/assignment IDs across retries and restarts. During a detected connection outage, collection has a ten-minute grace period, then pauses new tracking while preserving saved reports. Three consecutive verification failures can pause tracking until relog. The bot rejects outdated assignments and duplicate credit. Beta evidence support is not equivalent to strict verification for every boss.

## Development and submission

Use Java 11. Run `./gradlew test` for automated checks and `./gradlew run` for the local development client. Jagex Account users should follow [RuneLite's login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts). Only the player can validate game behavior.

`./gradlew runPublic` launches a development client against the packaged endpoint without the localhost override, once its release gate is enabled. See [SUBMISSION_CHECKLIST.md](SUBMISSION_CHECKLIST.md) for pending review and release checks. The plugin uses a BSD-2-Clause license.

See [CLEANUP_AUDIT.md](CLEANUP_AUDIT.md) for the code cleanup, regression coverage, and pending player checks.

The bot independently checks published HiScores totals for accepted plugin credit. Ordinary progression remains immediate; unknown/delayed totals remain uncorroborated and contradictions request staff review. `/huntmaster-verification` shows your status in Bosscape. These checks add confidence, not cryptographic gameplay proof. High-value reward verification requires independent support or recorded staff approval under a separate reward policy.

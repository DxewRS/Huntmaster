# Huntmaster data disclosure

Enabling Huntmaster connects to the Bosscape Huntmaster Discord Bot at https://huntmaster.bosscape.com. Communication is part of the plugin; disable the plugin to stop requests and new collection. No game passwords, Discord tokens, or linking keys are required.

The service and its network providers receive your IP address through normal HTTPS connections. Registration checks send the logged-in RuneScape name. Only registered accounts linked to an active Bosscape member may send accepted kill, encounter or account reports. An unregistered account can still send its name for a registration check, plus service health requests.

Reports include your RSN, assigned boss, event/assignment IDs, timestamps, detector versions, kill/completion counters and bounded evidence signal timing. Loot evidence reports attribution/presence rather than an inventory or item list. Account snapshots include relevant quest/miniquest states, Slayer task and kills remaining, base Slayer/Firemaking/Fishing levels and account type. Unknown observations remain unknown.

The plugin does not transmit screenshots, unrelated chat, nearby player names, locations, equipment, bank contents or passwords. Parsed game messages become structured counter/completion signals; raw chat is not included in encounter records.

Saved reports and counter checkpoints remain in RuneLite configuration/storage while disabled, for later delivery. Diagnostic reports have bounded age/capacity limits; pending credit candidates retain their original IDs until processed. Local filenames encode identifiers for path safety; this is not encryption. The bot may store reports and publish encounter summaries in Bosscape's development channel for verification review. Leaving Bosscape removes the active RSN registration; this does not erase permanent progression/history or previously collected evidence.

RSN matching checks registration and membership, not sender authentication or cryptographic game-account ownership. Kill reports still undergo the bot's evidence, assignment and duplicate checks. This plugin remains in beta; different bosses have different verification maturity.

The bot also queries public HiScores to corroborate credited totals and stores integrity receipts, baselines/published totals, review flags and staff decisions for audit. These records currently persist beyond encounter-report retention. This adds no extra plugin telemetry; ordinary progression does not wait for publication. High-value reward verification requires corroboration or recorded staff approval under a separate reward policy.

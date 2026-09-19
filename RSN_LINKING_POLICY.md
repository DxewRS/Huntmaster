# Current linking policy - 2026-09-18

Huntmaster uses keyless linking. The RuneScape name reported by the plugin must match an active registration owned by a current member of Bosscape (`HUNTMASTER_GUILD_ID`). There is no player key entry, issuance, or renewal flow, and `/huntmaster-link` is removed.

The API performs a Discord membership lookup for assignment, kill, encounter, and account-state requests. Discord outages or inconclusive membership checks fail closed. An unknown member removes the active registration. The health route is a public service-status check and contains no player data. Departure events and reconciliation remain enabled.

This model verifies current Bosscape membership and registration, but it is not cryptographic proof that the sender owns the RSN. A custom client can forge a matching RSN. Assignment identity, evidence, and duplicate checks reduce abuse but do not establish sender ownership.

Public builds use the fixed HTTPS origin `https://huntmaster.bosscape.com`; redirects are disabled and public launches never fall back to localhost. The public release gate and keyless membership checks are active. An obsolete saved `pluginCredential` value is deleted on startup and is not used.

The public client path was live-tested on September 18, 2026 with a Tempoross assignment: registration and assignment lookup worked, 1/2 progress survived disable/re-enable without duplicate credit, and the second completion awarded exactly 10 points.

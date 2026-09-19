# Account snapshot implementation - 2026-09-17

The RuneLite plugin sends minimal own-account observations once per minute after login settles, only when its authenticated bot connection confirms registration. POST /api/runelite/account-state uses the existing private credential and RSN ownership checks. The bot validates the schema, rejects stale/future/out-of-order snapshots, and holds accepted state in memory for up to five minutes. Snapshots never award KC or points.

Implemented observations: base Slayer/Firemaking/Fishing levels, current Slayer task including the boss-task subtable, kills remaining, relevant quest/miniquest completion, Fairytale II start, His Faithful Servants start, and account type. Quest names unavailable in the current API remain unknown. Existing bot assignment eligibility reads this fresh state. Unknown collection entries and unknown Slayer levels now remain unknown instead of being treated as negatives.

Remaining observation work: reliable Hespori readiness, bank-aware dark totem/Mimic availability, and brittle-key roof unlock. These remain unknown rather than inferred from absence in inventory. Verify quest-name aliases and Slayer task labels during continued beta; snapshots are observations, not cryptographic proof of the game account.

Public origin is prepared as https://huntmaster.bosscape.com with publicReady=false in the packaged resource. Development still uses loopback. Activate only after Cloudflare domain activation, protected tunnel routing to 127.0.0.1:8787, HUNTMASTER_PLUGIN_AUTH_REQUIRED=true, and external authentication checks. DNS registration alone does not create this connection.

Deferred owner checks: Discord linking/reissue/revoke, account departure and RSN changes, invalid credentials, logout/relogin, saved-kill delivery and old assignment rejection, and live account-snapshot observations. No additional boss sampling required for these code changes. In-game confirmation and Plugin Hub reviewer approval remain outstanding.

Submission review: Java 11 compilation and existing tests pass; source scan found none of Thread.sleep, ProcessBuilder, reflection, HttpURLConnection, or java.net.http. This is a limited static review, not full approval. Third-party communication disclosure must be reviewed with RuneLite. Owner requires plugin enable/disable as the sole communication switch; no separate player sharing toggle was added. Keep staging/build artifacts out of submission and review all new untracked source files before committing.

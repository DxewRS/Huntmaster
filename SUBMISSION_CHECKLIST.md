# Submission checklist - 2026-09-18

## Reviewer correction - September 19, 2026

- Local file operations converted to RuneLite `Filepath`, with lazy worker-side directory initialization and built-in legacy-directory migration.
- Java 11 build and 89 isolated JUnit tests passed against RuneLite 1.12.39; real-player data was not touched.
- Owner confirmed the requested in-game checkpoint/restart check on September 19, 2026. The submitted commit/marker has not been updated.
- See [FILEPATH_REVIEW.md](FILEPATH_REVIEW.md) for migration behavior, test evidence, rollback considerations and the remaining submission steps.

## Prepared locally

- Own-account data disclosure and current keyless registration instructions.
- Proposed Plugin Hub installation warning in submission/huntmaster.marker.template; no separate sharing toggle.
- Fixed HTTPS origin, disabled redirects and masked/no-key migration cleanup.
- Registration rejection notice limited to one per failed registration period; normal polling retries continue.
- Existing async network/disk paths, shutdown cancellation, minimal structured encounter records and Java 11 build.
- Public-origin development launch task runPublic, distinct from local run.
- Membership requests coalesce while in flight; no stale positive membership cache. Discord outages fail closed.
- BSD-2 license and actual plugin metadata/class name.
- Public release gate enabled after a `runPublic` Tempoross assignment test confirmed registration, 1/2 progress persistence, no disable/re-enable duplicate, exact 2/2 completion and one 10-point reward.
- Clean Java 11 `gradlew clean test` build passed on September 18, 2026.
- Release-tree scan found no credentials, local databases, logs or generated classes selected for source control.

## Required before publishing a submission

- Run final checks against the exact release commit, include new untracked source/tests/resources and exclude secrets, local databases and generated artifacts.
- Review the public repository URL: the current remote is https://github.com/DxewRS/Huntmaster.git. Confirm it is the intended plugin repository and contains the plugin project at its root.
- Replace the marker commit placeholder with the full release hash. Copy the marker to plugin-hub/plugins/huntmaster in the submission fork; the local template alone does not install a Hub warning.
- Submit the public repository/commit for RuneLite review. Approval is not guaranteed by local checks.

## Explicit remaining beta limitations

- RSN is registration matching, not sender authentication; forged reports are possible.
- Boss verification maturity varies; specific unknown account unlock observations remain pending.
- Doom of Mokhaiotl is observation-only and disabled in the bot assignment pool until its counter semantics are confirmed.
- Group/shared-account and broader detector edge-case live checks remain deferred for extended beta. Owner does not need to kill every boss.
- Bot privacy/data-retention administration and every UI evidence label still need broader review; README does not promise automatic evidence erasure on departure.

Official references: https://github.com/runelite/plugin-hub, https://github.com/runelite/runelite/wiki/Information-about-the-Plugin-Hub, https://github.com/runelite/runelite/wiki/Plugin-Hub-Review.

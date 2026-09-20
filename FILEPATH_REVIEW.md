# Filepath reviewer correction — September 19, 2026

Status: implemented locally; automated checks passed and owner confirmed the requested in-game check. Submission update pending.

The reviewer requested that file I/O use RuneLite's `Filepath` instead of direct Java filesystem APIs. The production scan covered all 28 Java source files, tests, resources, build configuration and storage call sites. The implementation changes are limited to checkpoint storage and its construction/descriptor, plus isolated tests.

## Implementation

- `HuntmasterPlugin` declares `internalName = "huntmaster"` and `legacyDataDirectory = "huntmaster"`.
- The checkpoint store receives a lazy directory supplier calling `getPluginDirectory().join("kc-baselines")`. Directory lookup/migration first runs on the existing storage worker, not during construction or on the client thread. Failed directory lookup is not cached, permitting a later retry.
- `KcBaselineStore` uses `Filepath` for path construction, existence/size checks, input streams, directory creation, temporary files, writes, file-channel acquisition, moves and cleanup. No production `Filepath.Unchecked` calls were added.
- Filenames still use URL-safe Base64 of profile plus boss, and values remain UTF-8 decimal totals. Reads are capped at 17 bytes to reject files exceeding the existing 16-byte limit even if a file changes during reading.
- Writes preserve the previous temporary-file, force-to-disk, atomic replacement and non-atomic fallback sequence. Handles close before replacement, and temporary files are cleaned on failure.
- Existing lifecycle/profile checks around asynchronous callbacks and nonblocking worker shutdown remain intact.
- ConfigManager-managed pending events, encounter reports, assignment caches and preferences retain their keys/formats. The packaged endpoint resource is a classpath resource, not arbitrary filesystem access. Detector logic and the bot API are unchanged.

## Migration behavior and rollback

RuneLite moves `.runelite/huntmaster` to `.runelite/plugin-data/huntmaster` when the old directory exists and the destination does not. Thus `kc-baselines` and its filenames are preserved. If both directories exist, RuneLite uses the destination and leaves the old directory untouched; it does not merge them. This behavior is verified with the actual plugin class and RuneLite implementation in isolated tests.

Do not pre-create the destination in an installation with legacy data merely to prepare migration. Before the first real-client test, preserve a backup of the Huntmaster checkpoint folder while the plugin is disabled. If both folders exist, inspect them before choosing a manual recovery action; do not automatically overwrite either copy. A source downgrade alone does not reverse the folder migration: the previous version reads the old location. Any data restoration must preserve checkpoints saved after the upgrade.

No real-player directory was migrated or changed during this work. The existing untracked submission marker was left untouched. No commit, push, reviewer message or Plugin Hub revision was submitted.

## Verification

- Java 11 compilation and production jar build passed against resolved RuneLite client **1.12.39**.
- **89 JUnit tests passed; zero failures, errors or skipped tests.** Eight additional cases cover invalid checkpoints, failed replacement cleanup, lazy worker initialization and retry, encoded path confinement, legacy migration/restart, both-directory preservation, fresh installation, and blocked migration/retry.
- Existing restart, profile separation and latest-total behavior remain tested.
- Test tasks receive a separate `user.home` under the build directory. Migration tests refuse filesystem setup if the isolation properties do not match RuneLite's initialized home. Only test fixtures use direct Java filesystem APIs/`Filepath.Unchecked`; they are outside the production source set.
- Production-source scan found no direct `Files`, `Path`, `RUNELITE_DIR`, `FileChannel.open`, file constructors or `Filepath.Unchecked` operations. Java file-option constants and handles returned by RuneLite remain appropriate.
- The first test attempt hit a Windows lock in the old build output. A fresh build location was used via ignored `.gradle/filepath-review.init.gradle`, containing `allprojects { layout.buildDirectory = file('build/filepath-review') }`. This produced fresh compilation and test execution; it was not an up-to-date test result.
- Validation commands: `gradlew.bat -I .gradle/filepath-review.init.gradle test --offline --console=plain` and the same invocation with `jar dependencyInsight --dependency net.runelite:client --configuration compileClasspath`.
- The filesystem's unsupported-atomic-move fallback was retained but not fault-injected. Automated tests do not establish in-game behavior, universal filesystem guarantees or Plugin Hub acceptance.

Final readiness recheck: all compile, resource, jar and test tasks were explicitly rerun in fresh `build/filepath-final-check` output. All 89 tests passed again with zero failures/errors/skips. The production jar contains no test classes, local databases, logs, credential files or submission files, and the checkpoint class targets Java 11 (class version 55). The production filesystem/prohibited-API scan and `git diff --check` passed. No further runtime edits were needed after the owner's confirmation. Local changes are ready to commit; the existing submission still points to the previous commit until publication is completed.

## Player confirmation and submission

The owner confirmed the requested in-game check on September 19, 2026. This is owner-reported validation; no game input was automated. The steps below document that check and can be reused for future regression testing.

Offer a development launch with `./gradlew run`; on Windows use `gradlew.bat run`. For a Jagex Account follow [RuneLite's account instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

Use an eligible standard-NPC assignment that exercises local checkpoints. Confirm one kill adds exactly one KC, then disable/re-enable or restart RuneLite and verify the progress survives without an extra credit. A second kill should increment exactly once. If switching accounts, confirm another account does not inherit the first account's checkpoint. Developer launch success alone is not a passing game test.

After player confirmation, review the exact diff (including the new migration test), commit the correction, update the submitted marker to that tested commit, and reply to the reviewer explaining the Filepath conversion and preserved storage behavior.

## Primary references

- [Filepath API](https://static.runelite.net/runelite-client/apidocs/net/runelite/client/util/Filepath.html)
- [RuneLite Plugin directory implementation](https://raw.githubusercontent.com/runelite/runelite/master/runelite-client/src/main/java/net/runelite/client/plugins/Plugin.java)
- [PluginDescriptor migration attributes](https://raw.githubusercontent.com/runelite/runelite/master/runelite-client/src/main/java/net/runelite/client/plugins/PluginDescriptor.java)

These reviewer-requested APIs supersede the older direct `RuneLite.RUNELITE_DIR` storage guidance for this correction.

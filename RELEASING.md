# Huntmaster release preparation

Current candidate: version **1.3**, build **collector-v2-release-1**. The published
baseline is version 1.2 at `ccd4b442a82315da87ff57556ce8c847b0bf2ec5`.

## Verify the candidate

1. Refresh RuneLite dependencies after a client update, then run `gradlew.bat
   --refresh-dependencies test build` (or `./gradlew` on other systems). Keep
   `latest.release`; offline mode reuses cached versions. This candidate was
   checked with RuneLite 1.13.3. Java compilation targets Java 11 bytecode.
2. Use `gradlew.bat runPublic` for a dev client against the hosted HTTPS bot.
   `run` instead selects the local development bot. Preserve existing settings
   and saved evidence when changing clients.
3. Verify the standard-built JAR independently of source resource directories:
   boss/rank images, mappings, native resource loading and Java 11 bytecode.
   Run `gradlew.bat verifyPackagedResources` (or `./gradlew verifyPackagedResources`)
   to check the normal release JAR. This also runs through `check` and `build`.
   To check a separately standard-built or Hub-produced artifact, use
   `gradlew.bat verifyPackagedResources "-PpackagedJar=C:/path/to/plugin.jar"`.
   The checker links every packaged plugin class and the RuneLite entry point,
   rejects bundled test/dependency classes, and loads artwork from the JAR.
   Missing plugin classes/resources cannot fall back to the development folders.
   A local standard-template/package check is separate from actual Hub CI/review.
4. Reuse unchanged passing evidence. Inspect new failures or affected behavior;
   never label synthetic lifecycle checks as natural gameplay validation.
5. A normal-play soak observes memory/CPU of one identified dev-client JVM.
   Whole-process measurements include RuneLite and other plugins; they cannot
   prove Huntmaster-only resource use or absence of every memory leak.
6. Keep version/build descriptions, README, privacy disclosure, changelog and
   encounter contracts consistent. Preserve upstream installation disclosure,
   account isolation, original report IDs and compatibility with older clients.

## Commit and submit only when authorized

The authoritative descriptor is `plugins/huntmaster` in the RuneLite Plugin Hub
repository. Keep the plugin repository free of a duplicate descriptor that can
become stale. After the owner authorizes commit/push and Hub submission, update
that descriptor in a Plugin Hub fork to the exact reviewed plugin commit and
open a separate update PR. Preserve the existing installation warning.
Do not invent a future commit hash or treat a local build as Hub publication.

Include only source, tests, canonical resources/provenance and release docs.
Keep credentials, player data, generated logs, local backups and task evidence
out of the plugin commit. Keep requested rollback copies until the owner
approves their disposal; source rollback must not replace newer player data.

# Plugin cleanup review - 2026-09-18

Reviewed every production Java source file, the test sources and fixtures, Gradle setup, plugin metadata, packaged endpoint configuration, and resource/lifecycle handling. Existing research and release documents remain useful records rather than runtime code.

## Changes

- Removed unused completion flags, setters/getters, supporting-message metadata, a test-only counter-delta helper, and the inactive draft candidate registry. Evidence-only protection and development reference detectors remain covered by tests.
- Removed constant-return legacy key-linking checks and their redundant callback comparisons. The fixed-origin guard, removal of Authorization headers, lifecycle checks, and bot membership checks remain.
- Combined duplicate completion/activity tick, message, and verification handlers. Their distinct evidence kinds and verification methods remain unchanged.
- Routed registered bosses through a case-insensitive index. Paused game ticks refresh only the assigned detector; profile and assignment transitions still refresh the full registry.
- Replaced encounter-list copies and repeated removal searches with iterator removal.
- Cached quest-name lookups, the report timestamp formatter, and the Dagannoth version pattern. Replaced deprecated account-type access with the documented gameval varbit, including unranked group ironmen; unknown values are omitted.
- Removed unnecessary digit-stripping and duplicate evidence prefix processing. Null and oversized evidence messages are rejected safely.
- Encoded reports once, preserving explicit null counter baselines, without creating a new Gson per report or parsing the encoded report back into JSON.
- Cached queue byte/count accounting and saved the report queue only after structural changes. Saved kill candidates still survive diagnostic capacity limits and retention expiry. Runtime retry deadlines were already ignored when restoring saved entries.
- Avoided duplicate/decreasing checkpoint writes, preserving the highest observed local total.
- Reused the common asynchronous HTTP callback path for reports. Request generations now prevent cancelled pre-rejection callbacks from changing current state.
- Reset account-snapshot scheduling/in-flight state on startup and chest-window state when captures are cleared. Snapshots require the current player to match the synchronized registration.
- Updated obsolete test names and endpoint comments. The Bosscape settings description, Discord icon, keyless registration, saved-event formats and public release gate remain intact.

## Validation and limits

Java 11 compilation and the full regression suite pass: 83 tests, zero failures/errors/skips. Added regression checks for diagnostic capacity reclaimed by acknowledgement/expiry/clear, duplicate acknowledgements, UTF-8 payload byte limits, and malformed evidence input. Existing tests cover encounter ordering, explicit-null wire data, counter attribution, assignment boundaries, interrupted captures, durable credit, fixed-origin HTTP requests, and RuneLite event registration.

No FPS, latency, or in-game performance benchmark was performed. Compiler checks report an unchecked generic-varargs warning in RuneLite's development launcher call; production code uses no deprecated account-type API. No game inputs were automated, no live bot data was changed, and no Plugin Hub submission or release-gate change was made.

## Player confirmation still needed

Launch with `./gradlew run`. Jagex Account users should follow https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts.

- Confirm assignment synchronization and one eligible assigned kill produce exactly one bot increment.
- Where practical, check a completion/activity assignment retains its correct evidence kind and method.
- Disable/re-enable during a request; confirm account snapshots resume and saved reports survive.
- Check relog/account switching and membership rejection preserve saved reports and do not apply cancelled responses.
- Check the settings description and Discord icon, including icon removal on disable.

These live checks remain pending; the automated suite cannot establish game behavior or Plugin Hub approval.
## Final release cleanup — September 18, 2026

- Removed an injected `BosscapeSettings` field that was never read; the existing provider remains the RuneLite configuration registration point.
- Removed the development-only Giant Mole/Sarachnis detector branch, its production-only policy flag/factories, its obsolete test, and a stale observation-candidate document. Development and public launches now use the same detector registry; development mode changes only the fixed endpoint.
- Reduced internal detector classes to package scope and marked mutable-by-design detector/state classes final where no extension is supported.
- Renamed the local-launch system property from `developmentEvidence` to `developmentMode` to describe its remaining purpose accurately.
- Rechecked all 28 production classes: every class is referenced, and no forbidden process/reflection/native/input/network APIs or production `log.info` calls remain.
- Fresh Java 11 compilation and all 81 JUnit tests passed with zero failures. Source/resource secret scanning found only intentionally invalid security-test URL values. Generated build output remains ignored.

# Doom completion and counter evidence V1

Plugin version 1.2 at published commit `ccd4b442a82315da87ff57556ce8c847b0bf2ec5` introduced this evidence extension. The local version 1.3 candidate, build `collector-v2-release-1`, preserves it and is not yet published. Production bot support is deployed; natural Doom/Awakened gameplay validation remains outstanding. Doom assignments remain disabled. Future bot policy is one potential KC for each successfully completed level >=8. The plugin reports lower levels too and makes no credit decision.

## Capture and schema

The existing ChatMessage subscriber accepts only GAMEMESSAGE and SPAM completion notifications. The anchored parser accepts `Delve level: 8 duration: ...` and `Delve level: 8+ (9) duration: ...`, including supported personal-best suffixes and formatting tags. Extended notation preserves the integer in parentheses. Player chat, entering/death messages, malformed messages and full-run duration summaries are rejected. No raw text is retained.

The existing COMPLETION detector still samples `VarPlayerID.DOM_LEVEL_HIGHSCORES` on GameTick. Initial baseline and unchanged totals produce no new counter observation. A changed aggregate alone does not create successful Doom completion evidence. The local extension also reads nine fixed per-level counters, described below; no additional widgets or polling loop were added.

The existing encounter report gains this optional object:

```json
"doomState": {
  "version": 1,
  "completedLevel": 9,
  "extended": true,
  "source": "GAMEMESSAGE",
  "completionMessageObserved": true,
  "tickOffset": 0
}
```

Detector version becomes `doom-completion-observation-v1`. Outer schemaVersion 1 and collectorVersion 2 remain unchanged. Existing COMPLETION and COUNTER signals retain relative timing and raw before/after totals, including jumps or decreases. These observations are not independent cryptographic proof of gameplay.

Correlation reuses the existing per-boss channel, UUIDs and capture windows: Doom defaults to 10 ticks; existing server policy can extend this to 128. A lone unmatched message/counter pair can correlate in either order before expiry. Multiple unmatched candidates remain separate rather than guessing. Each clearly paired 8/9/10 completion gets its own report. Same-level duplicate messages within one tick are suppressed. Counter-only and message-only evidence remain distinguishable. At most eight pending records per boss are retained; overflow produces interrupted evidence.

Logout, reconnect, world hop and profile changes interrupt transient Doom captures and clear its counter baseline. Shutdown uses existing interruption/persistence. Finalized reports survive subsequent death or interruption. An interrupted capture is not equivalent to a fully captured completion; the future bot must evaluate it conservatively.

## Delivery and privacy

No new endpoint, polling loop, dependency, setting, thread or storage mechanism was added. Existing encounter delivery and persisted retries preserve the report UUID and serialized payload.

The production bot accepts bounded optional doomState and doomCounters and advertises `doomEvidenceVersion: 1` and `doomCounterEvidenceVersion: 1`, but Doom Hunt credit remains disabled. Older bots may reject unknown fields, so Doom delivery still requires this valid, unexpired collector-v2 capability. Compatible ingestion already exists; live validation and future credit policy remain separate. Missing/expired support holds these reports locally; other compatible reports remain deliverable. This does not bypass the bot's Doom rejection.

Existing diagnostic limits remain: 100 reports, 1 MiB, 24-hour retention and 8 KiB per report. Existing credit-candidate persistence and backlog protection remain unchanged. Unassigned Doom diagnostics are not retained indefinitely while waiting for a future bot release.

Normally a paired completion produces one report with a small structured object. Lower-level completions also produce observations; ambiguous ordering can produce separate reports. There is no continuous chat/widget/varp stream. Registration, outage collection window, queue admission and backpressure still apply. No raw chat, extra identity fields or secrets are added.

## Automated verification

`gradlew.bat --offline test build` passed: 150 tests, zero failures/errors/skips. The 17 new tests cover parsing, message types, level notation, correlation ordering/expiry/ambiguity, separate events, baseline safety, lifecycle reset, capacity, serialization, stable retries and capability-gated delivery. Existing tests were retained. Java 17 was used with the existing Java 11 target. No separate formatter/static-check task is configured; compilation, tests and diff whitespace checks passed.

An isolated-home development client started Huntmaster, passed detector registry validation, displayed the client and closed normally without gameplay. Ignored artifacts: `build/doom-build.log`, `build/doom-startup.log`. Startup contained a nonfatal RuneLite-core DEBUG reflection-access exception on Java 17, after which initialization completed; no WARN/ERROR entries were found. This is not live Doom validation.

## Manual live validation

Use the rebuilt local development client with debug logging and an eligible registered account. Doom need not be assigned. No bot credit is expected in this plugin-only stage. Inspect queued evidence before diagnostic retention expires; do not publish payloads containing RSNs.

After a capture window closes, development logging emits one line per message-bearing snapshot:

```text
Huntmaster Doom evidence: level=8 extended=false messageTickOffset=0 counterSignals=1 capture=complete report=<UUID>
```

The message offset may exceed zero when the counter arrived first. A counterSignals value of 1 is expected only when an actual counter change paired within the window; 0 exposes missing corroboration. This line precedes queue admission: verify its corresponding queued UUID/payload too. Capture complete means the window closed normally, not validated or credited. Allow the configured window to elapse.

| Test | Expected evidence/log |
| --- | --- |
| Complete 7 | completedLevel 7, extended false, COMPLETION signal, log level=7. Record actual counter presence; never invent corroboration. No credit. |
| Complete 8 | completedLevel 8, extended false, log level=8. Inspect actual counter before/after and relative timing in the same report if within the window. Missing support is a test finding. |
| Complete 9 | completedLevel 9, extended true, log level=9 extended=true. Inspect aggregate counter behavior. |
| Complete 8 and continue | Preserve 8's report. Successful 9/10 completions get distinct UUIDs/exact levels; merely entering a level creates no completion object. |
| Die during 8 | No level-8 completion object/log from death. Raw counter diagnostics are not success. Repeat after a finalized successful 8: that queued evidence survives. |
| Leave/reset during 8 | No success from entering/leaving/resetting. Aggregate changes remain counter evidence without synthetic completion. |
| Reload/reconnect | Pending captures may finalize as interrupted; finalized queued UUIDs remain. Initial sync creates no success or inferred lower-level completions. Later real completion starts fresh correlation. |
| API unavailable | During the existing permitted outage capture window, verify saved UUID/payload; after compatible support returns, retry preserves both. Beyond outage/backpressure limits collection pauses. An older bot without capability leaves Doom-bearing reports queued within retention. |
| Consecutive deep delves | Each actual successful 8+ level has its own report across levels/runs. Verify counters and same-tick duplicate suppression. Retry/restart must preserve existing UUIDs. |

Before enabling Doom: prove real message formats and counter timing at 7/8/9+, add separately authorized conservative bot level/counter credit validation (compatible ingestion already exists), verify end-to-end duplicate/retry behavior, and publish the required plugin through Plugin Hub. Automated tests establish behavior with supplied inputs, not live game timing or reviewer approval.

The pending DT2 variant contract and diagnostic additions are documented in ENCOUNTER_VARIANT_CONTRACT.md. This update does not enable Doom Hunt KC.

## Per-level observation extension (published in version 1.2)

RuneLite 1.13.1's resolved gameval source defines the following. Names describe
apparent purpose; these definitions do not establish live update timing or coverage.

| Constant | ID | Apparent purpose | Sampled by this plugin |
| --- | --- | --- | --- |
| TOTAL_DOM_LEVELS | 4807 | Total completed delve levels | No |
| DOM_LEVEL_1_COMPLETIONS | 4808 | Level 1 completions | Yes |
| DOM_LEVEL_2_COMPLETIONS | 4809 | Level 2 completions | Yes |
| DOM_LEVEL_3_COMPLETIONS | 4810 | Level 3 completions | Yes |
| DOM_LEVEL_4_COMPLETIONS | 4811 | Level 4 completions | Yes |
| DOM_LEVEL_5_COMPLETIONS | 4812 | Level 5 completions | Yes |
| DOM_LEVEL_6_COMPLETIONS | 4813 | Level 6 completions | Yes |
| DOM_LEVEL_7_COMPLETIONS | 4814 | Level 7 completions | Yes |
| DOM_LEVEL_8_COMPLETIONS | 4815 | Level 8 completions | Yes |
| DOM_LEVEL_8_PLUS_COMPLETIONS | 4816 | Combined extended-level completions | Yes |
| DOM_LEVEL_HIGHSCORES | 4824 | Aggregate hiscore level counter | Yes, unchanged |

There are no separate per-level constants here for 9, 10, etc. The recognized
`8+ (actual level)` notification remains the source of the exact extended level.
Depth, current-level and duration values are context, not successful-completion
counters; they are not newly sampled. No runtime behavior is inferred from names.

The existing collector-v2 observation gains a bounded optional sibling to
`doomState`, preserving all six original doomState fields and version 1:

```json
"doomCounters": {
  "version": 1,
  "counters": [{
    "source": "DOM_LEVEL_8_COMPLETIONS",
    "previous": 12,
    "current": 13,
    "tickOffset": 1
  }]
}
```

There is at most one per-level observation per report. Source is one of the nine
fixed constants above; totals are nonnegative Java integers and tickOffset follows
the existing bounded capture window. Jumps and decreases retain raw before/after
values; the plugin never labels them valid Hunt kills. Unchanged totals do not
emit observations. First synchronization, unavailable reads, paused collection,
account/RS profile/Hunt changes and reset baselines do not manufacture increments.

This is a sibling because a counter-only report must be expressible without
inventing doomState or completedLevel. Such a report contains a DIAGNOSTIC signal,
not a synthetic COMPLETION. Message-only reports retain the level without fake
corroboration. Per-level sources match messages by their fixed bucket, with 8+
matching reported levels 9 and above. Either arrival order works only when there
is one compatible pending record; multiple compatible records remain separate.
Aggregate observation and per-level observation may both join one unambiguous
message record. The existing windows, eight-record limit and interruption rules
remain. Duplicate raw source/before/after observations are suppressed in context;
repeated messages cannot reuse an already attached per-level observation.

### Capability and older bots

`doomEvidenceVersion: 1` is unchanged. Older bots may strictly reject
unknown fields, so they cannot simply ignore an optional doomCounters object.
The additional optional collector policy capability `doomCounterEvidenceVersion: 1`
is required to serialize it. Without that capability, the original Doom message
and aggregate payload remains deliverable with doomCounters omitted. The production
bot now implements parsing and advertises this capability. If capability expires
after serialization, the immutable queued
report waits; it is never rewritten on retry. Ordinary compatible reports continue
to deliver. Capability also requires supported, unexpired collector-v2 policy and
doomEvidenceVersion 1; unknown capability versions are unsupported.

### Future server evaluation and live gate

The bot must match RSN, Hunt generation, completed level and per-level bucket,
check capture/timing and positive delta, then durably deduplicate by account,
counter source and value as well as report UUID. Exact completed level is not
sufficient as a duplicate key: the 8+ counter is shared across exact deep levels.
No client qualification, reward or credit rule was added. Future threshold >=8
is unchanged; lower-level observations are still reported. Doom credit stays off.

Structured reports retain aggregate before/after, allowlisted per-level source and
before/after, and associated message/counter facts. Capture association is not
verification; raw jumps/decreases remain visible. The report envelope includes
the player's own RSN, but no raw chat or credentials. The old local clipboard/status
display cache is removed in version 1.3.

Deterministic tests cover levels 7, 8, 9 and 12, both arrival orders, aggregate
coexistence, baselines/pause/reset/unavailable values, unchanged/decreased/jumping
totals, duplicate messages/counters, ambiguous/wrong-level/expired evidence,
interruption, real lifecycle events, real plugin sampling, queue persistence,
retry identity, optional serialization and capability downgrade/expiry/privacy.
Live 7/8/9+ counter values and timing still require natural gameplay confirmation.
The original 1.2 review concerned only the plugin repository. The current 1.3
preparation has no commit/push or Hub publication authorization. Doom credit
activation remains separate; no live Doom gameplay validation is claimed.

Final local verification October 6, 2026: previous plugin baseline 178 tests;
clean standard build and complete suite now 214/214, failures 0, errors 0, skips 0.
Independent clean source-only export compiles and passes the same 214/214.
Whitespace check passes. Logs: `build-doom-per-level-clean.log` and
`build/doom-source-export-test.log`; exported source location is recorded in
`build/doom-source-export-path.txt`. These checks do not constitute live validation.

## Historical 1.2 release contract review

Production bot commit 2f1f8f0b0f8361a30e6e9b014ba114bc59c32147 advertises all three V1 capabilities. Its 180/180 test files pass. Plugin version 1.2 uses build collector-v2-variants-1. Automated/source checks establish supplied-input behavior; actual Doom 7/8/9+ and Awakened counter timing remain unvalidated in natural gameplay. Doom credit and assignments remain disabled. No additional bot deployment is required for these optional fields.

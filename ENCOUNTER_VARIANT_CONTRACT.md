# Encounter-variant V1 release contract

Introduced in published plugin version 1.2, build `collector-v2-variants-1`,
at `ccd4b442a82315da87ff57556ce8c847b0bf2ec5`. The local version 1.3 candidate,
build `collector-v2-release-1`, preserves this protocol and is not yet published.
Production bot protocol support is deployed. Natural Doom/Awakened gameplay validation
remains outstanding; automated/source verification does not establish live timing.
RuneLite observes; the bot decides. Doom Hunt credit remains disabled.

## Detection

Normal and awakened DT2 share NPC identities. A name, visual phase or Duke's AWAKE
NPC constant does not identify difficulty. Dedicated personal completion varps are used:

| Canonical boss | Normal varp | Awakened varp |
| --- | --- | --- |
| Duke Sucellus | TOTAL_DUKE_SUCELLUS_KILLS (3967) | TOTAL_DUKE_SUCELLUS_AWAKENED_KILLS (3974) |
| The Leviathan | TOTAL_LEVIATHAN_KILLS (3968) | TOTAL_LEVIATHAN_AWAKENED_KILLS (3973) |
| Vardorvis | TOTAL_VARDORVIS_KILLS (3970) | TOTAL_VARDORVIS_AWAKENED_KILLS (3971) |
| The Whisperer | TOTAL_WHISPERER_KILLS (3969) | TOTAL_WHISPERER_AWAKENED_KILLS (3972) |

Source: RuneLite's public [VarPlayerID definitions](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/gameval/VarPlayerID.java),
also verified against the locally resolved RuneLite 1.13.1 source artifact.
Eight fixed GameTick reads; no scanning, new thread, dependency or polling loop.
Initial synchronization, unchanged totals, decreases and unavailable reads do not produce
completions. Paused collection baselines totals rather than replaying them. Simultaneous
normal/awakened increases emit only awakened evidence, not another normal kill. Counter jumps
remain one raw before/after observation; the plugin never invents individual credited kills.
Detection is at completion-counter observation time, not encounter entry or orb consumption.
Failed attempts/visual phase changes are not successful awakened observations.

## Exact protocol

Optional top-level JSON string `encounterVariant`: exactly `NORMAL` or `AWAKENED`.
Allowed only for the four canonical bosses above. Enum and serializer enforce bounded values
and boss context. No arbitrary values, four booleans, new canonical names or raw NPC gameplay IDs.
Existing schemaVersion 1, collectorVersion 2 and `/api/runelite/observations` route remain.
Current candidate build marker: `collector-v2-release-1` (published 1.2: `collector-v2-variants-1`). Existing UUID, assignment UUID/null, RSN, timing,
capture status, detector type/version, policy revision and diagnostics remain.

Dedicated-counter reports retain `detectorType: STANDARD_NPC` and existing signals:

```json
"encounterVariant": "AWAKENED",
"signals": [
  {"kind": "completion", "tickOffset": 0},
  {"kind": "counter", "tickOffset": 0, "source": "completion_varp", "previous": 10, "current": 11}
]
```

Capture complete means the window closed normally, not validated or credited.

## Existing capability negotiation

Optional collector policy integer `encounterVariantVersion: 1`, valid only with supported,
unexpired collector-v2 policy. Absent/unknown/expired capability disables variant delivery.
NORMAL reports omit the field at serialization without support. Existing ordinary NPC,
chat and profile reports remain independently deliverable. AWAKENED reports always retain
the field and wait in the existing queue for a capable bot; never downgrade them to normal.
Variant-bearing queued reports wait if support later expires. UUID and serialized payload
remain immutable through retries/restoration. Existing retention/backpressure still apply.

Absent means **no explicit variant observation**, not proof of NORMAL. The production bot
accepts and advertises this field. Servers advertising support must preserve it for
decisions; ignoring it is insufficient for variant-aware credit. Existing acknowledgements
that ignore optional presentation metadata still work through matching report-ID checks.

## Separation and lifecycle

Separate bounded normal, awakened and unclassified NPC/chat/profile capture channels.
Dedicated totals are not guessed onto nearby deaths/loot; late unclassified signals cannot
mutate awakened captures. Existing windows, limits, RSN and assignment checks remain.
Logout/reconnect/world hop/profile changes interrupt variant captures and reset baselines.
Account/profile/assignment context changes baseline before sampling. Finalized queues survive
through existing persistence/retries; diagnostic retention and credit-candidate protection remain.

## Deployed bot contract

- Accept/persist the bounded optional field through storage/replay/decisions for these bosses.
- Advertise `encounterVariantVersion: 1` only after those paths are tested.
- Understand DT2 dedicated `completion_varp` reports for all four bosses.
- Separate normal/awakened personal totals in durable counter deduplication. Equal numeric
  totals across variants are not the same counter identity.
- Preserve published-client behavior without requiring the field or inferring NORMAL from absence.
- Evaluate evidence and decide Hunt matching/substitutions/credit on the server; receipt alone
  never awards KC. Preserve assignment/generation and exactly-once protection.
- All three capabilities (`doomEvidenceVersion`, `doomCounterEvidenceVersion`,
  `encounterVariantVersion`) are deployed at version 1. Doom credit and assignments
  remain disabled. Normal/Awakened observations may satisfy the corresponding normal
  Hunt for one KC without a bonus; the server makes that decision.

## Diagnostics, privacy and validation

Structured reports retain the plugin-defined canonical encounter and available variant.
Doom adds bounded completed level, extended yes/no and supported game-message source.
The report envelope includes the player's own RSN; raw chat, credentials and NPC dumps
are not collected. Version 1.3 retires the unused local status/clipboard display cache;
structured encounter evidence, serialization and server credit decisions remain unchanged.

Tests cover all four bosses in both modes; synchronization, pauses/resets, simultaneous totals,
raw jumps, capture isolation, optional serialization, invalid values/bosses, capability expiry,
queue persistence/duplicates, retries, ignoring acknowledgements, real plugin sampling and privacy.
Existing Doom/chest/identity/reliability tests remain. Automated inputs do not prove live gameplay
timing or Plugin Hub approval. Live-check each dedicated counter separately; the bot
contract update is deployed and passes 180/180 bot test files. No live Doom/Awakened
gameplay validation is claimed by this release review.

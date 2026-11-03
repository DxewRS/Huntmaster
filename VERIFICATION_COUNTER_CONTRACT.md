# Verification-counter V1 contract

Build `collector-v2-verification-1`, not yet published. RuneLite observes; the bot
decides. A rise in a counter is never a completion verdict in the plugin.

## Detection

The plugin reads one fixed set of own-account totals once per game tick through the
existing collector: `TRACKING_BOSSES_KILLED` (4510) plus each boss total listed in
`VerificationCounters.java`, which mirrors `BOSS_VARPS` in the bot's `serverEvidence.js`.
The set is packaged. The bot cannot add to it and nothing is scanned.

The first read after login, a world hop, an account change or a pause is a baseline.
Unchanged totals, decreases and unavailable reads report nothing. Negative reads clear
the baseline.

A rise joins the newest open record of its boss, or a record that opens within four
ticks. It never opens a record, adds a signal, or extends a window. A record carries at
most six changes.

Left out on purpose: Doom of Mokhaiotl (levels and paired evidence), Obor and Bryophyta
(their totals count keyed chests), Moons of Peril and Barrows Brothers (chests),
Dagannoth Kings (component path), Brutus (variant unresolved) and raids (no kill total).

## Exact protocol

Optional top-level `bossCounters`, valid only with collector version 2 and
`server_observation` tracking:

```json
"bossCounters": {"version": 1, "changes": [
  {"varp": 4510, "previous": 14867, "current": 14868, "tickOffset": 6},
  {"varp": 3968, "previous": 1311, "current": 1312, "tickOffset": 6}
]}
```

`tickOffset` runs from -16 to the report's window. The bot rejects a malformed object
but ignores a varp it does not know, so a newer client stays deliverable.

## Capability negotiation

Optional collector policy integer `verificationCounterVersion: 1`, valid only with a
supported, unexpired collector-v2 policy. Absent, unknown or expired capability omits
the field at serialization. The report is otherwise unchanged and delivers normally, so
this build works against an older bot and older builds work against this bot.

## Bot rules

- Every existing proof still credits exactly as before.
- A pair is exactly one global rise of +1 and exactly one rise of that boss's total, at
  most two ticks apart. A jump in the boss total credits one kill.
- The global total is spent once per account in `verification_global_kills`. Any later
  report carrying the same rise is a duplicate, on either pathway. This is also what
  stops a normal-and-awakened pair of reports crediting one DT2 kill twice.
- When no existing proof applies, a pair may recover the kill if the boss is on the
  recovery allowlist, the capture was complete, the rise is at most four ticks before
  the record opened, any chat counter agrees with the boss total, and a death, matching
  loot or completion signal supports it. Assignment eligibility is still the bot's own.
- The global total alone never names a boss or credits a kill.
- Allowlist: `The Leviathan:NORMAL` and `Maggot King`. Other bosses need live proof and
  a further bot release. Until then they keep their existing method.

## Privacy and validation

Totals are the player's own, are sent only inside reports the plugin already sends, and
produce no in-game display. See [PRIVACY.md](PRIVACY.md). Automated tests do not prove
live timing. The observed offset comes from eleven recorded kills of two bosses. Whether
the global total rises for skilling, chest or raid activities is unknown.

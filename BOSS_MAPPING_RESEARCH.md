# Observation mapping research — September 17, 2026

Scope: Giant Mole, Sarachnis and Scurrius. All three remain inactive.

## Primary sources inspected

- [RuneLite ChatCommandsPlugin](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/chatcommands/ChatCommandsPlugin.java): general counter-message parsing and profile storage.
- [RuneLite ChatCommandsPluginTest](https://github.com/runelite/runelite/blob/master/runelite-client/src/test/java/net/runelite/client/plugins/chatcommands/ChatCommandsPluginTest.java): exact fixtures for other bosses, but none located for these three.
- [RuneLite LootTrackerPlugin](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/loottracker/LootTrackerPlugin.java) and its official tests: no exact target mapping fixtures located in the inspected files.
- OSRS Wiki [Giant Mole](https://oldschool.runescape.wiki/w/Giant_Mole), [Sarachnis](https://oldschool.runescape.wiki/w/Sarachnis), [Scurrius](https://oldschool.runescape.wiki/w/Scurrius): indexed page content supports the named boss identities. Direct page opens were unavailable. Indexed data can be older than the current game.

## Confirmed versus inferred

RuneLite's inspected implementation extracts a boss name and numeric total from
counter messages. It stores totals in the killcount group under the lowercase
parsed name. Its inspected rename map concerns Barrows, not these targets.
Therefore the expected keys below are source-supported deductions conditional
on the actual message naming the boss as expected, not independent captured
fixtures for each target.

| Boss | Expected key | Expected prefix | Full mapping status |
|---|---|---|---|
| Giant Mole | giant mole | Your Giant Mole kill count is: | Exact target message and NPC signal behaviour not independently confirmed |
| Sarachnis | sarachnis | Your Sarachnis kill count is: | Exact target message and NPC signal behaviour not independently confirmed |
| Scurrius | scurrius | Your Scurrius kill count is: | Exact target message and NPC signal behaviour not independently confirmed |

The generic parser supports these expected forms, but that does not prove each
form occurs in the current game. No third-party capture was treated as an
official fixture, and no transformed NPC alias was invented. No attack/death
timing, loot timing or group KC mechanics are assumed from these mappings.

## Code preparation

Evidence-only message matching now strips colour tags and compares the exact
configured prefix case-insensitively. Counter parsing still uses only the
leading total, excluding duration digits. Evidence-only NPC-name matching is
case-insensitive, without allowing partial names. Existing credit-capable
detectors retain their original matching behaviour.

No candidate was added to BossRegistry. A future exact reference message and
NPC name, or a suitable primary-source fixture, can complete a target mapping.
Then activate one profile in evidence-only mode and keep its in-game evidence
status separate from permission to award KC. The owner need not test every
boss; public beta can supply broader observations once capture mappings exist.

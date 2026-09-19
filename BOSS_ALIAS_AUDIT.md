# Counter alias audit

Primary source: [RuneLite Chat Commands](https://raw.githubusercontent.com/runelite/runelite/master/runelite-client/src/main/java/net/runelite/client/plugins/chatcommands/ChatCommandsPlugin.java), reviewed September 17, 2026. Its name conversions establish supported counter terminology; they are not actual Huntmaster encounter fixtures or proof of reliability.

| Huntmaster assignment | Accepted counter names | Status |
|---|---|---|
| Barrows Brothers | Barrows chest, Barrows Chests | Chest/reward adapter implemented; one reference confirmed |
| Moons of Peril | Lunar Chest, Lunar Chests | Chest/reward adapter implemented; one reference confirmed |
| The Nightmare | Nightmare | Counter alias enabled; Phosani's Nightmare remains distinct |
| The Hueycoatl | Hueycoatl | Counter alias enabled; beta evidence pending |

Aliases only change matching of personal counter messages. Reports retain the canonical Huntmaster assignment name. Existing registered verifiers take precedence. These changes do not invent NPC aliases or waive provisional-credit support requirements. A chest counter without matching supporting evidence is collected but does not qualify for provisional credit.

Owner-confirmed credit policy: Barrows and Moons award one credit per verified reward-chest completion, including partial clears. Killing every brother or every Moon is not required. Do not add a minimum boss-clear count; chest counter and supporting reward evidence still must qualify.

Do not globally strip "The", punctuation, or words such as "Corrupted"/"awakened". Do not count deaths of individual Barrows brothers or individual moons as chest completions. Dagannoth Rex/Prime/Supreme have separate counters. Separate observation collectors are implemented and reference-confirmed once per king; component-aware credit and duplicate keys remain pending.

The bot's coverage catalogue includes every current assignment, reviewed counter aliases, and an initial list of content needing completion/component adapters. This is a planning inventory, not an exhaustive list of OSRS bosses or confirmed coverage.

Other gaps: Royal Titans component identities, Grotesque Guardians paired deaths, Doom live completion totals, wave-based completion content, and activity counters. The Gauntlet and The Corrupted Gauntlet already use separate existing completion profiles. Exact identities and supported signals for new bosses must come from primary sources or beta encounter reports; no guessed aliases are activated.

Automated alias parsing and wrong-variant rejection tests pass. Barrows/Moons alias and reward paths are reference-confirmed once; wider beta and other alias paths remain pending. Further individual boss kills from the owner are not required; use broader beta evidence. See the bot BOSS_DETECTION_MAP.md and JSON for the current research inventory and remaining adapters.

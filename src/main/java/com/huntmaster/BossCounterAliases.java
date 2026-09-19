package com.huntmaster;

/** Counter-name aliases reviewed against RuneLite Chat Commands; not NPC aliases. */
final class BossCounterAliases
{
    private BossCounterAliases() { }
    static boolean matches(String assigned, String counterName)
    {
        if (assigned == null || counterName == null) return false;
        if (assigned.equalsIgnoreCase(counterName)) return true;
        switch (assigned.toLowerCase(java.util.Locale.ROOT))
        {
            case "barrows brothers":
                return counterName.equalsIgnoreCase("Barrows chest") || counterName.equalsIgnoreCase("Barrows Chests");
            case "moons of peril":
                return counterName.equalsIgnoreCase("Lunar Chest") || counterName.equalsIgnoreCase("Lunar Chests");
            case "the nightmare":
                return counterName.equalsIgnoreCase("Nightmare");
            case "the hueycoatl":
                return counterName.equalsIgnoreCase("Hueycoatl");
            case "the mimic":
                return counterName.equalsIgnoreCase("Mimic");
            case "the leviathan": return counterName.equalsIgnoreCase("Leviathan");
            case "the whisperer": return counterName.equalsIgnoreCase("Whisperer");
            case "the gauntlet": return counterName.equalsIgnoreCase("Gauntlet");
            case "the corrupted gauntlet": return counterName.equalsIgnoreCase("Corrupted Gauntlet");
            default:
                return false;
        }
    }
}

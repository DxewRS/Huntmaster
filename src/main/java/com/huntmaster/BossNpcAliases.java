package com.huntmaster;

/** Assignment-scoped supporting NPC identities; never awards credit without personal KC. */
final class BossNpcAliases
{
    private BossNpcAliases() { }
    static boolean matches(String boss, String npc)
    {
        if (boss == null || npc == null) return false;
        if (boss.equalsIgnoreCase(npc)) return true;
        switch (boss)
        {
            case "Grotesque Guardians": return "Dusk".equalsIgnoreCase(npc) || "Dawn".equalsIgnoreCase(npc);
            case "Royal Titans": return "Branda the Fire Queen".equalsIgnoreCase(npc) || "Eldric the Ice King".equalsIgnoreCase(npc);
            case "The Nightmare": return "Nightmare".equalsIgnoreCase(npc);
            case "The Hueycoatl": return "Hueycoatl".equalsIgnoreCase(npc);
            case "The Mimic": return "Mimic".equalsIgnoreCase(npc);
            default: return false;
        }
    }
}

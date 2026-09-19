package com.huntmaster;

import net.runelite.api.gameval.VarPlayerID;

/** Dedicated personal totals from RuneLite gamevals, never glory, waves or reward counts. */
final class SpecialEncounterTotals
{
    private SpecialEncounterTotals() { }
    static Integer varp(String boss)
    {
        if (boss == null) return null;
        switch (boss)
        {
            case "Grotesque Guardians": return VarPlayerID.TOTAL_GARGBOSS_KILLS;
            case "Royal Titans": return VarPlayerID.TOTAL_ROYAL_TITAN_KILLS;
            case "Zalcano": return VarPlayerID.TOTAL_ZALCANO_KILLS;
            case "TzTok-Jad": return VarPlayerID.TOTAL_JAD_KILLS;
            case "TzKal-Zuk": return VarPlayerID.TOTAL_ZUK_KILLS;
            case "Sol Heredit": return VarPlayerID.TOTAL_SOL_KILLS;
            case "Doom of Mokhaiotl": return VarPlayerID.DOM_LEVEL_HIGHSCORES;
            default: return null;
        }
    }
}

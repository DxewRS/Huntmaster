package com.huntmaster;

/** Bounded objective observations, never a Hunt classification. */
enum EncounterVariant
{
    NORMAL, AWAKENED;

    static boolean supports(String boss)
    {
        return "Duke Sucellus".equals(boss) || "The Leviathan".equals(boss)
            || "Vardorvis".equals(boss) || "The Whisperer".equals(boss);
    }
}

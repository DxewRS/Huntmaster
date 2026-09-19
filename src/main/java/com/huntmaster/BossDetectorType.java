package com.huntmaster;

enum BossDetectorType
{
    /**
     * Normal NPC boss.
     *
     * Typical evidence:
     * - NPC death
     * - KC increase
     * - NPC loot
     *
     * Examples:
     * Vardorvis
     * Vorkath
     * Zulrah
     * General Graardor
     */
    STANDARD_NPC,

    /**
     * Encounter completion where the kill/completion does not
     * behave like a normal NPC death + NPC loot sequence.
     *
     * Examples:
     * Barrows
     * Gauntlet
     * Moons of Peril
     */
    COMPLETION,

    /**
     * Activity or minigame completion.
     *
     * Examples:
     * Tempoross
     * Wintertodt
     * Zalcano
     */
    ACTIVITY,

    /**
     * Unique encounter requiring custom detection behavior.
     *
     * Examples:
     * TzTok-Jad
     * TzKal-Zuk
     * Sol Heredit
     */
    SPECIAL
}

package com.huntmaster;

final class BossDefinition
{
    private final String name;
    private final boolean evidenceOnly;
    private final String profileKey;

    private final String kcMessagePrefix;

    private final int pendingWindowTicks;

    private final String detectorVersion;

    private final BossDetectorType detectorType;


    // ==================================================
    // DEDICATED TOTAL-COUNT VARP METADATA
    // ==================================================

    /*
     * RuneLite / OSRS varp containing the encounter's
     * dedicated total completion / activity count.
     *
     * Used by:
     * - COMPLETION detectors
     * - ACTIVITY detectors
     *
     * Null for detector types that do not use one.
     */
    private final Integer completionVarpId;




    // ==================================================
    // STANDARD NPC CONSTRUCTOR
    // ==================================================

    public BossDefinition(
            String name,
            String profileKey,
            String kcMessagePrefix,
            int pendingWindowTicks,
            String detectorVersion)
    {
        this(
                name,
                profileKey,
                kcMessagePrefix,
                pendingWindowTicks,
                detectorVersion,
                BossDetectorType.STANDARD_NPC,
                null
        );
    }


    // ==================================================
    // DETECTOR TYPE CONSTRUCTOR
    // ==================================================

    public BossDefinition(
            String name,
            String profileKey,
            String kcMessagePrefix,
            int pendingWindowTicks,
            String detectorVersion,
            BossDetectorType detectorType)
    {
        this(
                name,
                profileKey,
                kcMessagePrefix,
                pendingWindowTicks,
                detectorVersion,
                detectorType,
                null
        );
    }


    // ==================================================
    // FULL CONSTRUCTOR
    // ==================================================

    public BossDefinition(
            String name,
            String profileKey,
            String kcMessagePrefix,
            int pendingWindowTicks,
            String detectorVersion,
            BossDetectorType detectorType,
            Integer completionVarpId)
    {
        this(name, profileKey, kcMessagePrefix, pendingWindowTicks, detectorVersion, detectorType, completionVarpId, false);
    }

    private BossDefinition(String name, String profileKey, String kcMessagePrefix, int pendingWindowTicks, String detectorVersion, BossDetectorType detectorType, Integer completionVarpId, boolean evidenceOnly)
    {
        this.evidenceOnly = evidenceOnly;
        this.name =
                name;

        this.profileKey =
                profileKey;

        this.kcMessagePrefix =
                kcMessagePrefix;

        this.pendingWindowTicks =
                pendingWindowTicks;

        this.detectorVersion =
                detectorVersion;

        this.detectorType =
                detectorType;

        this.completionVarpId =
                completionVarpId;

    }


    // ==================================================
    // GETTERS
    // ==================================================

    public static BossDefinition betaCandidate(String name, String profileKey, String prefix)
    { return new BossDefinition(name, profileKey, prefix, 10, "generic-beta-v1", BossDetectorType.STANDARD_NPC, null, true); }

    static BossDefinition totalCounterCandidate(String name, String profileKey)
    { return new BossDefinition(name, profileKey, "Your " + name, 10,
        "Doom of Mokhaiotl".equals(name) ? "dedicated-total-observation-v1" : "dedicated-total-beta-v1",
        BossDetectorType.COMPLETION, SpecialEncounterTotals.varp(name), true); }

    static BossDefinition dagannothComponent(String component)
    { return new BossDefinition("Dagannoth Kings", "dagannoth_" + component,
        "Your Dagannoth " + component + " kill count is:", 10,
        "dagannoth-" + component + "-beta-v1", BossDetectorType.STANDARD_NPC, null, true); }

    public boolean isEvidenceOnly() { return evidenceOnly; }

    public String getName()
    {
        return name;
    }

    public String getProfileKey()
    {
        return profileKey;
    }

    public String getKcMessagePrefix()
    {
        return kcMessagePrefix;
    }

    public int getPendingWindowTicks()
    {
        return pendingWindowTicks;
    }

    public String getDetectorVersion()
    {
        return detectorVersion;
    }

    public BossDetectorType getDetectorType()
    {
        return detectorType;
    }

    public Integer getCompletionVarpId()
    {
        return completionVarpId;
    }

}

package com.huntmaster;

final class BossDetector
{
    private final BossDefinition definition;

    private final BossVerificationState state;

    /*
     * STANDARD_NPC:
     * Previous RuneLite boss KC.
     *
     * COMPLETION:
     * Previous RuneLite completion total.
     */
    private Integer lastKc;


    public BossDetector(
            BossDefinition definition)
    {
        this.definition =
                definition;

        this.state =
                new BossVerificationState();

        this.lastKc =
                null;
    }


    // ==================================================
    // CORE
    // ==================================================

    public BossDefinition getDefinition()
    {
        return definition;
    }

    public BossVerificationState getState()
    {
        return state;
    }


    // ==================================================
    // COUNTER STATE
    // ==================================================

    public Integer getLastKc()
    {
        return lastKc;
    }

    public void setLastKc(
            Integer lastKc)
    {
        this.lastKc =
                lastKc;
    }


    // ==================================================
    // DEFINITION SHORTCUTS
    // ==================================================

    public String getName()
    {
        return definition.getName();
    }

    public String getProfileKey()
    {
        return definition.getProfileKey();
    }

    public String getKcMessagePrefix()
    {
        return definition.getKcMessagePrefix();
    }

    public int getPendingWindowTicks()
    {
        return definition.getPendingWindowTicks();
    }

    public String getDetectorVersion()
    {
        return definition.getDetectorVersion();
    }

    public BossDetectorType getDetectorType()
    {
        return definition.getDetectorType();
    }

    public Integer getCompletionVarpId()
    {
        return definition.getCompletionVarpId();
    }



    // ==================================================
    // VERIFICATION WINDOW
    // ==================================================

    public void startPendingVerification()
    {
        state.setPendingVerification(
                true
        );

        state.setPendingTicksRemaining(
                definition.getPendingWindowTicks()
        );
    }

    public void resetVerification()
    {
        state.reset();
    }
}

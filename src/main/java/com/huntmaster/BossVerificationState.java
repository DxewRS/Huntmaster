package com.huntmaster;

final class BossVerificationState
{
   // ==================================================
   // STANDARD NPC SIGNALS
   // ==================================================

   private boolean deathCandidate = false;

   private boolean lootOccurred = false;

   private int lootTick = -1;


   // ==================================================
   // SHARED KC / COMPLETION COUNTER SIGNAL
   // ==================================================

   /*
    * STANDARD_NPC:
    * RuneLite KC increased by exactly +1.
    *
    * COMPLETION:
    * RuneLite completion total increased by exactly +1.
    */
   private boolean kcIncreaseConfirmed = false;


   // ==================================================
   // SHARED VERIFICATION WINDOW
   // ==================================================

   private boolean pendingVerification = false;

   private int pendingTicksRemaining = 0;


   // ==================================================
   // DUPLICATE PROTECTION
   // ==================================================

   private int lastVerifiedTick = -1;


   // ==================================================
   // STANDARD NPC GETTERS / SETTERS
   // ==================================================

   public boolean isDeathCandidate()
   {
      return deathCandidate;
   }

   public void setDeathCandidate(
           boolean deathCandidate)
   {
      this.deathCandidate =
              deathCandidate;
   }

   public boolean isLootOccurred()
   {
      return lootOccurred;
   }

   public void setLootOccurred(
           boolean lootOccurred)
   {
      this.lootOccurred =
              lootOccurred;
   }

   public int getLootTick()
   {
      return lootTick;
   }

   public void setLootTick(
           int lootTick)
   {
      this.lootTick =
              lootTick;
   }


   // ==================================================
   // SHARED GETTERS / SETTERS
   // ==================================================

   public boolean isKcIncreaseConfirmed()
   {
      return kcIncreaseConfirmed;
   }

   public void setKcIncreaseConfirmed(
           boolean kcIncreaseConfirmed)
   {
      this.kcIncreaseConfirmed =
              kcIncreaseConfirmed;
   }

   public boolean isPendingVerification()
   {
      return pendingVerification;
   }

   public void setPendingVerification(
           boolean pendingVerification)
   {
      this.pendingVerification =
              pendingVerification;
   }

   public int getPendingTicksRemaining()
   {
      return pendingTicksRemaining;
   }

   public void setPendingTicksRemaining(
           int pendingTicksRemaining)
   {
      this.pendingTicksRemaining =
              pendingTicksRemaining;
   }

   public int getLastVerifiedTick()
   {
      return lastVerifiedTick;
   }

   public void setLastVerifiedTick(
           int lastVerifiedTick)
   {
      this.lastVerifiedTick =
              lastVerifiedTick;
   }


   // ==================================================
   // RESET
   // ==================================================

   public void reset()
   {
      deathCandidate = false;
      lootOccurred = false;
      lootTick = -1;

      kcIncreaseConfirmed = false;

      pendingVerification = false;
      pendingTicksRemaining = 0;

      /*
       * lastVerifiedTick intentionally survives reset.
       *
       * It is used for duplicate-event protection after
       * a successful verification.
       */
   }
}

package com.huntmaster;

final class StandardNpcVerification
{
	static boolean countsExpiredAttemptAsFailure(BossDefinition definition)
	{
		// A death alone does not establish that this player earned a kill.
		return !definition.isEvidenceOnly();
	}
	static EncounterObservation.Method method(BossDefinition definition, BossVerificationState state, boolean recentLoot)
	{
		if (definition.isEvidenceOnly()) return null;
		if (state.isDeathCandidate() && state.isKcIncreaseConfirmed()) return EncounterObservation.Method.DEATH_AND_COUNTER;
		if (state.isDeathCandidate() && recentLoot) return EncounterObservation.Method.DEATH_AND_LOOT;
		if (state.isKcIncreaseConfirmed() && recentLoot) return EncounterObservation.Method.COUNTER_AND_LOOT;
		return null;
	}
}

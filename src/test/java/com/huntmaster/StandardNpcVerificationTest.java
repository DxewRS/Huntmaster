package com.huntmaster;

import org.junit.Test;
import static org.junit.Assert.*;

public class StandardNpcVerificationTest
{
	@Test public void expiryFailureAppliesOnlyToCreditingDetectors()
	{
		assertFalse(StandardNpcVerification.countsExpiredAttemptAsFailure(BossDefinition.betaCandidate("Giant Mole","giant mole","Your Giant Mole kill count is:")));
		assertTrue(StandardNpcVerification.countsExpiredAttemptAsFailure(new BossDefinition("Brutus","brutus","Your Brutus kill count is:",10,"v1")));
	}
	@Test public void existingFallbackPoliciesArePreserved()
	{
		BossDefinition normal=new BossDefinition("Brutus","brutus","Your Brutus kill count is:",10,"v1");
		BossVerificationState state=new BossVerificationState();state.setDeathCandidate(true);
		assertEquals(EncounterObservation.Method.DEATH_AND_LOOT,StandardNpcVerification.method(normal,state,true));
		state.setDeathCandidate(false);state.setKcIncreaseConfirmed(true);
		assertEquals(EncounterObservation.Method.COUNTER_AND_LOOT,StandardNpcVerification.method(normal,state,true));
	}
	@Test public void evidenceOnlyNeverSelectsCreditMethod()
	{
		BossVerificationState state=new BossVerificationState();state.setDeathCandidate(true);state.setKcIncreaseConfirmed(true);
		assertNull(StandardNpcVerification.method(BossDefinition.betaCandidate("Giant Mole","giant mole","Your Giant Mole kill count is:"),state,true));
	}
}

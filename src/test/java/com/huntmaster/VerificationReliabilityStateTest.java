package com.huntmaster;

import org.junit.Test;
import static org.junit.Assert.*;

public class VerificationReliabilityStateTest
{
	@Test
	public void thirdFailurePausesAndFurtherFailuresDoNotAccumulate()
	{
		VerificationReliabilityState state = new VerificationReliabilityState();
		assertTrue(state.recordFailure());
		assertTrue(state.recordFailure());
		assertFalse(state.isPaused());
		assertTrue(state.recordFailure());
		assertTrue(state.isPaused());
		assertFalse(state.recordFailure());
		assertEquals(3, state.getFailures());
	}

	@Test
	public void verifiedKillBreaksFailureStreakBeforePause()
	{
		VerificationReliabilityState state = new VerificationReliabilityState();
		state.recordFailure();
		state.recordFailure();
		state.recordVerified();
		assertEquals(0, state.getFailures());
		state.recordFailure();
		assertFalse(state.isPaused());
	}

	@Test
	public void lateVerifiedSignalDoesNotResumePausedTracking()
	{
		VerificationReliabilityState state = new VerificationReliabilityState();
		state.restore(3);
		state.recordVerified();
		assertTrue(state.isPaused());
	}

	@Test
	public void persistedFailuresPreserveThresholdAndClampInvalidCounts()
	{
		VerificationReliabilityState state = new VerificationReliabilityState();
		state.restore(2);
		state.recordFailure();
		assertTrue(state.isPaused());
		state.restore(-1);
		assertEquals(0, state.getFailures());
		state.restore(Integer.MAX_VALUE);
		assertEquals(3, state.getFailures());
	}
}

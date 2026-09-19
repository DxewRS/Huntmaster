package com.huntmaster;

import org.junit.Test;
import static org.junit.Assert.*;

public class VerificationTelemetryTest
{
	@Test
	public void noEvidenceHasNoRate()
	{
		VerificationTelemetry stats = new VerificationTelemetry();
		assertEquals(0, stats.getCompletedAttempts());
		assertNull(stats.getVerificationRate());
	}

	@Test
	public void mixedOutcomesUseCompletedAttemptsAsDenominator()
	{
		VerificationTelemetry stats = new VerificationTelemetry();
		stats.record(VerificationTelemetry.Outcome.VERIFIED);
		stats.record(VerificationTelemetry.Outcome.VERIFIED);
		stats.record(VerificationTelemetry.Outcome.UNRESOLVED);
		stats.record(VerificationTelemetry.Outcome.AMBIGUOUS);
		assertEquals(4, stats.getCompletedAttempts());
		assertEquals(2, stats.getVerified());
		assertEquals(1, stats.getUnresolved());
		assertEquals(1, stats.getAmbiguous());
		assertEquals(0.5, stats.getVerificationRate(), 0.000001);
	}

	@Test
	public void restoredCountersContinueAccumulating()
	{
		VerificationTelemetry stats = new VerificationTelemetry();
		stats.restore(8, 1, 1);
		stats.record(VerificationTelemetry.Outcome.UNRESOLVED);
		assertEquals(11, stats.getCompletedAttempts());
		assertEquals(8.0 / 11, stats.getVerificationRate(), 0.000001);
	}

	@Test
	public void invalidPersistedCountersCannotOverflowTotals()
	{
		VerificationTelemetry stats = new VerificationTelemetry();
		stats.restore(-1, Long.MAX_VALUE, Long.MAX_VALUE);
		stats.record(VerificationTelemetry.Outcome.UNRESOLVED);
		assertEquals(0, stats.getVerified());
		assertEquals(2_000_000_000_000L, stats.getCompletedAttempts());
		assertEquals(0.0, stats.getVerificationRate(), 0.0);
	}

	@Test
	public void separateDetectorsDoNotShareCounts()
	{
		VerificationTelemetry first = new VerificationTelemetry();
		VerificationTelemetry second = new VerificationTelemetry();
		first.record(VerificationTelemetry.Outcome.AMBIGUOUS);
		assertEquals(0, second.getCompletedAttempts());
		assertNull(second.getVerificationRate());
	}
}

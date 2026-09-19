package com.huntmaster;

import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncounterObservationTest
{
	private static final int START = 100;
	private EncounterObservation observation()
	{
		return new EncounterObservation(UUID.randomUUID(), UUID.randomUUID(), "Example", "Brutus",
				BossDetectorType.STANDARD_NPC, "v1", 1_000_000L, START, 12);
	}

	@Test
	public void earlyVerificationRetainsLaterLootWithoutExtendingWindow()
	{
		EncounterObservation record = observation();
		record.recordPrimary(EncounterObservation.SignalKind.DEATH, START);
		record.recordCounter(START + 3, EncounterObservation.CounterSource.KC_MESSAGE, 161, 162);
		UUID eventId = UUID.randomUUID();
		record.markVerified(eventId, EncounterObservation.Method.DEATH_AND_COUNTER);
		assertTrue(record.recordLoot(START + 4, EncounterObservation.LootAttribution.MATCHING_ENCOUNTER));
		assertFalse(record.closeIfExpired(START + 11));
		assertTrue(record.closeIfExpired(START + 12));
		EncounterObservation.Snapshot report = record.snapshot();
		assertEquals(3, report.signals.size());
		assertEquals(4, report.signals.get(2).tickOffset);
		assertEquals(eventId, report.creditEventId);
		assertEquals(EncounterObservation.Outcome.VERIFIED, report.outcome);
		assertTrue(report.captureComplete);
		assertEquals(12, report.durationTicks);
	}

	@Test
	public void counterCanPrecedeDeathAndPreserveBothSources()
	{
		EncounterObservation record = observation();
		record.recordCounter(START, EncounterObservation.CounterSource.RS_PROFILE, 8, 9);
		record.recordCounter(START + 1, EncounterObservation.CounterSource.KC_MESSAGE, 8, 9);
		record.recordPrimary(EncounterObservation.SignalKind.DEATH, START + 2);
		record.markVerified(UUID.randomUUID(), EncounterObservation.Method.DEATH_AND_COUNTER);
		record.closeIfExpired(START + 12);
		assertEquals(EncounterObservation.SignalKind.COUNTER, record.snapshot().signals.get(0).kind);
		assertEquals(EncounterObservation.CounterSource.RS_PROFILE, record.snapshot().signals.get(0).source);
		assertEquals(3, record.snapshot().signals.size());
	}

	@Test
	public void missingBaselineIsUnknownRatherThanAnInventedDelta()
	{
		EncounterObservation record = observation();
		record.recordCounter(START, EncounterObservation.CounterSource.KC_MESSAGE, null, 200);
		record.closeIfExpired(START + 12);
		assertNull(record.snapshot().signals.get(0).previous);
		assertEquals(Integer.valueOf(200), record.snapshot().signals.get(0).current);
		assertEquals(EncounterObservation.Outcome.UNRESOLVED, record.snapshot().outcome);
		assertNull(record.snapshot().creditEventId);
		assertEquals(1, record.snapshot().signals.size());
	}

	@Test
	public void counterJumpIsPreservedWithoutCreatingCredit()
	{
		EncounterObservation record = observation();
		record.recordCounter(START, EncounterObservation.CounterSource.COMPLETION_VARP, 20, 23);
		record.markUncertain(EncounterObservation.Outcome.AMBIGUOUS, EncounterObservation.Reason.COUNTER_JUMP);
		record.closeIfExpired(START + 12);
		assertEquals(Integer.valueOf(20), record.snapshot().signals.get(0).previous);
		assertEquals(Integer.valueOf(23), record.snapshot().signals.get(0).current);
		assertEquals(EncounterObservation.Outcome.AMBIGUOUS, record.snapshot().outcome);
		assertNull(record.snapshot().creditEventId);
	}

	@Test
	public void nextPrimaryAndChangedCounterCannotBeMergedIntoPreviousKill()
	{
		EncounterObservation first = observation();
		EncounterObservation second = observation();
		first.recordPrimary(EncounterObservation.SignalKind.DEATH, START);
		first.recordCounter(START + 1, EncounterObservation.CounterSource.KC_MESSAGE, 1, 2);
		first.markVerified(UUID.randomUUID(), EncounterObservation.Method.DEATH_AND_COUNTER);
		assertFalse(first.recordPrimary(EncounterObservation.SignalKind.DEATH, START + 4));
		assertFalse(first.recordCounter(START + 5, EncounterObservation.CounterSource.KC_MESSAGE, 2, 3));
		second.recordPrimary(EncounterObservation.SignalKind.DEATH, START + 4);
		second.recordCounter(START + 5, EncounterObservation.CounterSource.KC_MESSAGE, 2, 3);
		first.recordLoot(START + 6, EncounterObservation.LootAttribution.UNCERTAIN);
		first.closeIfExpired(START + 12);
		second.closeIfExpired(START + 12);
		assertNotEquals(first.snapshot().reportId, second.snapshot().reportId);
		assertEquals(Integer.valueOf(2), first.snapshot().signals.get(1).current);
		assertEquals(Integer.valueOf(3), second.snapshot().signals.get(1).current);
		assertEquals(EncounterObservation.LootAttribution.UNCERTAIN, first.snapshot().signals.get(2).attribution);
	}

	@Test
	public void interruptionPreservesOriginalIdentityAndDoesNotClaimFailure()
	{
		EncounterObservation record = observation();
		record.recordPrimary(EncounterObservation.SignalKind.DEATH, START);
		record.interrupt(START + 3, EncounterObservation.Reason.ASSIGNMENT_CHANGED);
		EncounterObservation.Snapshot report = record.snapshot();
		assertEquals(EncounterObservation.Outcome.INTERRUPTED, report.outcome);
		assertFalse(report.captureComplete);
		assertEquals(3, report.durationTicks);
		assertNull(report.creditEventId);
		assertFalse(record.recordLoot(START + 4, EncounterObservation.LootAttribution.MATCHING_ENCOUNTER));
		assertEquals(report.assignmentId, record.snapshot().assignmentId);
	}

	@Test
	public void logoutAfterVerificationDoesNotEraseCreditCorrelation()
	{
		EncounterObservation record = observation();
		UUID eventId = UUID.randomUUID();
		record.recordPrimary(EncounterObservation.SignalKind.DEATH, START);
		record.markVerified(eventId, EncounterObservation.Method.DEATH_AND_LOOT);
		record.interrupt(START + 4, EncounterObservation.Reason.LOGOUT);
		assertEquals(EncounterObservation.Outcome.VERIFIED, record.snapshot().outcome);
		assertEquals(eventId, record.snapshot().creditEventId);
		assertFalse(record.snapshot().captureComplete);
		assertEquals(EncounterObservation.Reason.LOGOUT, record.snapshot().interruptionReason);
	}

	@Test
	public void exactDuplicatesAndOutOfWindowSignalsAreIgnored()
	{
		EncounterObservation record = observation();
		assertTrue(record.recordLoot(START, EncounterObservation.LootAttribution.UNCERTAIN));
		assertFalse(record.recordLoot(START, EncounterObservation.LootAttribution.UNCERTAIN));
		assertFalse(record.recordLoot(START - 1, EncounterObservation.LootAttribution.UNCERTAIN));
		assertFalse(record.recordLoot(START + 12, EncounterObservation.LootAttribution.UNCERTAIN));
		record.closeIfExpired(Integer.MAX_VALUE);
		assertEquals(1, record.snapshot().signals.size());
	}

	@Test
	public void signalOverflowIsExplicitlyIncompleteAndBounded()
	{
		EncounterObservation record = new EncounterObservation(UUID.randomUUID(), UUID.randomUUID(), "Example", "Brutus",
				BossDetectorType.STANDARD_NPC, "v1", 1_000_000L, START, 128);
		for (int index = 0; index < EncounterObservation.MAX_SIGNALS; index++)
		{
			assertTrue(record.recordLoot(START + index, EncounterObservation.LootAttribution.UNCERTAIN));
		}
		assertFalse(record.recordLoot(START + 32, EncounterObservation.LootAttribution.UNCERTAIN));
		assertTrue(record.isClosed());
		assertEquals(32, record.snapshot().signals.size());
		assertFalse(record.snapshot().captureComplete);
		assertEquals(EncounterObservation.Reason.SIGNAL_LIMIT, record.snapshot().interruptionReason);
	}

	@Test(expected = IllegalStateException.class)
	public void verdictCannotBeChangedByLaterEvidence()
	{
		EncounterObservation record = observation();
		record.markUncertain(EncounterObservation.Outcome.AMBIGUOUS, EncounterObservation.Reason.COUNTER_JUMP);
		record.markVerified(UUID.randomUUID(), EncounterObservation.Method.DEATH_AND_COUNTER);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void publishedSignalsAreImmutable()
	{
		EncounterObservation record = observation();
		record.recordPrimary(EncounterObservation.SignalKind.DEATH, START);
		record.closeIfExpired(START + 12);
		record.snapshot().signals.clear();
	}

	@Test
	public void clockResetDoesNotCreateNegativeDuration()
	{
		EncounterObservation record = observation();
		record.recordLoot(START + 2, EncounterObservation.LootAttribution.UNCERTAIN);
		record.interrupt(1, EncounterObservation.Reason.CLOCK_RESET);
		assertEquals(2, record.snapshot().durationTicks);
		assertEquals(EncounterObservation.Outcome.INTERRUPTED, record.snapshot().outcome);
	}

	@Test(expected = IllegalArgumentException.class)
	public void captureWindowCannotBeUnbounded()
	{
		new EncounterObservation(UUID.randomUUID(), UUID.randomUUID(), "Example", "Brutus",
				BossDetectorType.STANDARD_NPC, "v1", 1_000_000L, START, 129);
	}

	@Test(expected = IllegalArgumentException.class)
	public void negativeCountersAreRejected()
	{
		observation().recordCounter(START, EncounterObservation.CounterSource.KC_MESSAGE, 0, -1);
	}

	@Test(expected = IllegalStateException.class)
	public void unfinishedCaptureCannotBeReportedAsComplete()
	{
		observation().snapshot();
	}
}

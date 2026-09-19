package com.huntmaster;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncounterCaptureTest
{
	@Test public void serializedUnknownCounterBaselineIsExplicitNull()
	{
		EncounterObservation record = new EncounterObservation(UUID.randomUUID(), UUID.randomUUID(),
			"Example", "Scurrius", BossDetectorType.STANDARD_NPC, "observation-v1", 1000, 100, 10, true);
		record.recordCounter(101, EncounterObservation.CounterSource.KC_MESSAGE, null, 106);
		record.closeIfExpired(110);
		String wire = EncounterReportCodec.encode(record.snapshot()).toString();
		com.google.gson.JsonObject counter = new com.google.gson.JsonParser().parse(wire).getAsJsonObject()
			.getAsJsonArray("signals").get(0).getAsJsonObject();
		assertTrue(counter.has("previous"));
		assertTrue(counter.get("previous").isJsonNull());
	}
	@Test public void lateCounterRecoversEarlierDeathWithoutAwardingCredit()
	{
		List<EncounterObservation.Snapshot> output = new ArrayList<>();
		EncounterCapture c = new EncounterCapture(output::add);
		BossDetector d = new BossDetector(BossDefinition.betaCandidate("Sarachnis", "sarachnis", "Your Sarachnis kill count is:"));
		String id = UUID.randomUUID().toString();
		c.primary(d,"Example",id,100,1000,EncounterObservation.SignalKind.DEATH);
		c.advance(110);
		c.counter(d,"Example",id,112,8200,EncounterObservation.CounterSource.KC_MESSAGE,null,10);
		c.loot(d,"Example",id,113,8800);
		c.advance(122);
		EncounterObservation.Snapshot report=output.get(1);
		assertEquals(3,report.signals.size());
		assertEquals(EncounterObservation.SignalKind.DEATH,report.signals.get(0).kind);
		assertEquals(12,report.signals.get(1).tickOffset);
		assertNull(report.signals.get(1).previous);
		assertNull(report.creditEventId);
		assertTrue(report.evidenceOnly);
	}
	@Test public void signalHistoryIsBoundedAndCannotCrossAssignments()
	{
		EncounterSignalBuffer b=new EncounterSignalBuffer();
		for(int i=0;i<100;i++) b.add("old",10,1000,i%2==0?EncounterObservation.SignalKind.DEATH:EncounterObservation.SignalKind.LOOT);
		assertEquals(64,b.consume("old",10).size());
		b.add("old",10,1000,EncounterObservation.SignalKind.DEATH);
		assertTrue(b.consume("new",11).isEmpty());
		b.add("new",10,1000,EncounterObservation.SignalKind.DEATH);
		assertTrue(b.consume("new",27).isEmpty());
		b.add("new",30,1000,EncounterObservation.SignalKind.DEATH);
		assertTrue(b.consume("new",1).isEmpty());
	}
	private final BossDetector detector = new BossDetector(new BossDefinition("Brutus", "brutus", "Your Brutus", 12, "v1"));
	private final String assignment = UUID.randomUUID().toString();
	private final List<EncounterObservation.Snapshot> reports = new ArrayList<>();
	private final EncounterCapture capture = new EncounterCapture(reports::add);
	@Test public void verifiedCaptureKeepsLateLootAndCreditIdentity()
	{
		capture.primary(detector, "Example", assignment, 100, 1000, EncounterObservation.SignalKind.DEATH);
		capture.counter(detector, "Example", assignment, 101, 1600, EncounterObservation.CounterSource.KC_MESSAGE, 8, 9);
		UUID id = UUID.randomUUID(); capture.verified(id, EncounterObservation.Method.DEATH_AND_COUNTER);
		capture.loot(detector, "Example", assignment, 102, 2200);
		capture.advance(112);
		assertEquals(1, reports.size()); assertEquals(id, reports.get(0).creditEventId);
		assertEquals(3, reports.get(0).signals.size());
	}
	@Test public void overlappingEncountersRetainSeparateReportsAndUncertainLoot()
	{
		capture.primary(detector, "Example", assignment, 100, 1000, EncounterObservation.SignalKind.DEATH);
		capture.primary(detector, "Example", assignment, 104, 3400, EncounterObservation.SignalKind.DEATH);
		capture.loot(detector, "Example", assignment, 105, 4000);
		capture.advance(116);
		assertEquals(2, reports.size());
		assertEquals(EncounterObservation.LootAttribution.UNCERTAIN, reports.get(1).signals.get(1).attribution);
	}
	@Test public void changedAssignmentInterruptsOldEvidenceWithoutRebinding()
	{
		capture.primary(detector, "Example", assignment, 100, 1000, EncounterObservation.SignalKind.DEATH);
		String next = UUID.randomUUID().toString();
		capture.counter(detector, "Example", next, 101, 1600, EncounterObservation.CounterSource.KC_MESSAGE, null, 9);
		assertEquals(UUID.fromString(assignment), reports.get(0).assignmentId);
		assertEquals(EncounterObservation.Reason.ASSIGNMENT_CHANGED, reports.get(0).interruptionReason);
		capture.advance(113);
		assertEquals(UUID.fromString(next), reports.get(1).assignmentId);
		assertNull(reports.get(1).signals.get(0).previous);
	}
	@Test public void logoutPreservesVerifiedVerdictAndEndsCapture()
	{
		capture.primary(detector, "Example", assignment, 100, 1000, EncounterObservation.SignalKind.DEATH);
		capture.verified(UUID.randomUUID(), EncounterObservation.Method.DEATH_AND_LOOT);
		capture.interrupt(103, EncounterObservation.Reason.LOGOUT);
		assertEquals(EncounterObservation.Outcome.VERIFIED, reports.get(0).outcome);
		assertFalse(reports.get(0).captureComplete); capture.advance(120); assertEquals(1, reports.size());
	}
	@Test public void unknownCounterIsCapturedWithoutCredit()
	{
		capture.counter(detector, "Example", assignment, 100, 1000, EncounterObservation.CounterSource.KC_MESSAGE, null, 9);
		capture.advance(112); assertNull(reports.get(0).creditEventId);
		assertEquals(EncounterObservation.Outcome.UNRESOLVED, reports.get(0).outcome);
	}
	@Test public void wirePayloadMatchesBotNamingAndMillisecondTimestamp()
	{
		capture.counter(detector, "Example", assignment, 100, 1000, EncounterObservation.CounterSource.KC_MESSAGE, null, 9);
		capture.advance(112);
		com.google.gson.JsonObject body = EncounterReportCodec.encode(reports.get(0));
		assertEquals("1970-01-01T00:00:01.000Z", body.get("observedAt").getAsString());
		assertEquals("unresolved", body.get("outcome").getAsString());
		assertEquals("window_expired", body.get("reason").getAsString());
		assertTrue(body.getAsJsonArray("signals").get(0).getAsJsonObject().get("previous").isJsonNull());
		assertFalse(body.has("creditEventId"));
		System.out.println("ENCOUNTER_WIRE_FIXTURE=" + body);
	}
	@Test public void repeatedPrimaryInSameTickIsCoalesced()
	{
		capture.primary(detector, "Example", assignment, 100, 1000, EncounterObservation.SignalKind.DEATH);
		capture.primary(detector, "Example", assignment, 100, 1000, EncounterObservation.SignalKind.DEATH);
		capture.advance(112); assertEquals(1, reports.size()); assertEquals(1, reports.get(0).signals.size());
	}
	@Test public void verifiedInterruptedWirePayloadRetainsCreditReference()
	{
		capture.primary(detector, "Example", assignment, 100, 1000, EncounterObservation.SignalKind.DEATH);
		capture.counter(detector, "Example", assignment, 101, 1600, EncounterObservation.CounterSource.KC_MESSAGE, 8, 9);
		capture.verified(UUID.randomUUID(), EncounterObservation.Method.DEATH_AND_COUNTER);
		capture.interrupt(103, EncounterObservation.Reason.SHUTDOWN);
		System.out.println("ENCOUNTER_WIRE_FIXTURE=" + EncounterReportCodec.encode(reports.get(0)));
	}
}

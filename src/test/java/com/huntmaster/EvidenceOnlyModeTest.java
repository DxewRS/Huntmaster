package com.huntmaster;

import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class EvidenceOnlyModeTest
{
	@Test public void publicRegistryRemainsCreditCapableWithoutDraftCandidates()
	{
		for (BossDetector detector : BossRegistry.createDetectors())
		{
			assertFalse(detector.getDefinition().isEvidenceOnly());
			assertNotEquals("Giant Mole", detector.getName());
			assertNotEquals("Sarachnis", detector.getName());
			assertNotEquals("Scurrius", detector.getName());
		}
	}
	@Test public void canonicalGauntletNameKeepsCounterIdentity()
	{
		for (BossDetector detector : BossRegistry.createDetectors())
			if (detector.getName().equals("The Corrupted Gauntlet"))
			{
				assertEquals("corrupted gauntlet", detector.getProfileKey());
				assertEquals("Your Corrupted Gauntlet completion count is:", detector.getKcMessagePrefix()); return;
			}
		fail("Canonical Gauntlet definition missing");
	}
	@Test(expected=IllegalStateException.class) public void observationCannotClaimVerifiedEvent()
	{
		new EncounterObservation(UUID.randomUUID(),UUID.randomUUID(),"Example","Sarachnis",BossDetectorType.STANDARD_NPC,
			"observation-v1",1000,100,10,true).markVerified(UUID.randomUUID(),EncounterObservation.Method.DEATH_AND_COUNTER);
	}
	@Test public void observationSnapshotExplicitlyDeclaresMode()
	{
		EncounterObservation record=new EncounterObservation(UUID.randomUUID(),UUID.randomUUID(),"Example","Sarachnis",
			BossDetectorType.STANDARD_NPC,"observation-v1",1000,100,10,true);
		record.recordPrimary(EncounterObservation.SignalKind.DEATH,100);record.closeIfExpired(110);
		assertEquals("evidence_only",EncounterReportCodec.encode(record.snapshot()).get("trackingMode").getAsString());
		assertNull(record.snapshot().creditEventId);
	}
	@Test public void counterParserIgnoresColourAndDurationDigits()
	{
		assertEquals(Integer.valueOf(1234),EvidenceOnlyCounter.parse("Your Sarachnis kill count is: <col=ff0000>1,234</col>. Fight duration: 0:45", "Your Sarachnis kill count is:"));
	}
	@Test public void unrelatedAndOverflowCountersAreIgnored()
	{
		assertNull(EvidenceOnlyCounter.parse(null, "Your Sarachnis kill count is:"));
		assertNull(EvidenceOnlyCounter.parse("Your Sarachnis kill count is: 12", null));
		assertNull(EvidenceOnlyCounter.parse("Your Sarachnis kill count is: 12" + "x".repeat(1024), "Your Sarachnis kill count is:"));
		assertNull(EvidenceOnlyCounter.parse("Your Kraken kill count is: 12", "Your Sarachnis kill count is:"));
		assertNull(EvidenceOnlyCounter.parse("Your Sarachnis kill count is: 9999999999999", "Your Sarachnis kill count is:"));
	}
	@Test public void observationPrefixAcceptsBossColourAndCaseWithoutChangingCreditRoutes()
	{
		String prefix="Your Giant Mole kill count is:";
		String message="Your <col=ff0000>Giant mole</col> kill count is: <col=ff0000>12</col>.";
		assertTrue(EvidenceOnlyCounter.matchesPrefix(message,prefix));
		assertEquals(Integer.valueOf(12),EvidenceOnlyCounter.parse(message,prefix));
		assertFalse(EvidenceOnlyCounter.matchesPrefix("Your Baby mole kill count is: 12.",prefix));
		assertFalse(EvidenceOnlyCounter.matchesPrefix(null,prefix));
	}
}

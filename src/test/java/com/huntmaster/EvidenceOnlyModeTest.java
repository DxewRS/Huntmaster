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
	@Test public void observationSnapshotExplicitlyDeclaresMode()
	{
		EncounterObservation record=new EncounterObservation(UUID.randomUUID(),UUID.randomUUID(),"Example","Sarachnis",
			BossDetectorType.STANDARD_NPC,"observation-v1",1000,100,10,true);
		record.recordPrimary(EncounterObservation.SignalKind.DEATH,100);record.closeIfExpired(110);
		assertEquals("server_observation",EncounterReportCodec.encodeObservation(record.snapshot()).get("trackingMode").getAsString());
		assertFalse(EncounterReportCodec.encodeObservation(record.snapshot()).has("creditEventId"));
	}
}

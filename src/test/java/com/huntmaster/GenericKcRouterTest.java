package com.huntmaster;

import org.junit.Test;
import static org.junit.Assert.*;

public class GenericKcRouterTest
{
    @Test public void reviewedCounterAliasesKeepCompositeAndVariantNamesSeparate()
    {
        assertEquals(Integer.valueOf(12),GenericKcRouter.parse("Your Barrows chest count is: 12.","Barrows Brothers"));
        assertEquals(Integer.valueOf(13),GenericKcRouter.parse("Your Lunar Chest count is: 13.","Moons of Peril"));
        assertEquals(Integer.valueOf(14),GenericKcRouter.parse("Your Nightmare kill count is: 14.","The Nightmare"));
        assertEquals(Integer.valueOf(15),GenericKcRouter.parse("Your Hueycoatl kill count is: 15.","The Hueycoatl"));
        assertNull(GenericKcRouter.parse("Your Phosani's Nightmare kill count is: 14.","The Nightmare"));
        assertNull(GenericKcRouter.parse("Your Dagannoth Rex kill count is: 15.","Dagannoth Kings"));
        assertNull(GenericKcRouter.parse("Your Corrupted Gauntlet completion count is: 16.","The Gauntlet"));
        assertFalse(BossCounterAliases.matches("Moons of Peril","Blood Moon"));
    }
    @Test public void betaCandidatesAreLabelledButCannotBeLocallyMarkedVerified()
    {
        BossDefinition d=BossDefinition.betaCandidate("Scurrius","scurrius","Your Scurrius kill count is:");
        assertTrue(d.isEvidenceOnly());
        EncounterObservation r=new EncounterObservation(java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),
            "Example",d.getName(),d.getDetectorType(),d.getDetectorVersion(),1000,100,10,true);
        r.recordCounter(101,EncounterObservation.CounterSource.KC_MESSAGE,null,107);
        r.closeIfExpired(110);
        assertEquals("beta_candidate",EncounterReportCodec.encode(r.snapshot()).get("trackingMode").getAsString());
        assertNull(r.snapshot().creditEventId);
    }
    @Test public void parsesAssignedPersonalCountersWithoutReadingDurationDigits()
    {
        assertEquals(Integer.valueOf(1234), GenericKcRouter.parse("Your Scurrius kill count is: <col=ff0000>1,234</col>. Fight duration: 0:45", "Scurrius"));
        assertEquals(Integer.valueOf(12),GenericKcRouter.parse("Your completion count for The Mimic is: 12.","The Mimic"));
        assertNull(GenericKcRouter.parse("Your Scurrius kill count is: 12.","Sarachnis"));
        assertNull(GenericKcRouter.parse("Other player: Your Scurrius kill count is: 12.","Scurrius"));
        assertNull(GenericKcRouter.parse("Your Scurrius kill count is: 9999999999999.","Scurrius"));
        assertNull(GenericKcRouter.parse("Your Chambers of Xeric completion count is: 12.","Chambers of Xeric"));
    }
}

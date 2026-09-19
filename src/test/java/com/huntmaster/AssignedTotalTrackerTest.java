package com.huntmaster;

import org.junit.Test;
import static org.junit.Assert.*;

public class AssignedTotalTrackerTest
{
    @Test public void initialReadAndResumptionNeverCreditAccumulatedTotals()
    {
        AssignedTotalTracker tracker = new AssignedTotalTracker();
        assertNull(tracker.changed("player:assignment:Sol", 50, 100));
        assertNull(tracker.changed("player:assignment:Sol", 50, 101));
        assertEquals(Integer.valueOf(50), tracker.changed("player:assignment:Sol", 51, 102));
        tracker.clear();
        assertNull(tracker.changed("player:assignment:Sol", 52, 110));
        assertEquals(Integer.valueOf(52), tracker.changed("player:assignment:Sol", 53, 111));
        assertNull(tracker.changed("player:newAssignment:Sol", 54, 112));
        assertNull(tracker.changed("other:newAssignment:Sol", 99, 113));
        assertNull(tracker.changed("other:newAssignment:Sol", 99, 1));
    }
    @Test public void metricsExcludeWavesAndGloryAndUseDistinctBossTotals()
    {
        assertEquals(Integer.valueOf(net.runelite.api.gameval.VarPlayerID.TOTAL_SOL_KILLS), SpecialEncounterTotals.varp("Sol Heredit"));
        assertEquals(Integer.valueOf(net.runelite.api.gameval.VarPlayerID.DOM_LEVEL_HIGHSCORES), SpecialEncounterTotals.varp("Doom of Mokhaiotl"));
        assertNotEquals(SpecialEncounterTotals.varp("TzTok-Jad"), SpecialEncounterTotals.varp("TzKal-Zuk"));
        assertNull(SpecialEncounterTotals.varp("Dagannoth Kings"));
        assertNull(SpecialEncounterTotals.varp("Colosseum Glory"));
    }
    @Test public void completionWireVersionsKeepUnconfirmedDoomObservationOnly()
    {
        for (String boss : new String[]{"Royal Titans", "Doom of Mokhaiotl"})
        {
            BossDetector detector = new BossDetector(BossDefinition.totalCounterCandidate(boss, "test"));
            java.util.List<EncounterObservation.Snapshot> reports = new java.util.ArrayList<>();
            EncounterCapture capture = new EncounterCapture(reports::add);
            String assignment = java.util.UUID.randomUUID().toString();
            capture.primary(detector, "Fixture", assignment, 100, 1000, EncounterObservation.SignalKind.COMPLETION);
            capture.counter(detector, "Fixture", assignment, 100, 1000, EncounterObservation.CounterSource.COMPLETION_VARP, 40, 41);
            capture.advance(110);
            com.google.gson.JsonObject wire = EncounterReportCodec.encode(reports.get(0));
            assertEquals("COMPLETION", wire.get("detectorType").getAsString());
            assertEquals(boss.equals("Doom of Mokhaiotl") ? "evidence_only" : "beta_candidate", wire.get("trackingMode").getAsString());
            assertFalse(wire.has("creditEventId"));
        }
    }
}

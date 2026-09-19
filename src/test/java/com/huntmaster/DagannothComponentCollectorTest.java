package com.huntmaster;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class DagannothComponentCollectorTest
{
    @Test public void interleavedIdenticalTotalsHaveSeparateBaselinesAndEvidenceOnlyReports()
    {
        List<EncounterObservation.Snapshot> reports = new ArrayList<>();
        DagannothComponentCollector c = new DagannothComponentCollector(reports::add);
        String id = UUID.randomUUID().toString();
        String[] names = {"Rex", "Prime", "Supreme"};
        for (String name : names)
        {
            assertTrue(c.npc("Dagannoth " + name, false, "Example", id, 100, 1000));
        }
        for (String name : names)
        {
            assertTrue(c.counter("Your Dagannoth " + name + " kill count is: 50.", "Example", id, 101, 1600));
        }
        for (String name : names)
        {
            assertTrue(c.npc("Dagannoth " + name, true, "Example", id, 102, 2200));
        }
        c.advance(110);
        assertEquals(3, reports.size());
        assertEquals(3, reports.stream().map(r -> r.detectorVersion).distinct().count());
        for (EncounterObservation.Snapshot r : reports)
        {
            assertEquals("Dagannoth Kings", r.boss);
            assertTrue(r.evidenceOnly);
            assertNull(r.creditEventId);
            assertNull(r.signals.get(1).previous);
            assertEquals("beta_candidate", EncounterReportCodec.encode(r).get("trackingMode").getAsString());
        }
        c.counter("Your Dagannoth Rex kill count is: 51.", "Example", id, 120, 13000);
        c.advance(130);
        assertEquals(Integer.valueOf(50), reports.get(3).signals.get(0).previous);
        c.interrupt(131, EncounterObservation.Reason.LOGOUT);
        c.counter("Your Dagannoth Rex kill count is: 52.", "Example", id, 140, 25000);
        c.advance(150);
        assertNull(reports.get(4).signals.get(0).previous);
        assertFalse(c.counter("Your Dagannoth Kings kill count is: 820.", "Example", id, 151, 26000));
        assertFalse(c.npc("Spinolyp", false, "Example", id, 151, 26000));
    }
    @Test public void assignmentChangeDoesNotReuseCounterOrOtherKingSignals()
    {
        List<EncounterObservation.Snapshot> reports = new ArrayList<>();
        DagannothComponentCollector c = new DagannothComponentCollector(reports::add);
        String old = UUID.randomUUID().toString(), next = UUID.randomUUID().toString();
        c.counter("Your Dagannoth Rex kill count is: 50.", "Example", old, 100, 1000);
        c.npc("Dagannoth Prime", false, "Example", old, 101, 1600);
        c.counter("Your Dagannoth Rex kill count is: 51.", "Example", next, 102, 2200);
        c.advance(112);
        EncounterObservation.Snapshot latest = reports.get(reports.size() - 1);
        assertEquals(UUID.fromString(next), latest.assignmentId);
        assertEquals(1, latest.signals.size());
        assertNull(latest.signals.get(0).previous);
    }
}

package com.huntmaster;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class BroadEncounterCaptureTest
{
    private BossDetector boss(String name){return new BossDetector(BossDefinition.betaCandidate(name,name.toLowerCase(),"Your "+name+" kill count is:"));}
    @Test public void differentEncountersKeepSeparateCountersAndNullableAssignment()
    {
        List<EncounterObservation.Snapshot> rows=new ArrayList<>();EncounterCapture c=new EncounterCapture(rows::add);
        BossDetector a=boss("Artio"),b=boss("Callisto");String task=UUID.randomUUID().toString();
        c.primary(a,"Example",task,100,1000,EncounterObservation.SignalKind.DEATH);
        c.primary(b,"Example",task,101,1600,EncounterObservation.SignalKind.DEATH);
        c.counter(a,"Example",task,102,2200,EncounterObservation.CounterSource.KC_MESSAGE,20,21);
        c.counter(b,"Example",task,103,2800,EncounterObservation.CounterSource.KC_MESSAGE,20,21);
        c.advance(240);assertEquals(2,rows.size());
        assertEquals("Artio",rows.get(0).boss);assertEquals("Callisto",rows.get(1).boss);
        for(EncounterObservation.Snapshot row:rows){assertEquals(2,row.signals.size());assertEquals(task,row.assignmentId.toString());}
        c.primary(a,"Example",null,250,10000,EncounterObservation.SignalKind.DEATH);c.advance(400);
        assertNull(rows.get(2).assignmentId);assertTrue(EncounterReportCodec.encodeObservation(rows.get(2)).get("assignmentId").isJsonNull());
    }
    @Test public void interruptionAndDiagnosticOnlyDoNotInventCompletion()
    {
        List<EncounterObservation.Snapshot> rows=new ArrayList<>();EncounterCapture c=new EncounterCapture(rows::add);
        c.notice(boss("Nex"),"Example",null,100,1000,"UNPARSED_GAMEMESSAGE",null);
        c.interrupt(102,EncounterObservation.Reason.LOGOUT);assertEquals(1,rows.size());
        assertFalse(rows.get(0).captureComplete);assertEquals(EncounterObservation.SignalKind.DIAGNOSTIC,rows.get(0).signals.get(0).kind);
        c.advance(400);assertEquals(1,rows.size());
    }
}

package com.huntmaster;

import com.google.gson.JsonObject;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Accelerated state/lifecycle exercise, separate from the owner's real normal-play monitor. */
public class LongSessionSimulationTest
{
    @Test public void eightHoursOfPollingAccountSwitchesAndLateResponsesKeepCurrentState()
    {
        AssignmentDashboardState state=new AssignmentDashboardState();String id=UUID.randomUUID().toString();
        long now=1000,sequence=1;state.reset("Fixture",now);
        for(int poll=1;poll<=5760;poll++){
            now+=5000;int progress=poll%90;
            if(poll%120==0){state.reset(null,now);assertNull(state.view().assignment);state.reset("Fixture",now);id=UUID.randomUUID().toString();}
            JsonObject response=ReleaseHardeningTest.hunt(id,progress,sequence++);assertTrue(state.accept(response,now,null,0));
            assertEquals(progress,state.view().assignment.progress);
            if(poll%30==0){
                JsonObject late=ReleaseHardeningTest.hunt(id,0,sequence-3);assertFalse(state.accept(late,now+1,null,0));assertEquals(progress,state.view().assignment.progress);
                state.connection("Huntmaster unavailable");assertNull(state.overlay(now,true,HuntOverlayVisibility.WHILE_HUNT_ACTIVE));
                state.accept(ReleaseHardeningTest.hunt(id,progress,sequence++),now+2,null,0);assertTrue(state.view().connected);
            }
            if(poll%60==0){
                JsonObject other=ReleaseHardeningTest.hunt(id,99,sequence++);other.addProperty("rsn","Other");assertFalse(state.accept(other,now,null,0));assertEquals(progress,state.view().assignment.progress);
            }
        }
        state.reset(null,now);assertNull(state.view().assignment);assertNull(state.view().rank);
    }

    @Test public void repeatedQueueRestoreAndRetryKeepIdsPayloadsAndMemoryBounded()
    {
        EncounterReportQueue queue=new EncounterReportQueue();long now=1000;
        for(int cycle=0;cycle<100;cycle++){
            for(int i=0;i<20;i++){String id=UUID.randomUUID().toString();assertTrue(queue.add(id,"{\"id\":\""+id+"\"}",now,now,true));}
            EncounterReportQueue.Entry[] original=queue.snapshot();EncounterReportQueue restored=new EncounterReportQueue();
            for(EncounterReportQueue.Entry entry:original)assertTrue(restored.add(entry.id,entry.payload,entry.createdAt,now+86400000L,true));
            assertEquals(original.length,restored.snapshot().length);
            for(EncounterReportQueue.Entry entry:original){EncounterReportQueue.Entry retry=restored.next(now+86400000L);assertNotNull(retry);assertEquals(entry.id,retry.id);assertEquals(entry.payload,retry.payload);retry.inFlight=true;assertNull(restored.next(now+86400000L));restored.releaseInFlight();assertEquals(entry.id,restored.next(now+86400000L).id);restored.acknowledge(entry.id);}
            assertEquals(0,restored.snapshot().length);queue=restored;now+=5000;
        }
    }
}

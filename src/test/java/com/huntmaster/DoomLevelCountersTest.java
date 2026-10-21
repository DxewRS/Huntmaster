package com.huntmaster;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.runelite.api.ChatMessageType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class DoomLevelCountersTest
{
    @Parameterized.Parameters(name="level {0}")
    public static Iterable<Object[]> levels() { return Arrays.asList(new Object[][]{{7},{8},{9},{12}}); }
    private final int level;
    private final List<EncounterObservation.Snapshot> reports=new ArrayList<>();
    private final EncounterCapture capture=new EncounterCapture(reports::add);
    private final BossDetector boss=new BossDetector(BossDefinition.totalCounterCandidate("Doom of Mokhaiotl","doom"));
    private final String assignment=UUID.randomUUID().toString();
    public DoomLevelCountersTest(int level) { this.level=level; }
    private DoomLevelCounters.Source source() { return DoomLevelCounters.Source.values()[Math.min(level,9)-1]; }
    private void message(int tick)
    {
        String value="Delve level: "+(level>=9?"8+ ("+level+")":level)+" duration: 1:23.4";
        capture.doomCompletion(boss,"PrivateFixture",assignment,tick,100000L+tick*600,DoomCompletionEvidence.parse(ChatMessageType.GAMEMESSAGE,value));
    }
    private void counter(int tick,int previous,int current)
    { capture.doomCounter(boss,"PrivateFixture",assignment,tick,100000L+tick*600,new DoomLevelCounters.Observation(source(),previous,current,0)); }
    private void aggregate(int tick)
    { capture.counter(boss,"PrivateFixture",assignment,tick,100000L+tick*600,EncounterObservation.CounterSource.COMPLETION_VARP,100,101); }
    @Test public void messageThenCounterAndAggregate()
    {
        message(10);counter(11,12,13);aggregate(12);capture.advance(30);
        assertEquals(1,reports.size());EncounterObservation.Snapshot r=reports.get(0);
        assertEquals(level,r.doomState.completedLevel);assertEquals(source(),r.doomCounter.source);assertEquals(12,r.doomCounter.previous);assertEquals(13,r.doomCounter.current);
        assertEquals(1,r.doomCounter.tickOffset);assertTrue(r.signals.stream().anyMatch(s->s.kind==EncounterObservation.SignalKind.COUNTER));
        JsonObject payload=EncounterReportCodec.encodeObservation(r);
        assertEquals(1,payload.getAsJsonObject("doomCounters").getAsJsonArray("counters").size());assertEquals(1,payload.getAsJsonObject("doomState").get("version").getAsInt());
    }
    @Test public void counterThenMessageAndAggregate()
    { counter(10,12,13);aggregate(11);message(12);capture.advance(30);assertEquals(1,reports.size());assertEquals(level,reports.get(0).doomState.completedLevel);assertEquals(2,reports.get(0).doomState.tickOffset); }
    @Test public void baselineUnchangedPausedDecreaseJumpAndUnavailableRemainObjective()
    {
        DoomLevelCounters sampler=new DoomLevelCounters();Map<Integer,Integer> values=new HashMap<>();List<DoomLevelCounters.Observation> changes=new ArrayList<>();
        sampler.sample(id->values.getOrDefault(id,12),true,changes::add);assertTrue(changes.isEmpty());
        sampler.sample(id->values.getOrDefault(id,12),true,changes::add);assertTrue(changes.isEmpty());
        values.put(source().varp,13);sampler.sample(id->values.getOrDefault(id,12),false,changes::add);sampler.sample(id->values.getOrDefault(id,12),true,changes::add);assertTrue(changes.isEmpty());
        values.put(source().varp,10);sampler.sample(id->values.getOrDefault(id,12),true,changes::add);assertEquals(13,changes.get(0).previous);assertEquals(10,changes.get(0).current);
        values.put(source().varp,15);sampler.sample(id->values.getOrDefault(id,12),true,changes::add);assertEquals(10,changes.get(1).previous);assertEquals(15,changes.get(1).current);
        values.put(source().varp,-1);sampler.sample(id->values.getOrDefault(id,12),true,changes::add);values.put(source().varp,99);sampler.sample(id->values.getOrDefault(id,12),true,changes::add);assertEquals(2,changes.size());
        for(DoomLevelCounters.Observation c:changes)capture.doomCounter(boss,"PrivateFixture",assignment,10,100000,c);
        capture.advance(30);assertEquals(2,reports.size());for(EncounterObservation.Snapshot r:reports){assertNull(r.doomState);assertFalse(r.signals.stream().anyMatch(s->s.kind==EncounterObservation.SignalKind.COMPLETION));}
    }
    @Test public void duplicateMessageAndCounterDoNotReuseCorroboration()
    {
        counter(10,12,13);counter(11,12,13);message(12);message(12);message(13);capture.advance(30);
        assertEquals(2,reports.size());assertNotNull(reports.get(0).doomCounter);assertNull(reports.get(1).doomCounter);
    }
    @Test public void ambiguityAndWrongLevelDoNotGuess()
    {
        counter(10,12,13);counter(11,13,14);message(12);capture.advance(30);assertEquals(3,reports.size());assertNull(reports.get(2).doomCounter);
        capture.clear();reports.clear();counter(40,20,21);
        DoomCompletionEvidence other=DoomCompletionEvidence.parse(ChatMessageType.GAMEMESSAGE,"Delve level: 1 duration: 1:00");
        capture.doomCompletion(boss,"PrivateFixture",assignment,41,100000,other);capture.advance(60);assertEquals(2,reports.size());assertNull(reports.get(0).doomState);assertNull(reports.get(1).doomCounter);
    }
    @Test public void messageOnlyCounterOnlyAndExpiredEvidenceStayDistinct()
    {
        message(10);capture.advance(30);assertEquals(level,reports.get(0).doomState.completedLevel);assertNull(reports.get(0).doomCounter);
        counter(40,12,13);capture.advance(60);assertNull(reports.get(1).doomState);assertNotNull(reports.get(1).doomCounter);
    }
    @Test public void interruptedContextCannotPairButFinalizedQueueSurvives()
    {
        counter(10,12,13);capture.interrupt(11,EncounterObservation.Reason.LOGOUT);message(12);capture.advance(30);
        assertFalse(reports.get(0).captureComplete);assertNull(reports.get(1).doomCounter);
        capture.clear();reports.clear();message(40);counter(41,20,21);capture.advance(60);capture.interrupt(61,EncounterObservation.Reason.LOGOUT);
        EncounterObservation.Snapshot r=reports.get(0);assertTrue(r.captureComplete);
        String payload=EncounterReportCodec.encodeObservation(r).toString();EncounterReportQueue queue=new EncounterReportQueue();assertTrue(queue.add(r.reportId.toString(),payload,1000,1000,true));assertFalse(queue.add(r.reportId.toString(),payload,1000,1000,true));
        EncounterReportQueue.Entry original=queue.next(1000);original.inFlight=true;queue.releaseInFlight();assertEquals(payload,queue.next(1001).payload);
        EncounterReportQueue.Entry restored=new Gson().fromJson(new Gson().toJson(original),EncounterReportQueue.Entry.class);EncounterReportQueue next=new EncounterReportQueue();assertTrue(next.add(restored.id,restored.payload,restored.createdAt,1001,true));assertEquals(original.id,next.next(1001).id);assertEquals(payload,next.next(1001).payload);
    }
    @Test public void optionalCapabilityAndPrivacy()
    {
        message(10);counter(11,12,13);aggregate(12);capture.advance(30);EncounterObservation.Snapshot r=reports.get(0);
        JsonObject legacy=EncounterReportCodec.encodeObservation(r,true,false);assertFalse(legacy.has("doomCounters"));assertTrue(legacy.has("doomState"));assertEquals(1,legacy.getAsJsonObject("doomState").get("version").getAsInt());
        CollectionPolicy policy=new CollectionPolicy();JsonObject p=new Gson().fromJson("{\"version\":2,\"expiresAt\":10000,\"windows\":{},\"doomEvidenceVersion\":1}",JsonObject.class);policy.accept(p,100);assertFalse(policy.supportsDoomCounters(101));p.addProperty("doomCounterEvidenceVersion",1);policy.accept(p,100);assertTrue(policy.supportsDoomCounters(101));assertFalse(policy.supportsDoomCounters(10001));p.addProperty("doomCounterEvidenceVersion",2);policy.accept(p,100);assertFalse(policy.supportsDoomCounters(101));
        JsonObject supported=EncounterReportCodec.encodeObservation(r,true,true);
        JsonObject counter=supported.getAsJsonObject("doomCounters").getAsJsonArray("counters").get(0).getAsJsonObject();
        assertEquals(source().name(),counter.get("source").getAsString());
        assertEquals(12,counter.get("previous").getAsInt());assertEquals(13,counter.get("current").getAsInt());
        assertTrue(supported.has("doomState"));assertFalse(supported.toString().contains("duration:"));
    }
}

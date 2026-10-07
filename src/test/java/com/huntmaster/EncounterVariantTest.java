package com.huntmaster;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncounterVariantTest
{
    private final BossDetector boss=new BossDetector(BossDefinition.betaCandidate("Vardorvis","vardorvis","fixture"));
    private EncounterObservation.Snapshot report(EncounterVariant variant)
    {
        List<EncounterObservation.Snapshot> reports=new ArrayList<>();EncounterCapture capture=new EncounterCapture(reports::add);
        if(variant==null)capture.primary(boss,"PrivateAccount",null,100,1000,EncounterObservation.SignalKind.DEATH);
        else capture.variantCompletion(boss,"PrivateAccount",null,100,1000,variant,1,2);
        capture.advance(300);return reports.get(0);
    }
    @Test public void absentAndUnsupportedNormalRetainOldWireShape()
    {
        assertFalse(EncounterReportCodec.encodeObservation(report(null)).has("encounterVariant"));
        assertFalse(EncounterReportCodec.encodeObservation(report(EncounterVariant.NORMAL),false).has("encounterVariant"));
        assertEquals("AWAKENED",EncounterReportCodec.encodeObservation(report(EncounterVariant.AWAKENED),false).get("encounterVariant").getAsString());
    }
    @Test public void invalidVariantsAndBossesAreRejected()
    {
        assertEquals(2,EncounterVariant.values().length);
        try { EncounterVariant.valueOf("OTHER");fail(); } catch(IllegalArgumentException expected) { }
        EncounterCapture capture=new EncounterCapture(s->{});
        try { capture.variantCompletion(boss,"PrivateAccount",null,100,1000,null,1,2);fail(); } catch(IllegalArgumentException expected) { }
        BossDetector wrong=new BossDetector(BossDefinition.betaCandidate("Nex","nex","fixture"));
        try { capture.variantCompletion(wrong,"PrivateAccount",null,100,1000,EncounterVariant.AWAKENED,1,2);fail(); } catch(IllegalArgumentException expected) { }
    }
    @Test public void delayedNormalAndAwakenedStaySeparateFromUnknownNpc()
    {
        List<EncounterObservation.Snapshot> reports=new ArrayList<>();EncounterCapture capture=new EncounterCapture(reports::add);
        capture.primary(boss,"PrivateAccount",null,100,1000,EncounterObservation.SignalKind.DEATH);
        capture.variantCompletion(boss,"PrivateAccount",null,101,1600,EncounterVariant.AWAKENED,1,2);
        capture.variantCompletion(boss,"PrivateAccount",null,102,2200,EncounterVariant.NORMAL,50,51);
        capture.counter(boss,"PrivateAccount",null,103,2800,EncounterObservation.CounterSource.KC_MESSAGE,50,51);
        capture.advance(300);assertEquals(3,reports.size());
        for(EncounterObservation.Snapshot row:reports)if(row.encounterVariant!=null)
        { assertFalse(row.signals.stream().anyMatch(s->s.kind==EncounterObservation.SignalKind.DEATH));assertEquals(2,row.signals.size()); }
    }
    @Test public void nextEncounterAndLifecycleCannotInheritVariant()
    {
        List<EncounterObservation.Snapshot> reports=new ArrayList<>();EncounterCapture capture=new EncounterCapture(reports::add);
        capture.variantCompletion(boss,"PrivateAccount",null,100,1000,EncounterVariant.AWAKENED,1,2);
        capture.interruptVariants(101);assertFalse(reports.get(0).captureComplete);
        capture.variantCompletion(boss,"PrivateAccount",null,102,2200,EncounterVariant.NORMAL,50,51);
        capture.advance(300);assertEquals(EncounterVariant.NORMAL,reports.get(1).encounterVariant);
        capture.clear();capture.primary(boss,"OtherAccount",null,400,5000,EncounterObservation.SignalKind.DEATH);
        capture.advance(600);assertNull(reports.get(2).encounterVariant);
    }
    @Test public void capabilitiesAreVersionedOptionalAndExpire()
    {
        CollectionPolicy policy=new CollectionPolicy();assertFalse(policy.supportsEncounterVariants(100));
        for(int version:new int[]{2,1})
        {
            policy.accept(new Gson().fromJson("{\"version\":2,\"expiresAt\":1000,\"windows\":{},\"encounterVariantVersion\":"+version+"}",JsonObject.class),100);
            assertEquals(version==1,policy.supportsEncounterVariants(101));assertFalse(policy.supportsEncounterVariants(1000));
        }
        policy.accept(null,100);assertFalse(policy.supportsEncounterVariants(101));
    }
    @Test public void queuePersistenceAndDuplicateKeepOriginalPayload()
    {
        Gson gson=new Gson();JsonObject body=EncounterReportCodec.encodeObservation(report(EncounterVariant.AWAKENED));
        EncounterReportQueue queue=new EncounterReportQueue();String id=body.get("reportId").getAsString(),payload=body.toString();
        assertTrue(queue.add(id,payload,1000,1000,true));assertFalse(queue.add(id,payload,1000,1000,true));
        EncounterReportQueue restored=new EncounterReportQueue();
        for(EncounterReportQueue.Entry entry:gson.fromJson(gson.toJson(queue.snapshot()),EncounterReportQueue.Entry[].class))
            assertTrue(restored.add(entry.id,entry.payload,entry.createdAt,1001,entry.creditCandidate));
        assertEquals(id,restored.snapshot()[0].id);assertEquals(payload,restored.snapshot()[0].payload);
    }
    private void finalize(ImmediateReportDeliveryTest.Harness h,EncounterVariant variant)throws Exception
    {
        EncounterCapture capture=(EncounterCapture)ImmediateReportDeliveryTest.field("encounterCapture").get(h.plugin);
        capture.variantCompletion(boss,"Example",null,100,System.currentTimeMillis(),variant,1,2);capture.advance(125);
    }
    private void capable(ImmediateReportDeliveryTest.Harness h)throws Exception
    {
        ((CollectionPolicy)ImmediateReportDeliveryTest.field("collectionPolicy").get(h.plugin)).accept(
            new Gson().fromJson("{\"version\":2,\"expiresAt\":"+(System.currentTimeMillis()+60000)+",\"windows\":{},\"encounterVariantVersion\":1}",JsonObject.class),System.currentTimeMillis());
    }
    @Test public void oldBotDoesNotBlockNormalButAwakenedWaitsWithoutDowngrade()throws Exception
    {
        ImmediateReportDeliveryTest.Harness normal=new ImmediateReportDeliveryTest.Harness();finalize(normal,EncounterVariant.NORMAL);
        assertEquals(1,normal.calls.size());assertFalse(new Gson().fromJson(normal.queue.snapshot()[0].payload,JsonObject.class).has("encounterVariant"));
        ImmediateReportDeliveryTest.Harness awakened=new ImmediateReportDeliveryTest.Harness();finalize(awakened,EncounterVariant.AWAKENED);
        assertEquals(0,awakened.calls.size());assertEquals(1,awakened.queue.snapshot().length);
        capable(awakened);ImmediateReportDeliveryTest.invoke(awakened.plugin,"updateEncounterReports");assertEquals(1,awakened.calls.size());
    }
    @Test public void transportRetryRestoresIdentityAndAcceptsIgnoringServer()throws Exception
    {
        ImmediateReportDeliveryTest.Harness h=new ImmediateReportDeliveryTest.Harness();capable(h);finalize(h,EncounterVariant.AWAKENED);
        EncounterReportQueue.Entry original=h.queue.snapshot()[0];String id=original.id,payload=original.payload;
        h.calls.get(0).callback.onFailure(h.calls.get(0),new IOException("offline"));
        assertEquals(id,h.queue.snapshot()[0].id);assertEquals(payload,h.queue.snapshot()[0].payload);
        // Exercise the existing serialized-entry restoration and real delivery callback.
        h.queue.acknowledge(id);EncounterReportQueue.Entry saved=new Gson().fromJson(new Gson().toJson(original),EncounterReportQueue.Entry.class);
        assertTrue(h.queue.add(saved.id,saved.payload,saved.createdAt,System.currentTimeMillis(),saved.creditCandidate));
        ImmediateReportDeliveryTest.set(h.plugin,"healthReachable",true);ImmediateReportDeliveryTest.set(h.plugin,"assignmentSyncRequired",false);
        ImmediateReportDeliveryTest.invoke(h.plugin,"updateEncounterReports");assertEquals(2,h.calls.size());
        h.calls.get(1).respond("{\"success\":true,\"status\":\"stored\",\"reportId\":\""+id+"\"}");assertEquals(0,h.queue.snapshot().length);
    }
    @Test public void copiedDiagnosticsAreBoundedPrivateAndReset()
    {
        CollectorDiagnostics diagnostics=new CollectorDiagnostics();diagnostics.queued(report(EncounterVariant.AWAKENED));
        String text=diagnostics.copy("active",1,"default-v2");assertTrue(text.contains("Encounter: Vardorvis"));assertTrue(text.contains("Variant: AWAKENED"));
        assertFalse(text.contains("PrivateAccount"));assertFalse(text.contains("signals"));
        diagnostics.reset();text=diagnostics.copy("active",0,"default-v2");assertFalse(text.contains("Vardorvis"));assertFalse(text.contains("AWAKENED"));
    }
    @Test public void doomDiagnosticsKeepStructuredFactsWithoutChatOrAccount()
    {
        List<EncounterObservation.Snapshot> reports=new ArrayList<>();EncounterCapture capture=new EncounterCapture(reports::add);
        BossDetector doom=new BossDetector(BossDefinition.totalCounterCandidate(DoomCompletionEvidence.BOSS,"doom"));
        capture.doomCompletion(doom,"PrivateAccount",null,100,1000,DoomCompletionEvidence.parse(net.runelite.api.ChatMessageType.GAMEMESSAGE,"Delve level: 8+ (9) duration: 1:02.40"));
        capture.advance(300);CollectorDiagnostics diagnostics=new CollectorDiagnostics();diagnostics.queued(reports.get(0));
        String text=diagnostics.copy("active",1,"default-v2");assertTrue(text.contains("Completed Level: 9"));assertTrue(text.contains("Extended: yes"));
        assertFalse(text.contains("PrivateAccount"));assertFalse(text.contains("duration:"));
    }
    @Test public void realPluginSamplesDedicatedTotalsAndRetainsAwakenedIdentity()throws Exception
    {
        java.lang.reflect.Method fixture=CounterIdentityRoutingTest.class.getDeclaredMethod("harness",String.class);fixture.setAccessible(true);
        ImmediateReportDeliveryTest.Harness h=(ImmediateReportDeliveryTest.Harness)fixture.invoke(new CounterIdentityRoutingTest(),"Brutus");
        net.runelite.api.Client original=(net.runelite.api.Client)ImmediateReportDeliveryTest.field("client").get(h.plugin);
        int[] total={10},tick={125};
        net.runelite.api.Client client=(net.runelite.api.Client)java.lang.reflect.Proxy.newProxyInstance(net.runelite.api.Client.class.getClassLoader(),new Class[]{net.runelite.api.Client.class},
            (p,m,a)->m.getName().equals("getTickCount")?tick[0]:m.getName().equals("getVarpValue")?((int)a[0]==Dt2CompletionEvidence.AWAKENED[0]?total[0]:10):m.invoke(original,a));
        ImmediateReportDeliveryTest.set(h.plugin,"client",client);
        ImmediateReportDeliveryTest.invoke(h.plugin,"observeDt2Completions");assertEquals(0,h.queue.snapshot().length);
        total[0]=11;tick[0]=126;ImmediateReportDeliveryTest.invoke(h.plugin,"observeDt2Completions");
        ((EncounterCapture)ImmediateReportDeliveryTest.field("encounterCapture").get(h.plugin)).advance(140);
        assertEquals(1,h.queue.snapshot().length);JsonObject payload=new Gson().fromJson(h.queue.snapshot()[0].payload,JsonObject.class);
        assertEquals("Duke Sucellus",payload.get("boss").getAsString());assertEquals("AWAKENED",payload.get("encounterVariant").getAsString());
        assertEquals(0,h.calls.size());assertEquals(0,ImmediateReportDeliveryTest.field("collectionCaptureFailures").getLong(h.plugin));
        net.runelite.api.events.GameStateChanged state=new net.runelite.api.events.GameStateChanged();state.setGameState(net.runelite.api.GameState.HOPPING);
        h.plugin.onGameStateChanged(state);assertEquals(1,h.queue.snapshot().length);
        tick[0]=150;total[0]=20;ImmediateReportDeliveryTest.invoke(h.plugin,"observeDt2Completions");
        ((EncounterCapture)ImmediateReportDeliveryTest.field("encounterCapture").get(h.plugin)).advance(170);assertEquals(1,h.queue.snapshot().length);
    }
    @Test public void counterJumpRemainsOneRawObservationNotMultipleCredits()
    {
        Dt2CompletionEvidence detector=new Dt2CompletionEvidence();int[] total={10};List<Dt2CompletionEvidence.Completion> results=new ArrayList<>();
        detector.sample(id->total[0],true,results::add);total[0]=15;detector.sample(id->total[0],true,results::add);
        assertEquals(4,results.size());for(Dt2CompletionEvidence.Completion completion:results)
        { assertEquals(10,completion.previous);assertEquals(15,completion.current);assertEquals(EncounterVariant.AWAKENED,completion.variant); }
    }
}

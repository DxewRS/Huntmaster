package com.huntmaster;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import org.junit.Test;
import static org.junit.Assert.*;

public class DoomCounterDeliveryTest
{
    @Test public void realPluginReadsFixedCountersAndResetsAtWorldHop() throws Exception
    {
        Method fixture=CounterIdentityRoutingTest.class.getDeclaredMethod("harness",String.class);fixture.setAccessible(true);
        ImmediateReportDeliveryTest.Harness h=(ImmediateReportDeliveryTest.Harness)fixture.invoke(new CounterIdentityRoutingTest(),"Brutus");policy(h,true);
        net.runelite.api.Client original=(net.runelite.api.Client)ImmediateReportDeliveryTest.field("client").get(h.plugin);int[] total={10},tick={125};
        net.runelite.api.Client client=(net.runelite.api.Client)java.lang.reflect.Proxy.newProxyInstance(net.runelite.api.Client.class.getClassLoader(),new Class[]{net.runelite.api.Client.class},
            (p,m,a)->m.getName().equals("getTickCount")?tick[0]:m.getName().equals("getVarpValue")?((int)a[0]==DoomLevelCounters.Source.DOM_LEVEL_8_COMPLETIONS.varp?total[0]:10):m.invoke(original,a));
        ImmediateReportDeliveryTest.set(h.plugin,"client",client);ImmediateReportDeliveryTest.invoke(h.plugin,"observeDoomCounters");assertEquals(0,h.queue.snapshot().length);
        total[0]=11;tick[0]=126;ImmediateReportDeliveryTest.invoke(h.plugin,"observeDoomCounters");
        net.runelite.api.events.ChatMessage message=new net.runelite.api.events.ChatMessage();message.setType(net.runelite.api.ChatMessageType.GAMEMESSAGE);message.setMessage("Delve level: 8 duration: 1:00");h.plugin.onChatMessage(message);
        ((EncounterCapture)ImmediateReportDeliveryTest.field("encounterCapture").get(h.plugin)).advance(140);
        assertEquals(1,h.queue.snapshot().length);JsonObject report=new Gson().fromJson(h.queue.snapshot()[0].payload,JsonObject.class);assertTrue(report.has("doomCounters"));assertEquals(8,report.getAsJsonObject("doomState").get("completedLevel").getAsInt());
        GameStateChanged state=new GameStateChanged();state.setGameState(GameState.HOPPING);h.plugin.onGameStateChanged(state);tick[0]=150;total[0]=50;ImmediateReportDeliveryTest.invoke(h.plugin,"observeDoomCounters");
        ((EncounterCapture)ImmediateReportDeliveryTest.field("encounterCapture").get(h.plugin)).advance(170);assertEquals(1,h.queue.snapshot().length);assertEquals(0,ImmediateReportDeliveryTest.field("collectionCaptureFailures").getLong(h.plugin));
    }
    private void policy(ImmediateReportDeliveryTest.Harness h, boolean counters) throws Exception
    {
        long now=System.currentTimeMillis();JsonObject p=new Gson().fromJson("{\"version\":2,\"expiresAt\":"+(now+60000)+",\"windows\":{},\"doomEvidenceVersion\":1}",JsonObject.class);
        if(counters)p.addProperty("doomCounterEvidenceVersion",1);
        ((CollectionPolicy)ImmediateReportDeliveryTest.field("collectionPolicy").get(h.plugin)).accept(p,now);
    }
    private void queue(ImmediateReportDeliveryTest.Harness h) throws Exception
    {
        EncounterCapture c=new EncounterCapture(snapshot -> {
            try { Method m=HuntmasterPlugin.class.getDeclaredMethod("queueEncounterReport",EncounterObservation.Snapshot.class);m.setAccessible(true);m.invoke(h.plugin,snapshot); }
            catch(Exception ex) { throw new AssertionError(ex); }
        });
        BossDetector boss=new BossDetector(BossDefinition.totalCounterCandidate("Doom of Mokhaiotl","doom"));
        c.doomCompletion(boss,"Example",null,100,System.currentTimeMillis(),DoomCompletionEvidence.parse(net.runelite.api.ChatMessageType.GAMEMESSAGE,"Delve level: 8 duration: 1:00"));
        c.doomCounter(boss,"Example",null,101,System.currentTimeMillis(),new DoomLevelCounters.Observation(DoomLevelCounters.Source.DOM_LEVEL_8_COMPLETIONS,5,6,0));c.advance(135);
    }
    @Test public void legacyCompatibleBotReceivesUnchangedDoomShape() throws Exception
    {
        ImmediateReportDeliveryTest.Harness h=new ImmediateReportDeliveryTest.Harness();policy(h,false);queue(h);
        assertEquals(1,h.calls.size());JsonObject report=new Gson().fromJson(h.queue.snapshot()[0].payload,JsonObject.class);
        assertTrue(report.has("doomState"));assertFalse(report.has("doomCounters"));assertEquals(6,report.getAsJsonObject("doomState").size());
    }
    @Test public void capabilityDowngradeHoldsImmutablePayloadWithoutBlockingOrdinaryReports() throws Exception
    {
        ImmediateReportDeliveryTest.Harness h=new ImmediateReportDeliveryTest.Harness();policy(h,true);ImmediateReportDeliveryTest.set(h.plugin,"healthReachable",false);queue(h);
        EncounterReportQueue.Entry entry=h.queue.snapshot()[0];String payload=entry.payload;assertTrue(new Gson().fromJson(payload,JsonObject.class).has("doomCounters"));
        policy(h,false);ImmediateReportDeliveryTest.set(h.plugin,"healthReachable",true);ImmediateReportDeliveryTest.invoke(h.plugin,"updateEncounterReports");assertEquals(0,h.calls.size());assertEquals(payload,entry.payload);
        h.finalizeReport();assertEquals(1,h.calls.size());assertEquals(payload,entry.payload);
        h.calls.get(0).respond(h.ack(h.queue.snapshot()[1].id));policy(h,true);entry.nextAttemptAt=0;ImmediateReportDeliveryTest.invoke(h.plugin,"updateEncounterReports");assertEquals(2,h.calls.size());
        okio.Buffer body=new okio.Buffer();h.calls.get(1).request.body().writeTo(body);assertEquals(payload,body.readUtf8());
    }
    @Test public void logoutReconnectWorldHopAndLoginClearPerLevelBaselines() throws Exception
    {
        for(GameState state:new GameState[]{GameState.LOGIN_SCREEN,GameState.CONNECTION_LOST,GameState.HOPPING,GameState.LOGGING_IN})
        {
            ImmediateReportDeliveryTest.Harness h=new ImmediateReportDeliveryTest.Harness();DoomLevelCounters sampler=(DoomLevelCounters)ImmediateReportDeliveryTest.field("doomLevelCounters").get(h.plugin);
            java.util.List<DoomLevelCounters.Observation> changes=new java.util.ArrayList<>();Map<Integer,Integer> values=new HashMap<>();sampler.sample(id->10,true,changes::add);
            GameStateChanged event=new GameStateChanged();event.setGameState(state);h.plugin.onGameStateChanged(event);
            values.put(DoomLevelCounters.Source.DOM_LEVEL_8_COMPLETIONS.varp,11);sampler.sample(id->values.getOrDefault(id,10),true,changes::add);assertTrue(changes.isEmpty());
        }
    }
}

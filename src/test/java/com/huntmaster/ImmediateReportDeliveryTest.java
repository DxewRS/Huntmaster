package com.huntmaster;

import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import okhttp3.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the real finalization -> queue -> asynchronous HTTP callback path, without a live server. */
public class ImmediateReportDeliveryTest
{
    static Field field(String name) throws Exception { Field f=HuntmasterPlugin.class.getDeclaredField(name); f.setAccessible(true); return f; }
    static void set(HuntmasterPlugin p,String name,Object value)throws Exception {field(name).set(p,value);}
    static void invoke(HuntmasterPlugin p,String name)throws Exception {Method m=HuntmasterPlugin.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(p);}
    static final class PendingCall implements Call
    {
        final Request request; Callback callback; boolean cancelled;
        PendingCall(Request r){request=r;}
        public Request request(){return request;}
        public Response execute(){throw new AssertionError("Must be asynchronous");}
        public void enqueue(Callback c){callback=c;}
        public void cancel(){cancelled=true;}
        public boolean isExecuted(){return callback!=null;}
        public boolean isCanceled(){return cancelled;}
        public PendingCall clone(){return new PendingCall(request);}
        public okio.Timeout timeout(){return new okio.Timeout();}
        void respond(String body)throws Exception {callback.onResponse(this,new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(MediaType.parse("application/json"),body)).build());}
    }
    static final class Harness
    {
        final HuntmasterPlugin plugin=new HuntmasterPlugin();
        final List<PendingCall> calls=new ArrayList<>();
        final EncounterReportQueue queue;
        final AssignmentDashboardState dashboard;
        GameState gameState=GameState.LOGGED_IN;
        final String assignment=UUID.randomUUID().toString();
        Harness()throws Exception
        {
            Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->m.getName().equals("getName")?"Example":null);
            Client client=(Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class[]{Client.class},(p,m,a)->m.getName().equals("getLocalPlayer")?player:m.getName().equals("getGameState")?gameState:m.getName().equals("getTickCount")?125:null);
            set(plugin,"client",client);set(plugin,"gson",new Gson());
            set(plugin,"clientThread",new ClientThread(){@Override public void invokeLater(Runnable r){r.run();}});
            set(plugin,"httpClient",new OkHttpClient(){@Override public Call newCall(Request r){PendingCall c=new PendingCall(r);calls.add(c);return c;}});
            set(plugin,"running",true);set(plugin,"reportsEnabled",true);set(plugin,"registrationConfirmed",true);set(plugin,"healthReachable",true);set(plugin,"assignmentSyncRequired",false);set(plugin,"assignmentRsn","Example");
            ((CollectionPolicy)field("collectionPolicy").get(plugin)).accept(new Gson().fromJson("{\"version\":2,\"expiresAt\":"+(System.currentTimeMillis()+60000)+",\"windows\":{}}",com.google.gson.JsonObject.class),System.currentTimeMillis());
            queue=(EncounterReportQueue)field("reportQueue").get(plugin);dashboard=(AssignmentDashboardState)field("dashboard").get(plugin);
            dashboard.reset("Example",System.currentTimeMillis()-10000);
        }
        void finalizeReport()throws Exception
        {
            EncounterCapture capture=new EncounterCapture(snapshot->{try{Method m=HuntmasterPlugin.class.getDeclaredMethod("queueEncounterReport",EncounterObservation.Snapshot.class);m.setAccessible(true);m.invoke(plugin,snapshot);}catch(Exception e){throw new AssertionError(e);}});
            BossDetector boss=new BossDetector(BossDefinition.betaCandidate("The Nightmare","nightmare","Your Nightmare kill count is:"));
            capture.primary(boss,"Example",assignment,100,System.currentTimeMillis()-1000,EncounterObservation.SignalKind.DEATH);
            capture.counter(boss,"Example",assignment,112,System.currentTimeMillis(),EncounterObservation.CounterSource.KC_MESSAGE,58,59);
            capture.loot(boss,"Example",assignment,112,System.currentTimeMillis());
            int before=calls.size();
            capture.advance(123); // Existing window is deliberately unchanged.
            assertEquals(before,calls.size());capture.advance(124);
            assertTrue(calls.size()==before || calls.size()==before+1);
        }
        String ack(String id){return "{\"success\":true,\"status\":\"stored\",\"reportId\":\""+id+"\",\"serverCredit\":{\"status\":\"progress_updated\"},\"assignmentState\":{\"success\":true,\"rsn\":\"Example\",\"status\":\"solo\",\"sequence\":100,\"assignment\":{\"id\":\""+assignment+"\",\"boss\":\"The Nightmare\",\"progress\":1,\"requiredKC\":10,\"rewardPoints\":20,\"classification\":\"solo\"}}}";}
    }
    @Test public void finalizedReportSendsWithoutTimerAndAckAppliesServerState()throws Exception
    {
        Harness h=new Harness();h.finalizeReport();assertEquals(1,h.calls.size());assertTrue(h.queue.snapshot()[0].inFlight);
        assertNull(h.dashboard.overlay(System.currentTimeMillis(),true));
        invoke(h.plugin,"updateEncounterReports");assertEquals(1,h.calls.size());
        String ack=h.ack(h.queue.snapshot()[0].id);h.calls.get(0).respond(ack);
        assertEquals(0,h.queue.snapshot().length);assertEquals("1/10 KC: The Nightmare",h.dashboard.overlay(System.currentTimeMillis(),true));
        h.calls.get(0).respond(ack);assertEquals(0,h.queue.snapshot().length);assertEquals("1/10 KC: The Nightmare",h.dashboard.overlay(System.currentTimeMillis(),true));
    }
    @Test public void oldBotCannotDiscardNewProtocolReports()throws Exception
    {
        Harness h=new Harness();((CollectionPolicy)field("collectionPolicy").get(h.plugin)).accept(null,0);
        h.finalizeReport();assertEquals(0,h.calls.size());assertEquals(1,h.queue.snapshot().length);
    }
    @Test public void reportsFinalizedDuringRequestWaitForWorkerInExistingOrder()throws Exception
    {
        Harness h=new Harness();h.finalizeReport();h.finalizeReport();h.finalizeReport();assertEquals(1,h.calls.size());
        String second=h.queue.snapshot()[1].id;
        h.calls.get(0).respond(h.ack(h.queue.snapshot()[0].id));assertEquals(1,h.calls.size());assertEquals(second,h.queue.snapshot()[0].id);assertFalse(h.queue.snapshot()[0].inFlight);
        invoke(h.plugin,"updateEncounterReports");assertEquals(2,h.calls.size());assertTrue(h.queue.snapshot()[0].inFlight);
        invoke(h.plugin,"updateEncounterReports");assertEquals(2,h.calls.size());
    }
    @Test public void droppedRequestRetainsIdentityAndRetriesAfterRecovery()throws Exception
    {
        Harness h=new Harness();h.finalizeReport();EncounterReportQueue.Entry entry=h.queue.snapshot()[0];String payload=entry.payload;
        // Outage already recorded: this harness deliberately has no player configuration store.
        ConnectionOutageState outage=(ConnectionOutageState)field("outage").get(h.plugin);outage.fail(System.currentTimeMillis());
        h.calls.get(0).callback.onFailure(h.calls.get(0),new IOException("disconnected"));
        assertEquals(payload,h.queue.snapshot()[0].payload);assertFalse(entry.inFlight);assertTrue(entry.nextAttemptAt>System.currentTimeMillis());
        invoke(h.plugin,"updateEncounterReports");assertEquals(1,h.calls.size());
        set(h.plugin,"healthReachable",true);set(h.plugin,"assignmentSyncRequired",false);outage.recover();entry.nextAttemptAt=0;
        invoke(h.plugin,"updateEncounterReports");assertEquals(2,h.calls.size());h.calls.get(1).respond(h.ack(entry.id));assertEquals(0,h.queue.snapshot().length);
    }
    @Test public void offlineFinalizationStaysQueuedAndStaleAccountCallbackIsIgnored()throws Exception
    {
        Harness h=new Harness();set(h.plugin,"healthReachable",false);h.finalizeReport();assertEquals(0,h.calls.size());assertEquals(1,h.queue.snapshot().length);
        set(h.plugin,"healthReachable",true);invoke(h.plugin,"updateEncounterReports");String ack=h.ack(h.queue.snapshot()[0].id);
        GameStateChanged event=new GameStateChanged();event.setGameState(GameState.LOGIN_SCREEN);h.plugin.onGameStateChanged(event);
        h.calls.get(0).respond(ack);assertEquals(1,h.queue.snapshot().length);assertFalse(h.queue.snapshot()[0].inFlight);assertNull(h.dashboard.overlay(System.currentTimeMillis(),true));
    }
    @Test public void shutdownIgnoresResponseAndSavedQueueRestoresWithSameIdentity()throws Exception
    {
        Harness h=new Harness();h.finalizeReport();EncounterReportQueue.Entry entry=h.queue.snapshot()[0];
        String saved=new Gson().toJson(h.queue.snapshot());
        set(h.plugin,"running",false);h.calls.get(0).respond(h.ack(entry.id));
        assertEquals(1,h.queue.snapshot().length);assertNull(h.dashboard.overlay(System.currentTimeMillis(),true));
        EncounterReportQueue restored=new EncounterReportQueue();
        for(EncounterReportQueue.Entry e:new Gson().fromJson(saved,EncounterReportQueue.Entry[].class))
        {
            assertFalse(e.inFlight);
            assertTrue(restored.add(e.id,e.payload,e.createdAt,System.currentTimeMillis(),e.creditCandidate));
        }
        assertEquals(entry.id,restored.next(System.currentTimeMillis()).id);
        assertEquals(entry.payload,restored.next(System.currentTimeMillis()).payload);
    }

    @Test public void loggedOutHealthAndReportDeliveryDoNotSend()throws Exception
    {
        Harness h=new Harness();h.gameState=GameState.LOGIN_SCREEN;
        invoke(h.plugin,"testHuntmasterApiConnection");h.finalizeReport();
        assertEquals(0,h.calls.size());assertEquals(1,h.queue.snapshot().length);
        h.gameState=GameState.LOGGED_IN;invoke(h.plugin,"updateEncounterReports");assertEquals(1,h.calls.size());
    }
    @Test public void shutdownFinalizationQueuesWithoutStartingHttp()throws Exception
    {
        Harness h=new Harness();set(h.plugin,"running",false);h.finalizeReport();
        assertEquals(0,h.calls.size());assertEquals(1,h.queue.snapshot().length);assertFalse(h.queue.snapshot()[0].inFlight);
    }
}

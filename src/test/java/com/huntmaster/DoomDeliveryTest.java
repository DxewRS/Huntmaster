package com.huntmaster;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real plugin event routing and queue transport, with a fake client/HTTP transport. */
public class DoomDeliveryTest
{
    private void completion(ImmediateReportDeliveryTest.Harness h) throws Exception
    {
        ImmediateReportDeliveryTest.set(h.plugin, "connectionInitialized", true);
        ImmediateReportDeliveryTest.set(h.plugin, "reliabilityRsn", "Example");
        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage("Delve level: 8+ (9) duration: 1:02.40. Personal best: 1:01.20");
        h.plugin.onChatMessage(event);
        ((EncounterCapture) ImmediateReportDeliveryTest.field("encounterCapture").get(h.plugin)).advance(135);
    }
    private void policy(ImmediateReportDeliveryTest.Harness h, String version)
        throws Exception
    {
        long now = System.currentTimeMillis();
        ((CollectionPolicy) ImmediateReportDeliveryTest.field("collectionPolicy").get(h.plugin)).accept(
            new Gson().fromJson("{\"version\":2,\"expiresAt\":" + (now + 60000)
                + ",\"windows\":{},\"doomEvidenceVersion\":" + version + "}", JsonObject.class), now);
    }

    @Test public void oldBotRetainsDoomWithoutBlockingOtherBosses() throws Exception
    {
        ImmediateReportDeliveryTest.Harness h = new ImmediateReportDeliveryTest.Harness();
        completion(h);
        assertEquals(1, h.queue.snapshot().length); assertEquals(0, h.calls.size());
        JsonObject payload = new Gson().fromJson(h.queue.snapshot()[0].payload, JsonObject.class);
        assertTrue(payload.get("assignmentId").isJsonNull());
        assertEquals(9, payload.getAsJsonObject("doomState").get("completedLevel").getAsInt());
        h.finalizeReport(); assertEquals(1, h.calls.size());
        okio.Buffer body = new okio.Buffer(); h.calls.get(0).request.body().writeTo(body);
        assertFalse(new Gson().fromJson(body.readUtf8(), JsonObject.class).has("doomState"));
        assertFalse(h.queue.snapshot()[0].inFlight);
    }

    @Test public void compatibleServerReceivesOriginalPayloadAndRetryIdentity() throws Exception
    {
        ImmediateReportDeliveryTest.Harness h = new ImmediateReportDeliveryTest.Harness();
        completion(h);
        EncounterReportQueue.Entry entry = h.queue.snapshot()[0];
        String id = entry.id, payload = entry.payload;
        policy(h, "1"); ImmediateReportDeliveryTest.invoke(h.plugin, "updateEncounterReports");
        assertEquals(1, h.calls.size());
        okio.Buffer buffer = new okio.Buffer(); h.calls.get(0).request.body().writeTo(buffer);
        assertEquals(payload, buffer.readUtf8());
        ConnectionOutageState outage = (ConnectionOutageState) ImmediateReportDeliveryTest.field("outage").get(h.plugin);
        outage.fail(System.currentTimeMillis());
        ImmediateReportDeliveryTest.set(h.plugin, "healthReachable", false);
        h.calls.get(0).callback.onFailure(h.calls.get(0), new IOException("offline"));
        assertEquals(id, h.queue.snapshot()[0].id); assertEquals(payload, entry.payload);
        assertTrue(entry.nextAttemptAt > System.currentTimeMillis());
        ImmediateReportDeliveryTest.set(h.plugin, "healthReachable", true);
        ImmediateReportDeliveryTest.set(h.plugin, "assignmentSyncRequired", false);
        outage.recover(); entry.nextAttemptAt = 0;
        ImmediateReportDeliveryTest.invoke(h.plugin, "updateEncounterReports");
        assertEquals(2, h.calls.size());
        h.calls.get(1).respond("{\"success\":true,\"status\":\"stored\",\"reportId\":\"" + id + "\"}");
        assertEquals(0, h.queue.snapshot().length);
    }

    @Test public void capabilityIsOptInVersionedAndExpires() throws Exception
    {
        ImmediateReportDeliveryTest.Harness h = new ImmediateReportDeliveryTest.Harness();
        CollectionPolicy p = (CollectionPolicy) ImmediateReportDeliveryTest.field("collectionPolicy").get(h.plugin);
        assertFalse(p.supportsDoomEvidence(System.currentTimeMillis()));
        policy(h, "2"); assertFalse(p.supportsDoomEvidence(System.currentTimeMillis()));
        policy(h, "1"); assertTrue(p.supportsDoomEvidence(System.currentTimeMillis()));
        assertFalse(p.supportsDoomEvidence(System.currentTimeMillis() + 61000));
        p.accept(null, 0); assertFalse(p.supportsDoomEvidence(System.currentTimeMillis()));
    }

    @Test public void realTotalHandlerBaselinesAndDoesNotSynthesizeDoomCompletion() throws Exception
    {
        ImmediateReportDeliveryTest.Harness h = new ImmediateReportDeliveryTest.Harness();
        Client original = (Client) ImmediateReportDeliveryTest.field("client").get(h.plugin);
        int[] total = {100};
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class[]{Client.class},
            (p,m,a) -> m.getName().equals("getVarpValue") ? total[0] : m.invoke(original,a));
        ImmediateReportDeliveryTest.set(h.plugin, "client", client);
        BossDetector d = new BossDetector(BossDefinition.totalCounterCandidate(DoomCompletionEvidence.BOSS, "doom"));
        Method tick = HuntmasterPlugin.class.getDeclaredMethod("handleTotalGameTick", BossDetector.class); tick.setAccessible(true);
        tick.invoke(h.plugin,d); assertEquals(Integer.valueOf(100),d.getLastKc());
        EncounterCapture capture = (EncounterCapture) ImmediateReportDeliveryTest.field("encounterCapture").get(h.plugin);
        capture.advance(135); assertEquals(0,h.queue.snapshot().length);
        // Fresh capture simulates a later tick without a backwards clock in the fake client.
        capture.clear(); total[0] = 101; tick.invoke(h.plugin,d); capture.advance(135);
        JsonObject report = new Gson().fromJson(h.queue.snapshot()[0].payload, JsonObject.class);
        assertFalse(report.has("doomState")); assertEquals(1, report.getAsJsonArray("signals").size());
        assertEquals("counter",report.getAsJsonArray("signals").get(0).getAsJsonObject().get("kind").getAsString());
    }

    @Test public void reconnectClearsDoomBaselineAndInterruptsPendingMessage() throws Exception
    {
        ImmediateReportDeliveryTest.Harness h = new ImmediateReportDeliveryTest.Harness();
        ImmediateReportDeliveryTest.set(h.plugin, "connectionInitialized", true);
        ImmediateReportDeliveryTest.set(h.plugin, "reliabilityRsn", "Example");
        ChatMessage message = new ChatMessage(); message.setType(ChatMessageType.GAMEMESSAGE);
        message.setMessage("Delve level: 8 duration: 1:02.40"); h.plugin.onChatMessage(message);
        net.runelite.api.events.GameStateChanged state = new net.runelite.api.events.GameStateChanged();
        state.setGameState(net.runelite.api.GameState.CONNECTION_LOST); h.plugin.onGameStateChanged(state);
        assertEquals(1, h.queue.snapshot().length);
        JsonObject report = new Gson().fromJson(h.queue.snapshot()[0].payload, JsonObject.class);
        assertEquals("interrupted", report.get("captureStatus").getAsString());
        assertEquals(8, report.getAsJsonObject("doomState").get("completedLevel").getAsInt());
        Method detectors = HuntmasterPlugin.class.getDeclaredMethod("observationDetectors"); detectors.setAccessible(true);
        for (Object value : (java.util.Collection<?>) detectors.invoke(h.plugin))
        {
            BossDetector d = (BossDetector) value;
            if (DoomCompletionEvidence.BOSS.equals(d.getName())) assertNull(d.getLastKc());
        }
    }
}

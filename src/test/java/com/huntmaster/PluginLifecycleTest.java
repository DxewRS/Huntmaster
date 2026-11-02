package com.huntmaster;

import static com.huntmaster.ImmediateReportDeliveryTest.*;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.UUID;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import static org.junit.Assert.*;

/** Startup, shutdown, logout and account changes must keep every saved report, and bot replies must be read as sent. */
public class PluginLifecycleTest
{
    private static final String GROUP = "huntmaster", QUEUE = "pendingEncounterReports", BACKUP = "damagedEncounterReports";

    private Harness harness() throws Exception
    {
        Harness h = new Harness();
        set(h.plugin, "configManager", inertConfig("lifecycle.properties"));
        return h;
    }
    private ConfigManager config(Harness h) throws Exception { return (ConfigManager) field("configManager").get(h.plugin); }
    private Harness restart(Harness h) throws Exception
    {
        Harness next = new Harness();
        set(next.plugin, "configManager", config(h));
        invoke(next.plugin, "loadEncounterReports");
        return next;
    }
    private void logout(Harness h)
    {
        h.gameState = GameState.LOGIN_SCREEN;
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGIN_SCREEN);
        h.plugin.onGameStateChanged(event);
    }
    private boolean canTrack(Harness h) throws Exception
    {
        Method method = HuntmasterPlugin.class.getDeclaredMethod("canTrackNewKills");
        method.setAccessible(true);
        return (boolean) method.invoke(h.plugin);
    }
    private void chat(Harness h, String message)
    {
        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage(message);
        h.plugin.onChatMessage(event);
    }

    @Test public void aQueueSavedByVersion13RestoresAndDelivers() throws Exception
    {
        Harness h = harness();
        set(h.plugin, "healthReachable", false);
        h.finalizeReport();
        EncounterReportQueue.Entry entry = h.queue.snapshot()[0];
        JsonObject saved = new JsonObject();
        saved.addProperty("id", entry.id);
        saved.addProperty("payload", entry.payload);
        saved.addProperty("createdAt", entry.createdAt);
        saved.addProperty("nextAttemptAt", 0);
        saved.addProperty("creditCandidate", true);
        config(h).setConfiguration(GROUP, QUEUE, "[" + saved + "]");

        Harness next = restart(h);
        EncounterReportQueue.Entry restored = next.queue.snapshot()[0];
        assertEquals(entry.id, restored.id);
        assertTrue(restored.creditCandidate);
        assertEquals(0, restored.heldAt);
        assertEquals(0, restored.rejections);
        assertEquals(0, restored.temporaryFailures);
        assertNull(config(h).getConfiguration(GROUP, BACKUP));
        invoke(next.plugin, "updateEncounterReports");
        assertEquals(1, next.calls.size());
        next.calls.get(0).respond(next.ack(entry.id));
        assertEquals(0, next.queue.snapshot().length);
    }

    @Test public void shutdownMidSendKeepsTheReportForTheNextStart() throws Exception
    {
        Harness h = harness();
        h.finalizeReport();
        EncounterReportQueue.Entry entry = h.queue.snapshot()[0];
        assertTrue(entry.inFlight);
        h.plugin.shutDown();
        assertTrue(h.calls.get(0).isCanceled());
        h.calls.get(0).respond(h.ack(entry.id));
        assertEquals(1, h.queue.snapshot().length);

        Harness next = restart(h);
        assertEquals(entry.id, next.queue.snapshot()[0].id);
        assertFalse(next.queue.snapshot()[0].inFlight);
        invoke(next.plugin, "updateEncounterReports");
        assertEquals(1, next.calls.size());
    }

    @Test public void shutdownSavesWhatChangedSinceTheLastSave() throws Exception
    {
        Harness h = harness();
        String id = UUID.randomUUID().toString();
        assertTrue(h.queue.add(id, "{}", 1000, 1000, true));
        h.plugin.shutDown();
        assertTrue(config(h).getConfiguration(GROUP, QUEUE).contains(id));
    }

    @Test public void shutdownBeforeTheDeferredRestoreLeavesTheSavedQueueAlone() throws Exception
    {
        Harness h = harness();
        config(h).setConfiguration(GROUP, QUEUE, "[saved]");
        set(h.plugin, "reportRestorePending", true);
        assertTrue(h.queue.add(UUID.randomUUID().toString(), "{}", 1000, 1000, true));
        h.plugin.shutDown();
        assertEquals("[saved]", config(h).getConfiguration(GROUP, QUEUE));
    }

    @Test public void pluginReloadKeepsRetryState() throws Exception
    {
        Harness h = harness();
        h.finalizeReport();
        h.calls.get(0).respond(503, "{\"success\":false,\"error\":\"report_storage_unavailable\"}");
        long retryAt = h.queue.snapshot()[0].nextAttemptAt;
        h.plugin.shutDown();
        EncounterReportQueue.Entry restored = restart(h).queue.snapshot()[0];
        assertEquals(1, restored.temporaryFailures);
        assertEquals(retryAt, restored.nextAttemptAt);
    }

    @Test public void logoutMidCaptureFinishesTheReportAsInterrupted() throws Exception
    {
        Harness h = harness();
        EncounterCapture capture = (EncounterCapture) field("encounterCapture").get(h.plugin);
        BossDetector boss = new BossDetector(BossDefinition.betaCandidate("The Nightmare", "nightmare", "Your Nightmare kill count is:"));
        capture.primary(boss, "Example", h.assignment, 100, System.currentTimeMillis() - 1000, EncounterObservation.SignalKind.DEATH);
        assertEquals(0, h.queue.snapshot().length);
        logout(h);
        assertEquals(1, h.queue.snapshot().length);
        assertEquals(0, h.calls.size());
    }

    @Test public void aReportWaitsForItsOwnAccount() throws Exception
    {
        Harness h = harness();
        set(h.plugin, "healthReachable", false);
        h.finalizeReport();
        EncounterReportQueue.Entry entry = h.queue.snapshot()[0];
        set(h.plugin, "healthReachable", true);
        h.playerName = "Other";
        set(h.plugin, "assignmentRsn", "Other");
        invoke(h.plugin, "updateEncounterReports");
        assertEquals(0, h.calls.size());
        assertFalse(entry.inFlight);
        h.playerName = "Example";
        set(h.plugin, "assignmentRsn", "Example");
        invoke(h.plugin, "updateEncounterReports");
        assertEquals(1, h.calls.size());
    }

    @Test public void newKillsNeedTheAssignmentToMatchThePlayer() throws Exception
    {
        Harness h = harness();
        set(h.plugin, "connectionInitialized", true);
        set(h.plugin, "reliabilityRsn", "Example");
        assertTrue(canTrack(h));
        set(h.plugin, "assignmentRsn", "Other");
        assertFalse(canTrack(h));
    }

    @Test public void anAckForAnotherReportDoesNotDeliverThisOne() throws Exception
    {
        Harness h = harness();
        h.finalizeReport();
        EncounterReportQueue.Entry entry = h.queue.snapshot()[0];
        h.calls.get(0).respond(h.ack(UUID.randomUUID().toString()));
        assertSame(entry, h.queue.snapshot()[0]);
        assertNull(h.dashboard.overlay(System.currentTimeMillis(), true));
    }

    @Test public void thePluginsOwnNoticesAreNotKillEvidence() throws Exception
    {
        Harness h = harness();
        set(h.plugin, "connectionInitialized", true);
        set(h.plugin, "reliabilityRsn", "Example");
        EncounterCapture capture = (EncounterCapture) field("encounterCapture").get(h.plugin);
        chat(h, "Huntmaster RuneLite Plugin Your Nightmare kill count is: 5.");
        capture.interrupt(125, EncounterObservation.Reason.LOGOUT);
        assertEquals(0, h.queue.snapshot().length);
        chat(h, "Your Nightmare kill count is: 5.");
        capture.interrupt(125, EncounterObservation.Reason.LOGOUT);
        assertEquals(1, h.queue.snapshot().length);
    }

    // Reply bodies below are the bot's, from runeliteApi.js and its encounter report results.
    private EncounterReportQueue.Entry reply(Harness h, int status, String body) throws Exception
    {
        h.finalizeReport();
        h.calls.get(h.calls.size() - 1).respond(status, body.replace("ID", h.queue.snapshot()[0].id));
        return h.queue.snapshot().length == 0 ? null : h.queue.snapshot()[0];
    }

    @Test public void botAcceptanceRepliesRemoveTheReport() throws Exception
    {
        assertNull(reply(harness(), 200, "{\"success\":true,\"status\":\"stored\",\"reportId\":\"ID\"}"));
        assertNull(reply(harness(), 200, "{\"success\":true,\"status\":\"duplicate\",\"reportId\":\"ID\"}"));
        assertNull(reply(harness(), 400, "{\"success\":false,\"status\":\"report_expired\",\"reportId\":\"ID\"}"));
    }

    @Test public void botRefusalRepliesKeepTheReportAndCountAgainstIt() throws Exception
    {
        for (String error : new String[]{"invalid_encounter_report", "unknown_report_boss", "report_body_too_large"})
        {
            EncounterReportQueue.Entry entry = reply(harness(), 400, "{\"success\":false,\"error\":\"" + error + "\"}");
            assertNotEquals(0, entry.heldAt);
            assertEquals(1, entry.rejections);
        }
        EncounterReportQueue.Entry conflict = reply(harness(), 409, "{\"success\":false,\"status\":\"report_conflict\",\"reportId\":\"ID\"}");
        assertNotEquals(0, conflict.heldAt);
        EncounterReportQueue.Entry unregistered = reply(harness(), 404, "{\"success\":false,\"status\":\"rsn_not_registered\"}");
        assertEquals(0, unregistered.heldAt);
        assertEquals(1, unregistered.rejections);
    }

    @Test public void botOutageRepliesKeepTheReportWithoutCountingAgainstIt() throws Exception
    {
        for (String body : new String[]{"{\"success\":false,\"status\":\"report_capacity\"}", "{\"success\":false,\"error\":\"report_storage_unavailable\"}"})
        {
            EncounterReportQueue.Entry entry = reply(harness(), 503, body);
            assertEquals(0, entry.rejections);
            assertEquals(1, entry.temporaryFailures);
        }
        assertEquals(0, reply(harness(), 500, "{\"success\":false}").rejections);
    }

    @Test public void forbiddenDuringABotRestartKeepsTheReportAndBacksOff() throws Exception
    {
        Harness h = harness();
        EncounterReportQueue.Entry entry = reply(h, 403, "{\"success\":false,\"error\":\"credential_account_mismatch\"}");
        assertFalse(entry.inFlight);
        assertEquals(0, entry.heldAt);
        assertEquals(0, entry.rejections);
        assertEquals(0, entry.temporaryFailures);
        assertTrue(field("membershipDenied").getBoolean(h.plugin));
        assertTrue(field("nextHealthCheckAt").getLong(h.plugin) > System.currentTimeMillis());
    }

    @Test public void aSavedKillTheBotKeepsFailingBacksOffThenStopsCountingAsAnOutage() throws Exception
    {
        Harness h = harness();
        Class<?> type = Class.forName("com.huntmaster.HuntmasterPlugin$PendingKcEvent");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(String.class, String.class, String.class, long.class, String.class);
        constructor.setAccessible(true);
        Object event = constructor.newInstance("event", "Example", "Zulrah", System.currentTimeMillis(), h.assignment);
        @SuppressWarnings("unchecked") java.util.Map<String, Object> pending = (java.util.Map<String, Object>) field("pendingKcEvents").get(h.plugin);
        pending.put("event", event);
        java.lang.reflect.Field attempts = type.getDeclaredField("attempts"), nextAttemptAt = type.getDeclaredField("nextAttemptAt");
        attempts.setAccessible(true);
        nextAttemptAt.setAccessible(true);
        Method failed = HuntmasterPlugin.class.getDeclaredMethod("pendingKcFailed", type, String.class), quiet = HuntmasterPlugin.class.getDeclaredMethod("pendingKcQuiet", type);
        failed.setAccessible(true);
        quiet.setAccessible(true);

        attempts.setInt(event, 19);
        failed.invoke(h.plugin, event, "HTTP 500");
        assertNotEquals(0, ((ConnectionOutageState) field("outage").get(h.plugin)).getStartedAt());
        assertFalse((boolean) quiet.invoke(null, event));
        long delay = nextAttemptAt.getLong(event) - System.currentTimeMillis();
        assertTrue(delay > 60_000L && delay <= 300_000L);
        attempts.setInt(event, 20);
        assertTrue((boolean) quiet.invoke(null, event));
        field("healthReachable").setBoolean(h.plugin, true);
        field("assignmentSyncRequired").setBoolean(h.plugin, false);
        failed.invoke(h.plugin, event, "HTTP 500");
        assertEquals(0, ((ConnectionOutageState) field("outage").get(h.plugin)).getStartedAt());
        assertSame(event, pending.get("event"));
    }

    @Test public void retryAfterHoldsBackTheNextHealthCheck() throws Exception
    {
        Harness h = harness();
        h.finalizeReport();
        PendingCall call = h.calls.get(0);
        call.callback.onResponse(call, new Response.Builder().request(call.request).protocol(Protocol.HTTP_1_1).code(503).message("OK")
            .header("Retry-After", "30").body(ResponseBody.create(MediaType.parse("application/json"), "{}")).build());
        long until = field("retryAfterUntil").getLong(h.plugin);
        assertTrue(until > System.currentTimeMillis() + 25_000L && until <= System.currentTimeMillis() + 30_000L);

        set(h.plugin, "nextHealthCheckAt", 0L);
        int sent = h.calls.size();
        healthCheck(h);
        assertEquals(sent, h.calls.size());
        set(h.plugin, "retryAfterUntil", 0L);
        healthCheck(h);
        assertTrue(h.calls.get(sent).request.url().toString().endsWith("/health"));
    }

    @Test public void failedHealthChecksBackOff() throws Exception
    {
        Harness h = harness();
        long delay = 0;
        for (int i = 0; i < 4; i++)
        {
            set(h.plugin, "nextHealthCheckAt", 0L);
            healthCheck(h);
            PendingCall call = h.calls.get(h.calls.size() - 1);
            call.callback.onResponse(call, new Response.Builder().request(call.request).protocol(Protocol.HTTP_1_1).code(503).message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"), "{}")).build());
            delay = field("nextHealthCheckAt").getLong(h.plugin) - System.currentTimeMillis();
        }
        assertTrue(delay > 10_000L && delay <= 40_000L);
    }

    @Test public void repeatedMembershipRefusalsBackOff() throws Exception
    {
        Harness h = harness();
        Method reject = HuntmasterPlugin.class.getDeclaredMethod("rejectLink");
        reject.setAccessible(true);
        for (int i = 0; i < 4; i++) reject.invoke(h.plugin);
        long delay = field("nextHealthCheckAt").getLong(h.plugin) - System.currentTimeMillis();
        assertTrue(delay > 10_000L && delay <= 40_000L);
    }

    private void healthCheck(Harness h) throws Exception
    {
        Method check = HuntmasterPlugin.class.getDeclaredMethod("testHuntmasterApiConnection");
        check.setAccessible(true);
        check.invoke(h.plugin);
    }
}

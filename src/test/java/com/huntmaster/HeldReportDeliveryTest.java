package com.huntmaster;

import static com.huntmaster.ImmediateReportDeliveryTest.*;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.UUID;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;

/** A report the bot will not take must neither be lost nor stall tracking; saved reports must survive startup. */
public class HeldReportDeliveryTest
{
    private static final String GROUP = "huntmaster", QUEUE = "pendingEncounterReports", BACKUP = "damagedEncounterReports";

    private Harness harness() throws Exception
    {
        Harness h = new Harness();
        set(h.plugin, "configManager", inertConfig("held.properties"));
        return h;
    }
    private ConfigManager config(Harness h) throws Exception { return (ConfigManager) field("configManager").get(h.plugin); }
    private ConnectionOutageState outage(Harness h) throws Exception { return (ConnectionOutageState) field("outage").get(h.plugin); }
    private void reconnect(Harness h, EncounterReportQueue.Entry entry) throws Exception
    {
        set(h.plugin, "healthReachable", true);
        set(h.plugin, "assignmentSyncRequired", false);
        entry.nextAttemptAt = 0;
    }
    private boolean pendingCredit(Harness h) throws Exception
    {
        java.lang.reflect.Method method = HuntmasterPlugin.class.getDeclaredMethod("hasPendingBetaCredit");
        method.setAccessible(true);
        return (boolean) method.invoke(h.plugin);
    }

    @Test public void rejectedCreditIsHeldAndLaterReportsStillDeliver() throws Exception
    {
        Harness h = harness();
        h.finalizeReport();
        h.finalizeReport();
        EncounterReportQueue.Entry bad = h.queue.snapshot()[0], next = h.queue.snapshot()[1];
        assertTrue(bad.creditCandidate);
        for (int status : new int[]{400, 409, 413})
        {
            bad.heldAt = 0;
            bad.inFlight = false;
            h.calls.get(0).respond(status, "{\"error\":\"invalid_encounter_report\"}");
            assertNotEquals(0, bad.heldAt);
            assertEquals(2, h.queue.snapshot().length);
            assertEquals(0, outage(h).getStartedAt());
            assertTrue(bad.nextAttemptAt > System.currentTimeMillis() + 60_000L);
        }
        invoke(h.plugin, "updateEncounterReports");
        assertEquals(2, h.calls.size());
        assertTrue(next.inFlight);
    }

    @Test public void repeatedBotErrorsHoldTheReportAndEndTheOutage() throws Exception
    {
        Harness h = harness();
        h.finalizeReport();
        EncounterReportQueue.Entry entry = h.queue.snapshot()[0];
        h.calls.get(0).respond(422, "{}");
        assertEquals(0, entry.heldAt);
        assertTrue(outage(h).getStartedAt() != 0);
        reconnect(h, entry);
        invoke(h.plugin, "updateEncounterReports");
        h.calls.get(1).respond(404, "{\"status\":\"rsn_not_registered\"}");
        assertEquals(0, entry.heldAt);
        reconnect(h, entry);
        invoke(h.plugin, "updateEncounterReports");
        assertTrue(pendingCredit(h));
        h.calls.get(2).respond("not json");
        assertNotEquals(0, entry.heldAt);
        assertFalse(pendingCredit(h));
        assertEquals(0, outage(h).getStartedAt());
        assertEquals(1, h.queue.snapshot().length);
    }

    @Test public void outagesNeverHoldCredit() throws Exception
    {
        Harness h = harness();
        h.finalizeReport();
        EncounterReportQueue.Entry entry = h.queue.snapshot()[0];
        int call = 0;
        for (int status : new int[]{408, 429, 500, 502, 503, 504, 520, 530})
        {
            h.calls.get(call++).respond(status, "{}");
            assertEquals(0, entry.rejections);
            reconnect(h, entry);
            invoke(h.plugin, "updateEncounterReports");
        }
        h.calls.get(call).callback.onFailure(h.calls.get(call), new IOException("disconnected"));
        assertEquals(0, entry.heldAt);
        assertEquals(1, h.queue.snapshot().length);
    }

    @Test public void oneReportFailingAloneStopsBlockingAndFreshKillsStillDeliver() throws Exception
    {
        Harness h = harness();
        h.finalizeReport();
        EncounterReportQueue.Entry poison = h.queue.snapshot()[0];
        for (int call = 0; call < 2; call++)
        {
            h.calls.get(call).respond(503, "{}");
            assertTrue(outage(h).getStartedAt() != 0);
            assertTrue(pendingCredit(h));
            reconnect(h, poison);
            invoke(h.plugin, "updateEncounterReports");
        }
        h.calls.get(2).respond(503, "{}");
        assertEquals(0, poison.heldAt);
        assertFalse(pendingCredit(h));
        assertTrue(poison.nextAttemptAt > System.currentTimeMillis() + 60_000L);
        invoke(h.plugin, "recoverConnectionIfReady");
        assertEquals(0, outage(h).getStartedAt());
        h.finalizeReport();
        EncounterReportQueue.Entry fresh = h.queue.snapshot()[1];
        assertTrue(fresh.inFlight);
        h.calls.get(3).respond(h.ack(fresh.id));
        assertEquals(1, h.queue.snapshot().length);
        assertSame(poison, h.queue.snapshot()[0]);
    }

    @Test public void manyReportsFailingTogetherStillBlockAsAnOutage() throws Exception
    {
        Harness h = harness();
        set(h.plugin, "healthReachable", false);
        for (int i = 0; i < EncounterReportQueue.MAX_QUIET_REPORTS; i++) h.finalizeReport();
        for (EncounterReportQueue.Entry entry : h.queue.snapshot()) entry.temporaryFailures = EncounterReportQueue.QUIET_AFTER_FAILURES;
        assertFalse(pendingCredit(h));
        h.finalizeReport();
        h.queue.snapshot()[EncounterReportQueue.MAX_QUIET_REPORTS].temporaryFailures = EncounterReportQueue.QUIET_AFTER_FAILURES;
        assertTrue(pendingCredit(h));
    }

    @Test public void reportsTheClientCannotSendYetDoNotExtendAnOutage() throws Exception
    {
        Harness h = harness();
        set(h.plugin, "healthReachable", false);
        h.finalizeReport();
        assertTrue(pendingCredit(h));
        ((CollectionPolicy) field("collectionPolicy").get(h.plugin)).accept(null, 0);
        assertFalse(pendingCredit(h));
    }

    @Test public void lastingDamageKeepsBackupsAndOldestCopyRotatesOut() throws Exception
    {
        Harness h = harness();
        for (String damaged : new String[]{"[{a", "[{b", "[{c", "[{d"})
        {
            config(h).setConfiguration(GROUP, QUEUE, damaged);
            invoke(h.plugin, "loadEncounterReports");
        }
        assertEquals("[{d", config(h).getConfiguration(GROUP, BACKUP));
        assertEquals("[{c", config(h).getConfiguration(GROUP, BACKUP + 2));
        assertEquals("[{b", config(h).getConfiguration(GROUP, BACKUP + 3));
    }

    @Test public void droppedReportsAreBackedUpBeforeTheQueueForgetsThemAndPlayerIsTold() throws Exception
    {
        Harness h = harness();
        h.finalizeReport();
        EncounterReportQueue.Entry entry = h.queue.snapshot()[0];
        h.calls.get(0).respond(400, "{}");
        entry.heldAt = System.currentTimeMillis() - EncounterReportQueue.HELD_RETENTION_MS;
        entry.rejections = EncounterReportQueue.HELD_EXPIRY_ATTEMPTS;
        h.queue.prune(System.currentTimeMillis());
        assertEquals(0, h.queue.snapshot().length);
        invoke(h.plugin, "saveEncounterReports");
        assertEquals(0, h.queue.dropped().length);
        assertTrue(config(h).getConfiguration(GROUP, BACKUP).contains(entry.id));
        assertFalse(config(h).getConfiguration(GROUP, QUEUE).contains(entry.id));
        invoke(h.plugin, "showHeldReportNotice");
        assertTrue(h.chat.stream().anyMatch(line -> line.contains("stopped retrying 1 old")));
    }

    @Test public void savingBeforeTheRestoreCannotEraseTheSavedQueue() throws Exception
    {
        Harness h = harness();
        config(h).setConfiguration(GROUP, QUEUE, "[saved]");
        set(h.plugin, "reportRestorePending", true);
        h.finalizeReport();
        invoke(h.plugin, "saveEncounterReports");
        assertEquals("[saved]", config(h).getConfiguration(GROUP, QUEUE));
    }

    @Test public void heldReportStaysSlowDuringOutagesAndClearsWhenAccepted() throws Exception
    {
        Harness h = harness();
        h.finalizeReport();
        EncounterReportQueue.Entry entry = h.queue.snapshot()[0];
        h.calls.get(0).respond(400, "{}");
        entry.nextAttemptAt = 0;
        invoke(h.plugin, "updateEncounterReports");
        h.calls.get(1).respond(503, "{}");
        assertEquals(0, outage(h).getStartedAt());
        assertTrue(entry.nextAttemptAt > System.currentTimeMillis() + 60_000L);
        entry.nextAttemptAt = 0;
        invoke(h.plugin, "updateEncounterReports");
        h.calls.get(2).respond(h.ack(entry.id));
        assertEquals(0, h.queue.snapshot().length);
    }

    @Test public void expiredCreditIsDroppedAndDiagnosticsKeepTheirRules() throws Exception
    {
        Harness h = harness();
        h.finalizeReport();
        h.calls.get(0).respond(400, "{\"status\":\"report_expired\"}");
        assertEquals(0, h.queue.snapshot().length);
        h.finalizeReport();
        h.queue.snapshot()[0].creditCandidate = false;
        h.calls.get(1).respond(400, "{\"error\":\"invalid_encounter_report\"}");
        assertEquals(0, h.queue.snapshot().length);
        assertEquals(0, outage(h).getStartedAt());
    }

    @Test public void unroutableCreditIsHeldAndNoticeShowsOnce() throws Exception
    {
        Harness h = harness();
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        assertTrue(h.queue.add(id, "{\"rsn\":\"Example\",\"trackingMode\":{}}", now - 1000, now, true));
        invoke(h.plugin, "updateEncounterReports");
        assertEquals(0, h.calls.size());
        assertEquals(1, h.queue.snapshot().length);
        assertNotEquals(0, h.queue.snapshot()[0].heldAt);
        assertEquals(1, h.queue.held("example"));
        assertEquals(0, h.queue.held("Other"));
        invoke(h.plugin, "showHeldReportNotice");
        invoke(h.plugin, "showHeldReportNotice");
        assertEquals(1, h.chat.size());
        assertTrue(h.chat.get(0).contains("kept and retried"));
        h.queue.acknowledge(id);
        invoke(h.plugin, "showHeldReportNotice");
        h.queue.add(id, "{\"rsn\":\"Example\",\"trackingMode\":{}}", now - 1000, now, true);
        invoke(h.plugin, "updateEncounterReports");
        invoke(h.plugin, "showHeldReportNotice");
        assertEquals(2, h.chat.size());
    }

    @Test public void restartKeepsHeldStateAndFutureDatedCredit() throws Exception
    {
        Harness h = harness();
        h.finalizeReport();
        EncounterReportQueue.Entry entry = h.queue.snapshot()[0];
        h.calls.get(0).respond(400, "{}");
        entry.createdAt = System.currentTimeMillis() + 3_600_000L;
        config(h).setConfiguration(GROUP, QUEUE, new Gson().toJson(h.queue.snapshot()));
        invoke(h.plugin, "loadEncounterReports");
        EncounterReportQueue.Entry restored = h.queue.snapshot()[0];
        assertEquals(entry.id, restored.id);
        assertEquals(entry.heldAt, restored.heldAt);
        assertEquals(entry.rejections, restored.rejections);
        assertNull(config(h).getConfiguration(GROUP, BACKUP));
        assertTrue(config(h).getConfiguration(GROUP, QUEUE).contains(entry.id));
    }

    @Test public void unreadableSavedQueueIsBackedUpBeforeRewrite() throws Exception
    {
        Harness h = harness();
        config(h).setConfiguration(GROUP, QUEUE, "[{truncated");
        invoke(h.plugin, "loadEncounterReports");
        assertEquals(0, h.queue.snapshot().length);
        assertEquals("[{truncated", config(h).getConfiguration(GROUP, BACKUP));
        assertEquals("[]", config(h).getConfiguration(GROUP, QUEUE));
    }

    @Test public void oneDamagedEntryIsBackedUpAndTheRestRestored() throws Exception
    {
        Harness h = harness();
        h.finalizeReport();
        EncounterReportQueue.Entry good = h.queue.snapshot()[0];
        EncounterReportQueue.Entry bad = new EncounterReportQueue.Entry();
        bad.id = "not-a-uuid"; bad.payload = "{\"reportId\":\"not-a-uuid\"}"; bad.createdAt = 1;
        String saved = new Gson().toJson(new EncounterReportQueue.Entry[]{good, bad, null});
        config(h).setConfiguration(GROUP, QUEUE, saved);
        invoke(h.plugin, "loadEncounterReports");
        assertEquals(1, h.queue.snapshot().length);
        assertEquals(good.id, h.queue.snapshot()[0].id);
        assertEquals(saved, config(h).getConfiguration(GROUP, BACKUP));
    }
}

package com.huntmaster;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.runelite.api.ChatMessageType;
import org.junit.Test;
import static org.junit.Assert.*;

public class DoomCompletionEvidenceTest
{
    private final List<EncounterObservation.Snapshot> reports = new ArrayList<>();
    private final EncounterCapture capture = new EncounterCapture(reports::add);
    private final BossDetector doom = new BossDetector(BossDefinition.totalCounterCandidate(DoomCompletionEvidence.BOSS, "doom"));
    private final String assignment = UUID.randomUUID().toString();
    private static String message(int level)
    {
        return "Delve level: " + (level > 8 ? "8+ (" + level + ")" : level) + " duration: 1:02.40";
    }
    private void complete(int level, int tick)
    {
        capture.doomCompletion(doom, "Example", assignment, tick, 100000L + tick * 600,
            DoomCompletionEvidence.parse(ChatMessageType.GAMEMESSAGE, message(level)));
    }
    private void counter(Integer before, int after, int tick)
    {
        capture.counter(doom, "Example", assignment, tick, 100000L + tick * 600,
            EncounterObservation.CounterSource.COMPLETION_VARP, before, after);
    }
    private JsonObject report(int i) { return EncounterReportCodec.encodeObservation(reports.get(i)); }

    @Test public void parsesAllLevelsAndKnownDurationForms()
    {
        for (int level : new int[]{1, 7, 8, 9, 10, 100, Integer.MAX_VALUE})
        {
            DoomCompletionEvidence e = DoomCompletionEvidence.parse(ChatMessageType.GAMEMESSAGE, message(level));
            assertNotNull(e); assertEquals(level, e.completedLevel); assertEquals(level > 8, e.extended);
        }
        assertNotNull(DoomCompletionEvidence.parse(ChatMessageType.SPAM,
            "Delve level: <col=ff0000>8</col> duration: <col=ff0000>1:02.40</col>. Personal best: 0:59.40."));
        assertNotNull(DoomCompletionEvidence.parse(ChatMessageType.GAMEMESSAGE, message(8) + " (new personal best)"));
    }

    @Test public void rejectsPlayerChatMalformedAndOtherMessages()
    {
        for (ChatMessageType type : ChatMessageType.values())
            if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM)
                assertNull(DoomCompletionEvidence.parse(type, message(8)));
        for (String value : new String[]{"", "Delve level: 8 duration:", "Delve level: 8 duration: 1:99",
            message(0), "Delve level: 9 duration: 1:02.40", "Delve level: 8+ (8) duration: 1:02.40",
            "Delve level: 8+ (2147483648) duration: 1:02.40", "Delve level 1 - 8 duration: 10:02.40",
            "Your Doom of Mokhaiotl kill count is: 8.", "Entering delve level 8", "You have died.",
            "Player: " + message(8), message(8) + " arbitrary trailing text", "x".repeat(513)})
            assertNull(value, DoomCompletionEvidence.parse(ChatMessageType.GAMEMESSAGE, value));
        assertNull(DoomCompletionEvidence.parse(ChatMessageType.GAMEMESSAGE, null));
    }

    @Test public void levelsEightNineTenRemainSeparateEvenWithinOneWindow()
    {
        for (int i = 0; i < 3; i++) { complete(8 + i, 10 + 2 * i); counter(100 + i, 101 + i, 11 + 2 * i); }
        capture.advance(30);
        assertEquals(3, reports.size());
        for (int i = 0; i < 3; i++)
        {
            assertEquals(8 + i, reports.get(i).doomState.completedLevel);
            assertEquals(2, reports.get(i).signals.size());
            assertEquals(Integer.valueOf(101 + i), reports.get(i).signals.get(1).current);
            if (i > 0) assertNotEquals(reports.get(i-1).reportId, reports.get(i).reportId);
        }
    }

    @Test public void counterMayArriveBeforeOrAfterMessage()
    {
        counter(5, 6, 10); complete(8, 12); capture.advance(20);
        assertEquals(1, reports.size()); assertEquals(2, reports.get(0).doomState.tickOffset);
        complete(9, 30); counter(6, 7, 39); capture.advance(40);
        assertEquals(2, reports.size()); assertEquals(9, reports.get(1).signals.get(1).tickOffset);
    }

    @Test public void lowerLevelAndMissingCounterRemainTruthfulObservations()
    {
        complete(7, 10); capture.advance(20);
        assertEquals(7, report(0).getAsJsonObject("doomState").get("completedLevel").getAsInt());
        assertEquals(1, reports.get(0).signals.size());
        assertEquals("unresolved", report(0).get("outcome").getAsString());
        assertFalse(report(0).has("verificationMethod"));
    }

    @Test public void initialSynchronizationAndCounterOnlyNeverClaimCompletion()
    {
        counter(null, 100, 10); counter(100, 100, 11); capture.advance(30); assertTrue(reports.isEmpty());
        counter(100, 101, 40); capture.advance(50);
        assertNull(reports.get(0).doomState); assertEquals(1, reports.get(0).signals.size());
        assertEquals(EncounterObservation.SignalKind.COUNTER, reports.get(0).signals.get(0).kind);
    }

    @Test public void expiryAndAmbiguityDoNotInventPairings()
    {
        complete(8, 10); counter(5, 6, 20); capture.advance(30);
        assertEquals(2, reports.size()); assertEquals(1, reports.get(0).signals.size()); assertNull(reports.get(1).doomState);
        complete(9, 40); complete(10, 41); counter(6, 7, 42); capture.advance(60);
        assertEquals(5, reports.size());
        for (int i = 2; i < 5; i++) assertEquals(1, reports.get(i).signals.size());
    }

    @Test public void jumpsAndDecreasesRemainRawEvidence()
    {
        complete(8, 10); counter(5, 10, 11); capture.advance(20);
        assertEquals(Integer.valueOf(5), reports.get(0).signals.get(1).previous);
        assertEquals(Integer.valueOf(10), reports.get(0).signals.get(1).current);
        assertEquals("unresolved", report(0).get("outcome").getAsString());
    }

    @Test public void interruptionAndReloadDoNotCarryEvidenceIntoNextEncounter()
    {
        complete(8, 10); counter(5, 6, 11); capture.advance(20);
        capture.interrupt(21, EncounterObservation.Reason.LOGOUT);
        assertTrue(reports.get(0).captureComplete);
        complete(9, 30); capture.interrupt(31, EncounterObservation.Reason.SHUTDOWN); capture.clear();
        assertFalse(reports.get(1).captureComplete);
        counter(null, 100, 1); counter(100, 101, 2); capture.advance(12);
        assertNull(reports.get(2).doomState);
    }

    @Test public void accountAndAssignmentChangesCannotPair()
    {
        counter(5, 6, 10);
        capture.doomCompletion(doom, "Other", null, 11, 106600,
            DoomCompletionEvidence.parse(ChatMessageType.GAMEMESSAGE, message(8)));
        capture.advance(30);
        assertEquals(2, reports.size()); assertFalse(reports.get(0).captureComplete);
        assertEquals(1, reports.get(1).signals.size()); assertNull(reports.get(1).assignmentId);
    }

    @Test public void duplicateNotificationAndRetryKeepIdentityAndPayload()
    {
        complete(8, 10); complete(8, 10); counter(5, 6, 11); capture.advance(20);
        assertEquals(1, reports.size());
        String payload = report(0).toString(), id = reports.get(0).reportId.toString();
        EncounterReportQueue q = new EncounterReportQueue(); assertTrue(q.add(id, payload, 1000, 1000, true));
        assertFalse(q.add(id, payload, 1000, 1000, true));
        EncounterReportQueue.Entry entry = q.next(1000); entry.inFlight = true; q.releaseInFlight();
        assertSame(entry, q.next(1001));
        EncounterReportQueue.Entry restored = new Gson().fromJson(new Gson().toJson(entry), EncounterReportQueue.Entry.class);
        EncounterReportQueue next = new EncounterReportQueue(); next.add(restored.id, restored.payload, restored.createdAt, 1001, true);
        assertEquals(id, next.next(1001).id); assertEquals(payload, next.next(1001).payload);
        assertTrue(payload.length() < 8192); assertFalse(payload.contains("duration:"));
        assertEquals("/api/runelite/observations", EncounterReportCodec.deliveryRoute(report(0)));
    }

    @Test public void pendingRecordsRemainBoundedAndNonDoomSchemaUnchanged()
    {
        for (int i = 1; i <= 20; i++) complete(8 + i, 10);
        capture.advance(20); assertEquals(20, reports.size());
        assertEquals(12, reports.stream().filter(s -> !s.captureComplete).count());
        BossDetector other = new BossDetector(BossDefinition.totalCounterCandidate("Royal Titans", "titans"));
        capture.primary(other, "Example", null, 40, 124000, EncounterObservation.SignalKind.COMPLETION);
        capture.counter(other, "Example", null, 41, 124600, EncounterObservation.CounterSource.COMPLETION_VARP, 10, 11);
        capture.advance(50);
        JsonObject result = report(20); assertFalse(result.has("doomState"));
        assertEquals(1, result.get("schemaVersion").getAsInt()); assertEquals(2, result.getAsJsonArray("signals").size());
    }
}

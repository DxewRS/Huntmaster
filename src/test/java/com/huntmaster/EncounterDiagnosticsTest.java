package com.huntmaster;

import java.util.*;
import net.runelite.api.ChatMessageType;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncounterDiagnosticsTest
{
    @Test public void acceptsOnlyGameNotificationCategories()
    {
        assertTrue(CounterMessageEvidence.accepts(ChatMessageType.GAMEMESSAGE));
        assertTrue(CounterMessageEvidence.accepts(ChatMessageType.SPAM));
        assertTrue(CounterMessageEvidence.accepts(ChatMessageType.FRIENDSCHATNOTIFICATION));
        assertFalse(CounterMessageEvidence.accepts(ChatMessageType.PUBLICCHAT));
        assertFalse(CounterMessageEvidence.accepts(ChatMessageType.PRIVATECHAT));
        assertTrue(CounterMessageEvidence.related("Your Nightmare kill count is: unknown", "The Nightmare"));
        assertFalse(CounterMessageEvidence.related("Your Scurrius kill count is: 8", "The Nightmare"));
        assertEquals(Integer.valueOf(59), GenericKcRouter.parse("Your Nightmare kill count is: <col=ff0000>59</col>","The Nightmare"));
    }
    @Test public void delayedNightmareEvidenceStaysTogetherAndDiagnosticsAreBounded()
    {
        List<EncounterObservation.Snapshot> out = new ArrayList<>();
        EncounterCapture c = new EncounterCapture(out::add);
        BossDetector boss = new BossDetector(BossDefinition.betaCandidate("The Nightmare","nightmare","Your Nightmare kill count is:"));
        String assignment = UUID.randomUUID().toString();
        c.primary(boss,"Example",assignment,100,1000,EncounterObservation.SignalKind.DEATH);
        c.diagnostic("PROFILE_SNAPSHOT",58,100);
        c.advance(110);
        assertTrue(out.isEmpty());
        c.counter(boss,"Example",assignment,112,8200,EncounterObservation.CounterSource.KC_MESSAGE,58,59);
        c.diagnostic("SPAM",59,112);
        c.loot(boss,"Example",assignment,112,8200);
        for(int n=0;n<20;n++) c.diagnostic("PROFILE_CHANGED",59,113);
        c.advance(124);
        assertEquals(1,out.size());
        assertEquals(3,out.get(0).signals.size());
        assertEquals(3,out.get(0).diagnostics.size());
        assertEquals("server_observation",EncounterReportCodec.encodeObservation(out.get(0)).get("trackingMode").getAsString());
        c.primary(boss,"Example",assignment,130,19000,EncounterObservation.SignalKind.DEATH);
        c.interrupt(131,EncounterObservation.Reason.LOGOUT);
        assertTrue(out.get(1).diagnostics.isEmpty());
        assertEquals(1,out.get(1).signals.size());
    }
}

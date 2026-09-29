package com.huntmaster;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecruitmentNotificationsTest
{
    private JsonObject response(String cursor, String id, String... activities)
    {
        JsonObject body = new JsonObject(); body.addProperty("success", true); body.addProperty("cursor", cursor);
        JsonArray events = new JsonArray();
        if (id != null)
        {
            JsonObject event = new JsonObject(); event.addProperty("id", id);
            JsonArray names = new JsonArray(); for (String activity : activities) names.add(activity);
            event.add("activities", names); events.add(event);
        }
        body.add("events", events); return body;
    }
    @Test public void preferencesLoginAndDuplicates()
    {
        RecruitmentNotifications state = new RecruitmentNotifications();
        assertNull(state.begin("Player", Set.of(), 1000));
        assertTrue(state.complete(state.begin("Player", Set.of("toa"), 1000), response("1:1000", "1", "toa"), 1001).isEmpty());
        assertEquals("Huntmaster: Someone is looking for members for Tombs of Amascut. Join through Bosscape Discord.",
            state.complete(state.begin("Player", Set.of("toa"), 6000), response("2:6000", "2", "toa", "nex", "toa"), 6001).get(0));
        assertTrue(state.complete(state.begin("Player", Set.of("toa"), 11000), response("2:11000", "2", "toa"), 11001).isEmpty());
        assertTrue(state.complete(state.begin("Player", Set.of("toa"), 16000), response("3:16000", "3", "nex"), 16001).isEmpty());
    }
    @Test public void reconnectAccountSwitchAndOldResponses()
    {
        RecruitmentNotifications state = new RecruitmentNotifications();
        state.complete(state.begin("Player", Set.of("toa"), 1000), response("0:1000", null), 1001);
        RecruitmentNotifications.Poll old = state.begin("Player", Set.of("toa"), 6000);
        assertTrue(state.isCurrent(old));
        state.reset();
        assertFalse(state.isCurrent(old));
        assertFalse(state.isCurrent(null));
        assertTrue(state.complete(old, response("1:6000", "1", "toa"), 6001).isEmpty());
        assertTrue(state.complete(state.begin("Other", Set.of("toa"), 7000), response("1:7000", "1", "toa"), 7001).isEmpty());
        assertTrue(state.complete(state.begin("Other", Set.of("toa"), 30000), response("2:30000", "2", "toa"), 30001).isEmpty());
    }
    @Test public void failedAndSlowPollsAndOptOut()
    {
        RecruitmentNotifications state = new RecruitmentNotifications();
        state.complete(state.begin("Player", Set.of("toa"), 1000), response("0:1000", null), 1001);
        assertTrue(state.complete(state.begin("Player", Set.of("toa"), 6000), null, 6001).isEmpty());
        assertTrue(state.complete(state.begin("Player", Set.of("toa"), 11000), response("1:11000", "1", "toa"), 11001).isEmpty());
        RecruitmentNotifications.Poll pending = state.begin("Player", Set.of("toa"), 16000);
        assertNull(state.begin("Player", Set.of(), 16001));
        assertTrue(state.complete(pending, response("2:16000", "2", "toa"), 16002).isEmpty());
        pending = state.begin("Player", Set.of("toa"), 20000);
        assertTrue(state.complete(pending, response("2:20000", "2", "toa"), 31000).isEmpty());
    }
    @Test public void noInventedNamesAndDefaultsOff()
    {
        assertNull(RecruitmentActivity.fromId("<col=ff0000>fake"));
        BosscapeSettings settings = new BosscapeSettings() {};
        assertFalse(settings.notifyToa()); assertFalse(settings.notifyTfa());
        assertFalse(settings.notifyNex()); assertFalse(settings.notifySoulWars());
        assertEquals(32, RecruitmentActivity.values().length);
    }
}

package com.huntmaster;

import java.lang.reflect.Field;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import org.junit.Test;
import static org.junit.Assert.*;

public class LogoutDeliveryTest
{
    private static Field field(String name) throws Exception
    {
        Field field = HuntmasterPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @Test public void logoutInvalidatesCallbacksAndPreservesReportsForRetry() throws Exception
    {
        HuntmasterPlugin plugin = new HuntmasterPlugin();
        field("running").setBoolean(plugin, true);
        field("registrationConfirmed").setBoolean(plugin, true);
        field("nextHealthCheckAt").setLong(plugin, Long.MAX_VALUE);
        EncounterReportQueue queue = (EncounterReportQueue) field("reportQueue").get(plugin);
        assertTrue(queue.add("saved-report", "{}", 1000, 1000, true));
        EncounterReportQueue.Entry entry = queue.next(1000);
        entry.inFlight = true;
        long generation = field("requestGeneration").getLong(plugin);

        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(event);

        assertEquals(generation + 1, field("requestGeneration").getLong(plugin));
        assertFalse(field("registrationConfirmed").getBoolean(plugin));
        assertTrue(field("assignmentSyncRequired").getBoolean(plugin));
        assertEquals(0, field("nextHealthCheckAt").getLong(plugin));
        assertFalse(entry.inFlight);
        assertSame(entry, queue.next(1000));
        assertEquals("saved-report", entry.id);
    }
}

package com.huntmaster;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecruitmentSelectionsTest
{
    @Test public void unchangedPollsReadEachSettingOnlyOnce()
    {
        RecruitmentSelections choices = new RecruitmentSelections();
        AtomicInteger reads = new AtomicInteger();
        Set<String> selected = choices.get(key -> { reads.incrementAndGet(); return "true"; });
        assertEquals(32, selected.size()); assertTrue(selected.contains("tfa"));
        for (int i = 0; i < 1000; i++)
            assertSame(selected, choices.get(key -> { fail("Unchanged settings reread"); return null; }));
        assertEquals(32, reads.get());
        try { selected.clear(); fail("Mutable cached selection"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void invalidationReplacesTheSnapshotWithoutMutatingPendingChoices()
    {
        RecruitmentSelections choices = new RecruitmentSelections();
        Map<String, String> settings = new HashMap<>();
        RecruitmentActivity first = RecruitmentActivity.values()[0];
        settings.put(first.configKey(), "true");
        Set<String> before = choices.get(settings::get);
        settings.clear(); choices.invalidate();
        assertTrue(choices.get(settings::get).isEmpty());
        assertEquals(Set.of(first.id), before);
        for (RecruitmentActivity activity : RecruitmentActivity.values()) settings.put(activity.configKey(), "true");
        choices.invalidate(); assertEquals(32, choices.get(settings::get).size());
    }

    @Test public void realPluginSettingAndRuneLiteProfileEventsInvalidateTheCache() throws Exception
    {
        HuntmasterPlugin plugin = new HuntmasterPlugin();
        Field cached = HuntmasterPlugin.class.getDeclaredField("recruitmentSelections"); cached.setAccessible(true);
        RecruitmentSelections selections = (RecruitmentSelections) cached.get(plugin);
        Field clientThread = HuntmasterPlugin.class.getDeclaredField("clientThread"); clientThread.setAccessible(true);
        clientThread.set(plugin, new ClientThread() { @Override public void invokeLater(Runnable action) { action.run(); } });
        assertEquals(32, selections.get(key -> "true").size());
        ConfigChanged event = new ConfigChanged(); event.setGroup("unrelated"); event.setKey("setting");
        plugin.onConfigChanged(event);
        assertEquals(32, selections.get(key -> { fail("Unrelated setting invalidated cache"); return null; }).size());
        event.setGroup("huntmaster"); event.setKey(RecruitmentActivity.values()[0].configKey());
        plugin.onConfigChanged(event);
        assertTrue(selections.get(key -> null).isEmpty());
        plugin.onProfileChanged(new ProfileChanged());
        assertEquals(32, selections.get(key -> "true").size());
        event.setKey("enableRecruitmentAlerts"); plugin.onConfigChanged(event);
        assertEquals(32, selections.get(key -> "true").size());
    }
}

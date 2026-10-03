package com.huntmaster;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;
import static org.junit.Assert.*;

public class CounterIdentityRoutingTest
{
    @Test public void everyPackagedCounterIdentityHasExactlyOneOwner()
    {
        for (String boss : SupportedEncounters.NAMES)
        {
            assertOwner("Your " + boss + " kill count is: unknown", boss);
            assertOwner("Your " + boss + " kill count is: 123.", boss);
        }
        for (BossDetector detector : BossRegistry.createDetectors())
            assertOwner(detector.getKcMessagePrefix() + " unknown", detector.getName());
    }

    private void assertOwner(String message, String expected)
    {
        Set<String> owners = new HashSet<>();
        for (String boss : SupportedEncounters.NAMES)
            if (CounterMessageEvidence.related(message, boss)) owners.add(boss);
        assertEquals(message, java.util.Collections.singleton(expected), owners);
    }

    @Test public void aliasesAndMalformedValuesKeepExactIdentity()
    {
        String[][] pairs = {{"Gauntlet", "The Gauntlet"}, {"Corrupted Gauntlet", "The Corrupted Gauntlet"},
            {"Nightmare", "The Nightmare"}, {"Phosani's Nightmare", "Phosani's Nightmare"},
            {"Barrows chest", "Barrows Brothers"}, {"Barrows Chests", "Barrows Brothers"},
            {"Lunar Chest", "Moons of Peril"}, {"Lunar Chests", "Moons of Peril"},
            {"Hueycoatl", "The Hueycoatl"}, {"Mimic", "The Mimic"},
            {"Leviathan", "The Leviathan"}, {"Whisperer", "The Whisperer"}};
        for (String[] pair : pairs)
            for (String value : new String[]{"123.", "unknown", "999999999999999999999"})
                assertOwner("Your <col=ff0000>" + pair[0] + "</col> completion count is: " + value, pair[1]);
        assertOwner("Your subdued Wintertodt count is: unknown", "Wintertodt");
        assertOwner("Your completed Gauntlet count is: unknown", "The Gauntlet");
    }

    private ImmediateReportDeliveryTest.Harness harness(String assignment) throws Exception
    {
        ImmediateReportDeliveryTest.Harness h = new ImmediateReportDeliveryTest.Harness();
        ImmediateReportDeliveryTest.set(h.plugin, "connectionInitialized", true);
        ImmediateReportDeliveryTest.set(h.plugin, "reliabilityRsn", "Example");
        ImmediateReportDeliveryTest.set(h.plugin, "assignedBoss", assignment);
        ImmediateReportDeliveryTest.set(h.plugin, "assignmentId", assignment == null ? null : h.assignment);
        // A real, unloaded ConfigManager returns unknown profile counters. Its scheduler
        // is inert so this fixture neither starts threads nor touches player settings.
        java.lang.reflect.Constructor<?> constructor = net.runelite.client.config.ConfigManager.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] dependencies = new Object[constructor.getParameterCount()];
        Class<?>[] types = constructor.getParameterTypes();
        for (int i = 0; i < types.length; i++)
        {
            if (types[i] == java.util.concurrent.ScheduledExecutorService.class)
                dependencies[i] = java.lang.reflect.Proxy.newProxyInstance(types[i].getClassLoader(), new Class[]{types[i]}, (p, m, a) -> null);
            else if (types[i] == Gson.class) dependencies[i] = new Gson();
            else if (types[i] == net.runelite.client.eventbus.EventBus.class) dependencies[i] = new net.runelite.client.eventbus.EventBus();
        }
        Object config = constructor.newInstance(dependencies);
        Class<?> dataType = Class.forName("net.runelite.client.config.ConfigData");
        java.lang.reflect.Constructor<?> dataConstructor = dataType.getDeclaredConstructor(java.io.File.class);
        dataConstructor.setAccessible(true);
        java.lang.reflect.Field profile = net.runelite.client.config.ConfigManager.class.getDeclaredField("configProfile");
        profile.setAccessible(true);
        profile.set(config, dataConstructor.newInstance(new java.io.File(System.getProperty("huntmaster.testHome"), "routing.properties")));
        ImmediateReportDeliveryTest.set(h.plugin, "configManager", config);
        return h;
    }

    private void send(ImmediateReportDeliveryTest.Harness h, String name, String value)
    {
        ChatMessage event = new ChatMessage();
        event.setType(ChatMessageType.GAMEMESSAGE);
        event.setMessage("Your " + name + " completion count is: " + value);
        h.plugin.onChatMessage(event);
    }

    private Set<String> finish(ImmediateReportDeliveryTest.Harness h) throws Exception
    {
        assertEquals(0L, ImmediateReportDeliveryTest.field("collectionCaptureFailures").getLong(h.plugin));
        ((EncounterCapture)ImmediateReportDeliveryTest.field("encounterCapture").get(h.plugin)).advance(400);
        Set<String> bosses = new HashSet<>();
        for (EncounterReportQueue.Entry entry : h.queue.snapshot())
        {
            JsonObject report = new Gson().fromJson(entry.payload, JsonObject.class);
            assertEquals("Example", report.get("rsn").getAsString());
            assertTrue("Duplicate report for same boss", bosses.add(report.get("boss").getAsString()));
        }
        return bosses;
    }

    @Test public void chatEventsNeverCreateSecondaryGauntletDiagnostics() throws Exception
    {
        for (String assignment : new String[]{null, "The Gauntlet", "Nex"})
            for (String value : new String[]{"123.", "unknown"})
            {
                ImmediateReportDeliveryTest.Harness h = harness(assignment);
                send(h, "Corrupted Gauntlet", value);
                assertEquals(java.util.Collections.singleton("The Corrupted Gauntlet"), finish(h));
            }
    }

    @Test public void independentOverlappingNotificationsKeepSeparateCaptures() throws Exception
    {
        ImmediateReportDeliveryTest.Harness h = harness(null);
        for (String name : new String[]{"Gauntlet", "Corrupted Gauntlet", "Nightmare", "Phosani's Nightmare"})
            send(h, name, "unknown");
        assertEquals(new HashSet<>(java.util.Arrays.asList("The Gauntlet", "The Corrupted Gauntlet",
            "The Nightmare", "Phosani's Nightmare")), finish(h));
    }

    @Test public void arbitraryTextCannotBorrowKnownBossNames() throws Exception
    {
        ImmediateReportDeliveryTest.Harness h = harness(null);
        for (String name : new String[]{"Baby Giant Mole", "Royal Titans impostor", "Unknown Nightmare"})
            send(h, name, "unknown");
        assertTrue(finish(h).isEmpty());
        assertFalse(CounterMessageEvidence.related("Your message about Nightmare count", "The Nightmare"));
        assertNull(GenericKcRouter.parse("Your Kraken kill count is: 999999999999999999", "Kraken"));
        assertEquals(Integer.valueOf(1234), GenericKcRouter.parse(
            "Your <col=ff0000>Sarachnis</col> kill count is: 1,234. Fight duration: 0:45", "Sarachnis"));
    }

    @Test public void npcAliasesHaveOneOwnerAcrossAllPackagedEncounters()
    {
        for (String boss : SupportedEncounters.NAMES) assertNpcOwner(boss, boss);
        String[][] aliases = {{"Dawn", "Grotesque Guardians"}, {"Dusk", "Grotesque Guardians"},
            {"Branda the Fire Queen", "Royal Titans"}, {"Eldric the Ice King", "Royal Titans"},
            {"Nightmare", "The Nightmare"}, {"Hueycoatl", "The Hueycoatl"}, {"Mimic", "The Mimic"}};
        for (String[] pair : aliases) assertNpcOwner(pair[0], pair[1]);
    }

    private void assertNpcOwner(String npc, String expected)
    {
        Set<String> owners = new HashSet<>();
        for (String boss : SupportedEncounters.NAMES)
            if (BossNpcAliases.matches(boss, npc)) owners.add(boss);
        assertEquals(npc, java.util.Collections.singleton(expected), owners);
    }

    @Test public void deathAndLootKeepTheirActualBossWhileOffAssignment() throws Exception
    {
        ImmediateReportDeliveryTest.Harness h = harness("Artio");
        for (String name : new String[]{"Artio", "Callisto"})
        {
            net.runelite.api.NPC npc = (net.runelite.api.NPC) java.lang.reflect.Proxy.newProxyInstance(
                net.runelite.api.NPC.class.getClassLoader(), new Class[]{net.runelite.api.NPC.class},
                (p, m, a) -> m.getName().equals("getName") ? name : m.getName().equals("getId") ? 1 : null);
            h.plugin.onActorDeath(new net.runelite.api.events.ActorDeath(npc));
            h.plugin.onNpcLootReceived(new net.runelite.client.events.NpcLootReceived(npc, java.util.Collections.emptyList()));
        }
        assertEquals(new HashSet<>(java.util.Arrays.asList("Artio", "Callisto")), finish(h));
        for (EncounterReportQueue.Entry entry : h.queue.snapshot())
        {
            JsonObject report = new Gson().fromJson(entry.payload, JsonObject.class);
            assertEquals(h.assignment, report.get("assignmentId").getAsString());
            assertEquals(2, report.getAsJsonArray("signals").size());
        }
    }
}

package com.huntmaster;
import java.lang.reflect.Method;
import org.junit.Test;
import static org.junit.Assert.*;
public class CollectionRoutingTest
{
    private BossDetector route(HuntmasterPlugin p,String method,String input)throws Exception
    {
        Method m=HuntmasterPlugin.class.getDeclaredMethod(method,String.class);m.setAccessible(true);return (BossDetector)m.invoke(p,input);
    }
    @Test public void actualBossRoutingDoesNotDependOnAssignment()throws Exception
    {
        HuntmasterPlugin p=new HuntmasterPlugin();
        for(String assignment:new String[]{null,"The Gauntlet","The Corrupted Gauntlet","Artio","Nex"}){
            ImmediateReportDeliveryTest.set(p,"assignedBoss",assignment);
            assertEquals("The Corrupted Gauntlet",route(p,"findDetectorByKcMessage","Your Corrupted Gauntlet completion count is: 123.").getName());
            assertEquals("The Gauntlet",route(p,"findDetectorByKcMessage","Your Gauntlet completion count is: 77.").getName());
            assertEquals("Callisto",route(p,"findDetectorByNpcName","Callisto").getName());
            assertEquals("Barrows Brothers",route(p,"findDetectorByKcMessage","Your Barrows chest count is: 321.").getName());
            assertEquals("Moons of Peril",route(p,"findDetectorByKcMessage","Your Lunar chest count is: 44.").getName());
            assertNull(route(p,"findDetectorByKcMessage","someone says Your Nex kill count is: 5."));
        }
    }
    @Test public void registrationAndBacklogStillGateUnassignedCollection()throws Exception
    {
        ImmediateReportDeliveryTest.Harness h=new ImmediateReportDeliveryTest.Harness();
        ImmediateReportDeliveryTest.set(h.plugin,"connectionInitialized",true);
        ImmediateReportDeliveryTest.set(h.plugin,"reliabilityRsn","Example");
        Method m=HuntmasterPlugin.class.getDeclaredMethod("canTrackNewKills");m.setAccessible(true);
        assertTrue((Boolean)m.invoke(h.plugin));
        ImmediateReportDeliveryTest.set(h.plugin,"registrationConfirmed",false);assertFalse((Boolean)m.invoke(h.plugin));
        ImmediateReportDeliveryTest.set(h.plugin,"registrationConfirmed",true);
        ImmediateReportDeliveryTest.set(h.plugin,"trackingPaused",true);assertFalse((Boolean)m.invoke(h.plugin));
    }
    @Test public void dedicatedTotalCapturesCompletionWithoutAssignmentOrChat()throws Exception
    {
        ImmediateReportDeliveryTest.Harness h=new ImmediateReportDeliveryTest.Harness();
        net.runelite.api.Client original=(net.runelite.api.Client)ImmediateReportDeliveryTest.field("client").get(h.plugin);
        net.runelite.api.Client client=(net.runelite.api.Client)java.lang.reflect.Proxy.newProxyInstance(net.runelite.api.Client.class.getClassLoader(),new Class[]{net.runelite.api.Client.class},
            (p,m,a)->m.getName().equals("getVarpValue")?101:m.invoke(original,a));
        ImmediateReportDeliveryTest.set(h.plugin,"client",client);
        BossDetector d=new BossDetector(BossDefinition.totalCounterCandidate("Royal Titans","royal titans"));d.setLastKc(100);
        Method tick=HuntmasterPlugin.class.getDeclaredMethod("handleTotalGameTick",BossDetector.class);tick.setAccessible(true);tick.invoke(h.plugin,d);
        EncounterCapture capture=(EncounterCapture)ImmediateReportDeliveryTest.field("encounterCapture").get(h.plugin);capture.advance(300);
        assertEquals(1,h.queue.snapshot().length);
        com.google.gson.JsonObject report=new com.google.gson.Gson().fromJson(h.queue.snapshot()[0].payload,com.google.gson.JsonObject.class);
        assertTrue(report.get("assignmentId").isJsonNull());
        assertEquals("Royal Titans",report.get("boss").getAsString());
        assertEquals("completion",report.getAsJsonArray("signals").get(0).getAsJsonObject().get("kind").getAsString());
        assertEquals(101,report.getAsJsonArray("signals").get(1).getAsJsonObject().get("current").getAsInt());
    }
}

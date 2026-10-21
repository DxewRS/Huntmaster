package com.huntmaster;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.*;

public class OutageNotificationsTest
{
    @Test public void shortInterruptionsAreSilentAndDelayedNoticesAreReentrySafe() throws Exception
    {
        HuntmasterPlugin plugin=new HuntmasterPlugin();List<String> messages=new ArrayList<>();
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(p,m,a)->m.getName().equals("getName")?"Example":null);
        Client client=(Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class[]{Client.class},(p,m,a)->{
            if(m.getName().equals("getGameState"))return GameState.LOGGED_IN;
            if(m.getName().equals("getLocalPlayer"))return player;
            if(m.getName().equals("addChatMessage")){messages.add((String)a[2]);ImmediateReportDeliveryTest.invoke(plugin,"showOutageNotices");}
            return null;
        });
        ImmediateReportDeliveryTest.set(plugin,"client",client);ImmediateReportDeliveryTest.set(plugin,"running",true);ImmediateReportDeliveryTest.set(plugin,"connectionInitialized",true);
        ConnectionOutageState outage=(ConnectionOutageState)ImmediateReportDeliveryTest.field("outage").get(plugin);
        outage.fail(System.currentTimeMillis()-60000);ImmediateReportDeliveryTest.invoke(plugin,"showOutageNotices");assertTrue(messages.isEmpty());
        outage.restore(System.currentTimeMillis()-301000,System.currentTimeMillis());
        ImmediateReportDeliveryTest.invoke(plugin,"showOutageNotices");ImmediateReportDeliveryTest.invoke(plugin,"showOutageNotices");
        assertEquals(1,messages.size());assertTrue(messages.get(0).contains("5 minutes"));
        ImmediateReportDeliveryTest.set(plugin,"trackingPaused",true);
        ImmediateReportDeliveryTest.invoke(plugin,"showOutageNotices");ImmediateReportDeliveryTest.invoke(plugin,"showOutageNotices");
        assertEquals(2,messages.size());assertTrue(messages.get(1).contains("10 minutes"));
        ImmediateReportDeliveryTest.set(plugin,"recoveryNoticePending",true);
        ImmediateReportDeliveryTest.invoke(plugin,"showOutageNotices");assertEquals(3,messages.size());
        ImmediateReportDeliveryTest.invoke(plugin,"showOutageNotices");assertEquals(3,messages.size());
    }
    @Test public void activityPreferencesRemainOptInWithoutRepeatedConfirmation() throws Exception
    {
        BosscapeSettings settings=new BosscapeSettings(){};int count=0;
        for(java.lang.reflect.Method method:BosscapeSettings.class.getMethods()){
            ConfigItem item=method.getAnnotation(ConfigItem.class);
            if(item!=null&&item.keyName().startsWith("recruitment_")&&method.getReturnType()==boolean.class){
                count++;assertEquals("",item.warning());assertEquals(false,method.invoke(settings));
            }
        }
        assertEquals(32,count);
    }
}

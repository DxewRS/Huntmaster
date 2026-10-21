package com.huntmaster;

import com.google.gson.JsonObject;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.UUID;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReleaseHardeningTest
{
    static JsonObject hunt(String id,int progress,long sequence)
    {
        JsonObject body=new JsonObject();body.addProperty("success",true);body.addProperty("rsn","Fixture");
        body.addProperty("status","solo");body.addProperty("sequence",sequence);
        JsonObject task=new JsonObject();task.addProperty("id",id);task.addProperty("boss","Vardorvis");
        task.addProperty("progress",progress);task.addProperty("requiredKC",100);task.addProperty("rewardPoints",267);
        body.add("assignment",task);return body;
    }
    @Test public void unchangedPollsDoNotRepaintAndHiddenChangesAppearWhenReopened() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            AssignmentDashboardState state=new AssignmentDashboardState();state.reset("Fixture",1000);String id=UUID.randomUUID().toString();
            HuntmasterPanel panel=new HuntmasterPanel();state.accept(hunt(id,5,1),1001,null,0);panel.update(state.view());panel.onActivate();
            RepaintManager previous=RepaintManager.currentManager(panel);
            class CountRepaints extends RepaintManager {
                int paints;
                @Override public void addDirtyRegion(JComponent c,int x,int y,int w,int h){if(c==panel)paints++;}
            }
            CountRepaints counter=new CountRepaints();RepaintManager.setCurrentManager(counter);
            try {
                for(int i=2;i<1002;i++){state.accept(hunt(id,5,i),1000+i,null,0);panel.update(state.view());}
                assertEquals(0,counter.paints);
                state.accept(hunt(id,6,1002),3000,null,0);panel.update(state.view());assertTrue(counter.paints>0);
                assertTrue(HuntmasterPanelTest.visibleText(panel).contains("6 / 100 KC"));
                panel.onDeactivate();int paints=counter.paints;
                state.accept(hunt(id,7,1003),4000,null,0);panel.update(state.view());assertEquals(paints,counter.paints);
                panel.onActivate();assertTrue(HuntmasterPanelTest.visibleText(panel).contains("7 / 100 KC"));
                state.connection("Huntmaster unavailable");panel.update(state.view());assertTrue(HuntmasterPanelTest.visibleText(panel).contains("Huntmaster unavailable"));
            } finally {RepaintManager.setCurrentManager(previous);}
        });
    }

    @Test public void aboutVersionMatchesReleaseMetadata() throws Exception
    {
        Properties properties=new Properties();try(FileInputStream in=new FileInputStream("runelite-plugin.properties")){properties.load(in);}
        assertEquals(properties.getProperty("version"),ReleaseInfo.VERSION);
        net.runelite.client.config.ConfigItem label=BosscapeSettings.class.getMethod("releaseInformation").getAnnotation(net.runelite.client.config.ConfigItem.class);
        assertTrue(label.name().contains(ReleaseInfo.VERSION));assertTrue(label.name().contains(ReleaseInfo.BUILD));assertEquals(BosscapeSettings.about,label.section());
    }
}

package com.huntmaster;

import com.google.gson.JsonObject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankPanelTest
{
    private static final String[] NAMES={"New Blood","Novice Hunter","Skilled Hunter","Seasoned Hunter","Veteran Hunter","Elite Hunter","Master Hunter","Alpha Hunter","Apex Hunter","Godly Hunter"};
    private static String file(String name){return name.toLowerCase(java.util.Locale.ROOT).replace(' ','-')+".png";}
    private static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container)layout((Container)c);}
    private static void bounds(Container root,HuntmasterPanel panel)
    {
        for(Component c:root.getComponents())if(c.isVisible()){
            Rectangle box=SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),panel);
            assertTrue("Horizontal clipping: "+c,box.x>=0&&box.x+box.width<=225);
            if(c instanceof JLabel)assertTrue("Text clipping: "+((JLabel)c).getText(),c.getPreferredSize().width<=c.getWidth());
            if(c instanceof Container)bounds((Container)c,panel);
        }
    }
    private static AssignmentDashboardState.Assignment hunt()
    {
        JsonObject json=new JsonObject();json.addProperty("id",UUID.randomUUID().toString());json.addProperty("boss","Vardorvis");json.addProperty("progress",13);json.addProperty("requiredKC",16);json.addProperty("rewardPoints",267);
        return new AssignmentDashboardState.Assignment(json,false);
    }
    @Test public void packagedRanksRenderAtNativeWidthIncludingMaximumAndMissingArtwork() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            try{UIManager.setLookAndFeel(new net.runelite.client.ui.laf.RuneLiteLAF());}catch(Exception e){throw new AssertionError(e);}
            File directory=new File("build/reports/rank-sidebar-20261021");directory.mkdirs();
            BufferedImage sheet=new BufferedImage(225*5,1000*2,BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();g.setColor(net.runelite.client.ui.ColorScheme.DARK_GRAY_COLOR);g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
            for(int i=0;i<NAMES.length;i++){
                ImageIcon image=RankArtwork.icon(file(NAMES[i]));assertNotNull(NAMES[i],image);assertTrue(image.getIconWidth()<=80&&image.getIconHeight()<=80);
                JsonObject data=i==3?RankProgressTest.sample():RankProgressTest.rank(i==9?600000:125,NAMES[i],file(NAMES[i]),0,i==9?null:NAMES[i+1],1000);
                RankProgress rank=new RankProgress(data);HuntmasterPanel panel=new HuntmasterPanel();panel.update(new AssignmentDashboardState.View(hunt(),null,"Connected to Huntmaster",true,rank));panel.onActivate();
                panel.setSize(225,panel.getPreferredSize().height);layout(panel);assertTrue("Panel height "+panel.getHeight(),panel.getHeight()<1000);bounds(panel,panel);
                String text=HuntmasterPanelTest.visibleText(panel);assertTrue(text.contains("YOUR RANK"));assertTrue(text.contains(NAMES[i]));assertTrue(text.contains(rank.total()));assertTrue(text.contains(rank.remaining()));
                Graphics2D cell=(Graphics2D)g.create(i%5*225,i/5*1000,225,1000);panel.printAll(cell);cell.dispose();
                if(i==3)try{BufferedImage one=new BufferedImage(225,panel.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D single=one.createGraphics();panel.printAll(single);single.dispose();ImageIO.write(one,"png",new File(directory,"current-hunt-with-rank.png"));}catch(Exception e){throw new AssertionError(e);}
                panel.update(new AssignmentDashboardState.View(null,null,"Huntmaster unavailable",false,rank));panel.setSize(225,panel.getPreferredSize().height);layout(panel);bounds(panel,panel);assertTrue(HuntmasterPanelTest.visibleText(panel).contains("YOUR RANK"));
                panel.update(new AssignmentDashboardState.View(null,null,"Log in to RuneScape",false));assertFalse(HuntmasterPanelTest.visibleText(panel).contains("YOUR RANK"));
            }
            assertNull(RankArtwork.icon("../bosses/unknown.png"));assertNull(RankArtwork.icon("https://example.org/rank.png"));assertNull(RankArtwork.icon("unknown.png"));
            AssignmentDashboardState state=new AssignmentDashboardState();state.reset("Example",1000);
            HuntmasterPanel waiting=new HuntmasterPanel();waiting.update(state.view());waiting.onActivate();
            assertTrue(HuntmasterPanelTest.visibleText(waiting).contains("YOUR RANK"));assertTrue(HuntmasterPanelTest.visibleText(waiting).contains("Loading your rank"));
            waiting.update(new AssignmentDashboardState.View(null,null,"Huntmaster unavailable",false,new RankProgress(RankProgressTest.sample()),"Last confirmed rank"));
            assertTrue(HuntmasterPanelTest.visibleText(waiting).contains("Last confirmed rank"));assertTrue(HuntmasterPanelTest.visibleText(waiting).contains("Seasoned Hunter"));
            g.dispose();try{ImageIO.write(sheet,"png",new File(directory,"rank-states.png"));}catch(Exception e){throw new AssertionError(e);}
        });
    }
}

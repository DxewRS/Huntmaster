package com.huntmaster;

import com.google.gson.JsonObject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;
import static org.junit.Assert.*;

public class HuntmasterPanelTest
{
    static String visibleText(Container root)
    {
        StringBuilder text=new StringBuilder();
        for(Component c:root.getComponents())if(c.isVisible()){
            if(c instanceof JLabel)text.append(((JLabel)c).getText()).append(' ');
            if(c instanceof JProgressBar)text.append(((JProgressBar)c).getString()).append(' ');
            if(c instanceof JButton)text.append(((JButton)c).getText()).append(' ');
            if(c instanceof Container)text.append(visibleText((Container)c));
        }return text.toString();
    }
    private static List<Component> components(Container root)
    {
        List<Component> result=new ArrayList<>();
        for(Component c:root.getComponents())if(c.isVisible()){result.add(c);if(c instanceof Container)result.addAll(components((Container)c));}return result;
    }
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container)layout((Container)child);}
    private static AssignmentDashboardState.Assignment task(String boss,String type,int progress,int required,long points)
    {
        JsonObject j=new JsonObject();j.addProperty("id",UUID.randomUUID().toString());j.addProperty("boss",boss);j.addProperty("classification",type);
        j.addProperty("progress",progress);j.addProperty("requiredKC",required);j.addProperty("rewardPoints",points);return new AssignmentDashboardState.Assignment(j,type.startsWith("group"));
    }
    private static HuntmasterPanel panel(AssignmentDashboardState.View view)
    {HuntmasterPanel p=new HuntmasterPanel();p.update(view);p.onActivate();p.setSize(225,p.getPreferredSize().height);layout(p);return p;}

    @Test public void progressAndArtworkFollowConfirmedViewAndDiagnosticsAreAbsent() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            HuntmasterPanel p=panel(new AssignmentDashboardState.View(task("Vardorvis","solo",5,16,267),null,"Connected to Huntmaster",true));
            String text=visibleText(p);assertTrue(text.contains("CURRENT HUNT"));assertFalse(text.contains("TRACKING STATUS"));assertFalse(text.contains("Copy diagnostics"));
            assertTrue(text.contains("11 kills remaining"));assertTrue(text.contains("267 points"));
            JProgressBar bar=(JProgressBar)components(p).stream().filter(c->c instanceof JProgressBar).findFirst().get();
            assertEquals(5,bar.getValue());assertEquals(16,bar.getMaximum());assertEquals("5 / 16 KC",bar.getString());
            assertNotNull(BossArtwork.icon("Vardorvis"));assertNull(BossArtwork.icon("../../unknown"));
            p.update(new AssignmentDashboardState.View(task("Vardorvis","solo",6,16,267),null,"Connected to Huntmaster",true));
            assertEquals(6,bar.getValue());assertTrue(visibleText(p).contains("10 kills remaining"));
            p.update(new AssignmentDashboardState.View(task("<unknown boss>","solo",1,2,15),null,"Unavailable",false));
            assertTrue(visibleText(p).contains("&lt;unknown boss&gt;"));
        });
    }
    @Test public void everyPackagedBossHasDecodableBoundedArtwork()
    {
        for(String boss:SupportedEncounters.NAMES){ImageIcon icon=BossArtwork.icon(boss);assertNotNull(boss,icon);assertTrue(icon.getIconWidth()<=175);assertTrue(icon.getIconHeight()<=145);}
    }
    @Test public void allStatesFitNativeWidthAndProduceActualRuneLiteRender() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{
            try{UIManager.setLookAndFeel(new net.runelite.client.ui.laf.RuneLiteLAF());}catch(Exception e){throw new AssertionError(e);}
            String[] bosses={"Vardorvis","Royal Titans","The Corrupted Gauntlet","Phantom Muspah","Thermonuclear Smoke Devil","<unknown boss>"};
            List<AssignmentDashboardState.View> states=new ArrayList<>();
            for(int i=0;i<bosses.length;i++)states.add(new AssignmentDashboardState.View(task(bosses[i],i==1?"group":"solo",i==0?5:123,i==0?16:999,i==0?267:1234567),null,"Connected to Huntmaster",true));
            states.add(new AssignmentDashboardState.View(null,task("Vardorvis","solo",16,16,267),"Connected to Huntmaster",true));
            states.add(new AssignmentDashboardState.View(null,null,"Connected to Huntmaster",true));
            for(String status:new String[]{"Connecting...","Huntmaster unavailable","Register your RSN in Bosscape","Log in to RuneScape"})states.add(new AssignmentDashboardState.View(null,null,status,false));
            BufferedImage sheet=new BufferedImage(225*4,700*3,BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();
            g.setColor(ColorScheme.DARK_GRAY_COLOR);g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
            for(int i=0;i<states.size();i++){
                HuntmasterPanel p=panel(states.get(i));assertTrue("Panel height "+p.getPreferredSize().height,p.getPreferredSize().height<700);
                for(Component c:components(p))if(c instanceof JLabel||c instanceof JProgressBar||c instanceof JButton){
                    Rectangle r=SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),p);
                    assertTrue(c+" out of bounds "+r,r.x>=0&&r.x+r.width<=225);
                    if(c instanceof JLabel){assertTrue(c+" preferred width exceeds actual",c.getPreferredSize().width<=c.getWidth());assertEquals(SwingConstants.CENTER,((JLabel)c).getHorizontalAlignment());}
                }
                if(states.get(i).completed!=null){String text=visibleText(p);assertTrue(text.contains("HUNT COMPLETE"));assertTrue(text.contains("16 / 16 KC"));assertTrue(text.contains("Points awarded"));}
                Graphics2D cell=(Graphics2D)g.create((i%4)*225,(i/4)*700,225,700);p.printAll(cell);cell.dispose();
                if(i==0)try{BufferedImage one=new BufferedImage(225,p.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D single=one.createGraphics();p.printAll(single);single.dispose();File out=new File("build/reports/sidebar-20261021/current-hunt.png");out.getParentFile().mkdirs();ImageIO.write(one,"png",out);}catch(Exception e){throw new AssertionError(e);}
            }
            g.dispose();try{ImageIO.write(sheet,"png",new File("build/reports/sidebar-20261021/states.png"));}catch(Exception e){throw new AssertionError(e);}
        });
    }
}

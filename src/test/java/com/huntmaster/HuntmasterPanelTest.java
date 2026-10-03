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
import net.runelite.client.ui.FontManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class HuntmasterPanelTest
{
    @Test public void diagnosticsAreWrappedAndEscaped() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            HuntmasterPanel p=new HuntmasterPanel();
            p.diagnostics("Plugin update required through RuneLite","Evidence received; no new credit (<unknown>)","safe support text");
            String text=visibleText(p);assertTrue(text.contains("Plugin update required"));assertTrue(text.contains("&lt;unknown&gt;"));
            p.setSize(225,p.getPreferredSize().height);layout(p);
            for(JLabel label:labels(p))assertTrue(label.getPreferredSize().width<=225);
        });
    }
    static String visibleText(Container root) {
        StringBuilder text=new StringBuilder();
        for(Component c:root.getComponents())if(c.isVisible()) {
            if(c instanceof JLabel)text.append(((JLabel)c).getText()).append(' ');
            if(c instanceof Container)text.append(visibleText((Container)c));
        }
        return text.toString();
    }
    private static List<JLabel> labels(Container root) {
        List<JLabel> result=new ArrayList<>();
        for(Component c:root.getComponents())if(c.isVisible()) {
            if(c instanceof JLabel)result.add((JLabel)c);
            if(c instanceof Container)result.addAll(labels((Container)c));
        }
        return result;
    }
    private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container)layout((Container)child);}
    private static AssignmentDashboardState.Assignment task(String boss,String type,int progress,int required,long points) {
        JsonObject j=new JsonObject();j.addProperty("id",UUID.randomUUID().toString());j.addProperty("boss",boss);j.addProperty("classification",type);j.addProperty("progress",progress);j.addProperty("requiredKC",required);j.addProperty("rewardPoints",points);return new AssignmentDashboardState.Assignment(j,type.startsWith("group"));
    }
    private static HuntmasterPanel panel(AssignmentDashboardState.View view) {
        HuntmasterPanel p=new HuntmasterPanel();p.update(view);p.onActivate();p.setSize(225,p.getPreferredSize().height);layout(p);return p;
    }
    @Test public void bulletUsesActualSidebarFontAndSurvivesHtml() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            UIManager.put("Label.font",FontManager.getRunescapeFont());
            HuntmasterPanel p=panel(new AssignmentDashboardState.View(task("Amoxliatl","solo",1,2,15),null,"Connected to Huntmaster",true));
            JLabel identity=labels(p).stream().filter(l->l.getText().contains("Amoxliatl")).findFirst().get();
            assertTrue(identity.getText().contains("Amoxliatl \u2022 Solo"));
            assertTrue(identity.getFont().canDisplay('\u2022'));
            assertNotNull(identity.getClientProperty("html"));
            System.out.println("Sidebar bullet font: "+identity.getFont()+"; supports U+2022=true; RuneLite game font supports bullet="+FontManager.getRunescapeFont().canDisplay('\u2022'));
        });
    }
    @Test public void allStatesFitNativeWidthAndProduceVisualReview() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            UIManager.put("Panel.background",ColorScheme.DARK_GRAY_COLOR);UIManager.put("Button.background",ColorScheme.DARKER_GRAY_COLOR);UIManager.put("Button.foreground",Color.WHITE);
            String[] bosses={"Amoxliatl","Royal Titans","Corrupted Gauntlet","Phantom Muspah","The Theatre of Blood: Hard Mode"};
            String[] types={"solo","group","group MVP","mass","solo"};
            List<AssignmentDashboardState.View> states=new ArrayList<>();
            for(int i=0;i<bosses.length;i++)states.add(new AssignmentDashboardState.View(task(bosses[i],types[i],i==0?1:123,i==0?2:999,i==0?15:1234567),null,"Connected to Huntmaster",true));
            states.add(new AssignmentDashboardState.View(null,task("Amoxliatl","solo",2,2,15),"Connected to Huntmaster",true));
            states.add(new AssignmentDashboardState.View(null,null,"Connected to Huntmaster",true));
            for(String status:new String[]{"Connecting...","Huntmaster unavailable","Register your RSN in Bosscape","Log in to RuneScape"})states.add(new AssignmentDashboardState.View(null,null,status,false));
            BufferedImage sheet=new BufferedImage(225*4,620*3,BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();g.setColor(ColorScheme.DARK_GRAY_COLOR);g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
            for(int i=0;i<states.size();i++){
                HuntmasterPanel p=panel(states.get(i));assertTrue(p.getPreferredSize().height<620);
                for(JLabel l:labels(p)){
                    Rectangle bounds=SwingUtilities.convertRectangle(l.getParent(),l.getBounds(),p);
                    assertTrue(l.getText()+" extends beyond panel: "+bounds,bounds.x>=0&&bounds.x+bounds.width<=225);
                    assertTrue(l.getText()+" is clipped horizontally preferred="+l.getPreferredSize()+" actual="+l.getSize(),l.getPreferredSize().width<=l.getWidth());
                }
                String text=visibleText(p);
                if(states.get(i).completed!=null){assertTrue(text.contains("TASK COMPLETED"));assertTrue(text.contains("Completed"));assertTrue(text.contains("2 / 2 KC"));assertTrue(text.contains("15 pts"));assertTrue(text.contains("You do not have a current Huntmaster assignment."));}
                Graphics2D cell=(Graphics2D)g.create((i%4)*225,(i/4)*620,225,620);p.printAll(cell);cell.dispose();
            }
            g.dispose();try{File out=new File("build/reports/sidebar-states.png");out.getParentFile().mkdirs();ImageIO.write(sheet,"png",out);}catch(Exception e){throw new AssertionError(e);}
        });
    }
}

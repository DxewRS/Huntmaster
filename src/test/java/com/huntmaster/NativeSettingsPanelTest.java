package com.huntmaster;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.util.Properties;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;
import static org.junit.Assert.*;

/** Uses RuneLite's actual settings renderer and an isolated profile, never the player's profile. */
public class NativeSettingsPanelTest
{
    @Test public void nativeControlsFitAndMasterSwitchPreservesAllActivitySelections() throws Exception
    {
        ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor();
        try
        {
            Constructor<?> managerConstructor=ConfigManager.class.getDeclaredConstructors()[0];managerConstructor.setAccessible(true);
            Object[] arguments=new Object[managerConstructor.getParameterCount()];arguments[1]=executor;arguments[2]=new EventBus();
            ConfigManager manager=(ConfigManager)managerConstructor.newInstance(arguments);
            File profile=new File(System.getProperty("huntmaster.testHome"),"native-settings.properties");profile.getParentFile().mkdirs();
            Properties values=new Properties();
            values.setProperty("huntmaster.enableRecruitmentAlerts","true");values.setProperty("huntmaster.showAssignmentProgressOverlay","true");
            values.setProperty("huntmaster.huntOverlayVisibility",HuntOverlayVisibility.AFTER_CREDITED_KILL.name());
            for(RecruitmentActivity activity:RecruitmentActivity.values())values.setProperty("huntmaster."+activity.configKey(),"true");
            try(FileOutputStream stream=new FileOutputStream(profile)){values.store(stream,"Isolated RuneLite settings test");}
            Class<?> dataType=Class.forName("net.runelite.client.config.ConfigData");Constructor<?> dataConstructor=dataType.getDeclaredConstructor(File.class);dataConstructor.setAccessible(true);
            Field data=ConfigManager.class.getDeclaredField("configProfile");data.setAccessible(true);data.set(manager,dataConstructor.newInstance(profile));
            SwingUtilities.invokeAndWait(()->{
                try
                {
                    UIManager.setLookAndFeel(new net.runelite.client.ui.laf.RuneLiteLAF());
                    BosscapeSettings settings=new BosscapeSettings(){};
                    Class<?> descriptorType=Class.forName("net.runelite.client.plugins.config.PluginConfigurationDescriptor");
                    Constructor<?> descriptorConstructor=java.util.Arrays.stream(descriptorType.getDeclaredConstructors()).filter(c->c.getParameterCount()==5).findFirst().get();descriptorConstructor.setAccessible(true);
                    Object descriptor=descriptorConstructor.newInstance("Huntmaster","Huntmaster settings",new String[0],settings,manager.getConfigDescriptor(settings));
                    Class<?> panelType=Class.forName("net.runelite.client.plugins.config.ConfigPanel");Constructor<?> constructor=panelType.getDeclaredConstructors()[0];constructor.setAccessible(true);
                    Object[] dependencies=new Object[constructor.getParameterCount()];dependencies[1]=manager;
                    JPanel panel=(JPanel)constructor.newInstance(dependencies);Method init=panelType.getDeclaredMethod("init",descriptorType);init.setAccessible(true);init.invoke(panel,descriptor);
                    for(Component component:components(panel))if(component instanceof JLabel
                        && (((JLabel)component).getText().equals("Raids")||((JLabel)component).getText().equals("About Huntmaster")))
                        for(Component sibling:((JLabel)component).getParent().getComponents())if(sibling instanceof JButton)((JButton)sibling).doClick();
                    panel.setSize(225,1000);layout(panel);
                    JCheckBox master=null;int raidRows=0;
                    for(Component component:components(panel))
                    {
                        if(component instanceof JLabel)
                        {
                            JLabel label=(JLabel)component;
                            if(label.getText().equals("Enable recruitment alerts"))for(Component sibling:label.getParent().getComponents())if(sibling instanceof JCheckBox)master=(JCheckBox)sibling;
                            if(label.getText().equals("The Fractured Archive"))raidRows++;
                            if(visible(component)&&!label.getText().startsWith("<html>"))assertTrue(label.getText()+" clipped: "+label.getWidth()+" < "+label.getPreferredSize().width,label.getWidth()>=label.getPreferredSize().width);
                        }
                    }
                    assertEquals(1,raidRows);assertTrue(HuntmasterPanelTest.visibleText(panel).contains(ReleaseInfo.BUILD));
                    assertNotNull(master);assertTrue(master.isSelected());master.doClick();
                    assertEquals("false",manager.getConfiguration("huntmaster","enableRecruitmentAlerts"));
                    for(RecruitmentActivity activity:RecruitmentActivity.values())assertEquals(activity.id,"true",manager.getConfiguration("huntmaster",activity.configKey()));
                    master.doClick();assertEquals("true",manager.getConfiguration("huntmaster","enableRecruitmentAlerts"));
                    File image=new File("build/reports/settings-20261021/native-settings.png");image.getParentFile().mkdirs();
                    BufferedImage output=new BufferedImage(225,1000,BufferedImage.TYPE_INT_RGB);Graphics2D g=output.createGraphics();panel.printAll(g);g.dispose();ImageIO.write(output,"png",image);
                }
                catch(Exception e){throw new AssertionError(e);}
            });
        }
        finally{executor.shutdownNow();}
    }
    private static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container)layout((Container)c);}
    private static java.util.List<Component> components(Container root)
    {
        java.util.List<Component> result=new java.util.ArrayList<>();
        for(Component c:root.getComponents()){result.add(c);if(c instanceof Container)result.addAll(components((Container)c));}return result;
    }
    private static boolean visible(Component c){while(c!=null){if(!c.isVisible())return false;c=c.getParent();}return true;}
}

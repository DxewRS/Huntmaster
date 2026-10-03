package com.huntmaster;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/** Presentation only; the supplied immutable view owns all assignment/lifecycle state. */
final class HuntmasterPanel extends PluginPanel
{
    static final String DISCORD_URL="https://discord.gg/Bosscape";
    static final String GITHUB_URL="https://github.com/DxewRS/Huntmaster";
    private static final Color ACCENT=new Color(196,98,65);
    private static final Color MUTED=new Color(174,174,174);
    private static final Font BODY=FontManager.getDefaultFont().deriveFont(12f);
    private static final Font BOLD=FontManager.getDefaultBoldFont().deriveFont(12f);
    private final JPanel card=new JPanel();
    private final JLabel cardHeading=label("CURRENT ASSIGNMENT",BOLD,MUTED);
    private final JLabel identity=label("",BOLD,Color.WHITE);
    private final JLabel progressLabel=label("Progress",BODY,MUTED);
    private final JLabel progress=label("",BODY,Color.WHITE);
    private final JLabel reward=label("",BODY,Color.WHITE);
    private final JLabel message=label("",BODY,MUTED);
    private final JLabel connection=label("",BODY,MUTED);
    private AssignmentDashboardState.View latest;
    private boolean active;

    HuntmasterPanel()
    {
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        add(section("HUNTMASTER",ACCENT));gap(12);
        card.setLayout(new BoxLayout(card,BoxLayout.Y_AXIS));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(new CompoundBorder(new MatteBorder(0,2,0,0,ACCENT),new EmptyBorder(10,10,10,10)));
        card.add(cardHeading);card.add(Box.createVerticalStrut(9));card.add(identity);
        card.add(Box.createVerticalStrut(12));card.add(row(progressLabel,progress));
        card.add(Box.createVerticalStrut(5));card.add(row(label("Reward",BODY,MUTED),reward));
        for(Component c:card.getComponents())if(c instanceof JComponent)((JComponent)c).setAlignmentX(LEFT_ALIGNMENT);
        add(card);gap(8);add(message);gap(12);
        JButton discord=new JButton("Open Huntmaster in Discord");
        discord.setFont(BODY);discord.setMargin(new Insets(7,4,7,4));
        discord.setToolTipText("Manage your assignment in Bosscape Discord");
        discord.setMaximumSize(new Dimension(Integer.MAX_VALUE,32));
        discord.addActionListener(e->LinkBrowser.browse(DISCORD_URL));add(discord);gap(22);
        add(section("ABOUT HUNTMASTER",MUTED));gap(10);
        JLabel about=label(wrap("Huntmaster connects RuneLite with the Bosscape Discord to track PvM assignments.<br><br>Complete assignments to earn points, climb the leaderboards, and unlock Huntmaster ranks.",201),BODY,MUTED);
        add(about);gap(10);
        JPanel links=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));links.setOpaque(false);
        links.add(link("Discord","discord_icon.png",DISCORD_URL,ACCENT));links.add(Box.createHorizontalStrut(8));
        links.add(link("GitHub","github_icon.png",GITHUB_URL,null));add(links);gap(16);add(connection);
        for(Component child:getComponents())if(child instanceof JComponent)((JComponent)child).setAlignmentX(LEFT_ALIGNMENT);
    }
    private static JLabel label(String text,Font font,Color color)
    { JLabel l=new JLabel(text);l.setFont(font);l.setForeground(color);return l; }
    private static JPanel row(JLabel key,JLabel value)
    { JPanel p=new JPanel(new BorderLayout(6,0));p.setOpaque(false);p.add(key,BorderLayout.WEST);p.add(value,BorderLayout.EAST);return p; }
    private static JLabel section(String text,Color color)
    { JLabel l=label(text,BOLD,color);l.setBorder(new CompoundBorder(new MatteBorder(0,0,1,0,ColorScheme.MEDIUM_GRAY_COLOR),new EmptyBorder(0,0,7,0)));l.setMaximumSize(new Dimension(Integer.MAX_VALUE,l.getPreferredSize().height));return l; }
    private void gap(int height){add(Box.createVerticalStrut(height));}
    private static String wrap(String text,int width){return "<html><table width='"+width+"' cellpadding='0' cellspacing='0'><tr><td>"+text+"</td></tr></table></html>";}
    private static String html(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private JButton link(String name,String resource,String url,Color tint)
    {
        BufferedImage icon=ImageUtil.loadImageResource(HuntmasterPanel.class,"/net/runelite/client/plugins/info/"+resource);
        if(tint!=null)icon=ImageUtil.recolorImage(icon,tint);
        JButton button=new JButton(new ImageIcon(ImageUtil.resizeImage(icon,18,18)));
        button.setPreferredSize(new Dimension(28,28));button.setToolTipText(name);button.getAccessibleContext().setAccessibleName(name);
        button.setBorder(new EmptyBorder(5,5,5,5));button.setContentAreaFilled(false);
        button.setRolloverIcon(new ImageIcon(ImageUtil.resizeImage(ImageUtil.recolorImage(icon,Color.WHITE),18,18)));
        button.addActionListener(e->LinkBrowser.browse(url));return button;
    }
    void update(AssignmentDashboardState.View view){latest=view;if(active)render();}
    @Override public void onActivate(){active=true;render();}
    @Override public void onDeactivate(){active=false;}
    private void render()
    {
        if(latest==null)return;
        boolean completed=latest.assignment==null&&latest.completed!=null;
        AssignmentDashboardState.Assignment a=completed?latest.completed:latest.assignment;
        card.setVisible(a!=null);
        if(a!=null){
            cardHeading.setText(completed?"TASK COMPLETED":"CURRENT ASSIGNMENT");
            String type=a.classification.isEmpty()?"":a.classification.substring(0,1).toUpperCase()+a.classification.substring(1);
            String title=a.boss+(type.isEmpty()?"":" \u2022 "+type);
            identity.setText(wrap(html(title),181));identity.setToolTipText(title);
            progressLabel.setText(completed?"Completed":"Progress");progress.setText(a.progress+" / "+a.required+" KC");
            reward.setText(a.reward==null?"Unavailable":String.format(java.util.Locale.ROOT,"%,d pts",a.reward)+(a.group?" each":""));
        }
        message.setVisible(a==null||completed);
        message.setText(wrap((a==null?"<b>CURRENT ASSIGNMENT</b><br><br>":"")+
            (completed||latest.connected?"You do not have a current Huntmaster assignment.":"Log in and connect to Huntmaster to view your assignment."),201));
        connection.setText(wrap("\u25cf "+html(latest.connection),201));
        connection.setForeground(latest.connected?new Color(109,190,126):MUTED);
        revalidate();repaint();
    }
}

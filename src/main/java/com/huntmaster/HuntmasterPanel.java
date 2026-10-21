package com.huntmaster;

import java.awt.*;
import java.util.Objects;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/** Presentation only: all numbers come from the immutable server-confirmed view. */
final class HuntmasterPanel extends PluginPanel
{
    static final String DISCORD_URL = "https://discord.gg/Bosscape";
    private static final Color ACCENT = new Color(224,160,64), MUTED = new Color(174,174,174), COMPLETE = new Color(109,190,126);
    private static final Font BODY = FontManager.getDefaultFont().deriveFont(12f), BOLD = FontManager.getDefaultBoldFont().deriveFont(12f);
    private final JPanel card = new JPanel();
    private final JLabel heading = centered("CURRENT HUNT",BOLD,MUTED);
    private final JLabel artwork = centered("",BODY,MUTED);
    private final JLabel identity = centered("",BOLD.deriveFont(19f),Color.WHITE);
    private final JLabel classification = centered("",BODY,MUTED), percent = centered("",BOLD,ACCENT);
    private final HuntBar progress = new HuntBar();
    private final JLabel remaining = centered("",BODY,MUTED), rewardLabel = centered("Hunt reward",BODY,MUTED);
    private final JLabel reward = centered("",BOLD.deriveFont(19f),ACCENT), message = centered("",BODY,MUTED);
    private final JLabel connection = centered("",BODY.deriveFont(11f),MUTED);
    private final JPanel rankCard = new JPanel();
    private final JLabel rankArtwork = centered("",BODY,MUTED), rankName = centered("",BOLD.deriveFont(15f),Color.WHITE);
    private final HuntBar rankBar = new HuntBar();
    private final JLabel rankTotal = centered("",BODY,MUTED), rankNext = centered("",BODY,MUTED);
    private final JLabel rankStatus = centered("",BODY.deriveFont(11f),MUTED);
    private AssignmentDashboardState.View latest;
    private AssignmentDashboardState.View rendered;
    private String artworkBoss;
    private boolean active;

    HuntmasterPanel()
    {
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        add(centered("HUNTMASTER",FontManager.getRunescapeBoldFont().deriveFont(25f),ACCENT));
        add(centered("B O S S C A P E",BODY.deriveFont(10f),MUTED));gap(12);
        card.setLayout(new BoxLayout(card,BoxLayout.Y_AXIS));card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(ColorScheme.MEDIUM_GRAY_COLOR),new EmptyBorder(10,9,12,9)));
        card.setAlignmentX(CENTER_ALIGNMENT);
        card.add(heading);space(card,10);card.add(artwork);space(card,8);
        card.add(identity);space(card,4);card.add(classification);space(card,15);
        card.add(percent);space(card,7);card.add(progress);space(card,7);card.add(remaining);
        space(card,16);card.add(rewardLabel);space(card,4);card.add(reward);
        add(card);gap(10);add(message);
        rankCard.setLayout(new BoxLayout(rankCard,BoxLayout.Y_AXIS));rankCard.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        rankCard.setBorder(BorderFactory.createCompoundBorder(new LineBorder(ColorScheme.MEDIUM_GRAY_COLOR),new EmptyBorder(10,9,12,9)));
        rankCard.setAlignmentX(CENTER_ALIGNMENT);
        rankCard.add(centered("YOUR RANK",BOLD,MUTED));space(rankCard,6);rankCard.add(rankArtwork);space(rankCard,5);
        rankCard.add(rankName);space(rankCard,8);rankCard.add(rankBar);space(rankCard,6);rankCard.add(rankTotal);space(rankCard,4);rankCard.add(rankNext);
        space(rankCard,4);rankCard.add(rankStatus);
        rankCard.setVisible(false);add(rankCard);gap(10);
        JButton discord = new JButton("Open Huntmaster in Discord");
        discord.setFont(BODY.deriveFont(11f));discord.setForeground(ACCENT);discord.setMargin(new Insets(7,3,7,3));
        discord.setAlignmentX(CENTER_ALIGNMENT);discord.setMaximumSize(new Dimension(Integer.MAX_VALUE,34));
        discord.setToolTipText("Manage your Hunt in Bosscape Discord");
        discord.addActionListener(e->LinkBrowser.browse(DISCORD_URL));add(discord);gap(17);
        add(centered("Boss hunts. Earn points.",BODY,MUTED));add(centered("Rise through the ranks.",BODY,MUTED));gap(9);
        JPanel links = new JPanel(new FlowLayout(FlowLayout.CENTER,12,0));links.setOpaque(false);
        links.add(link("Discord",DISCORD_URL));links.add(link("About","https://github.com/DxewRS/Huntmaster#readme"));
        links.setMaximumSize(new Dimension(Integer.MAX_VALUE,25));add(links);gap(10);add(connection);
    }
    private static JLabel centered(String text,Font font,Color color)
    {
        JLabel label = new JLabel(text,SwingConstants.CENTER);label.setFont(font);label.setForeground(color);
        label.setAlignmentX(CENTER_ALIGNMENT);label.setMaximumSize(new Dimension(Integer.MAX_VALUE,Integer.MAX_VALUE));return label;
    }
    private static JButton link(String text,String url)
    {
        JButton button = new JButton(text);button.setFont(BODY.deriveFont(11f));button.setForeground(MUTED);
        button.setBorder(new EmptyBorder(2,0,2,0));button.setContentAreaFilled(false);
        button.addActionListener(e->LinkBrowser.browse(url));return button;
    }
    private void gap(int height){space(this,height);}
    private static void space(JPanel panel,int height){panel.add(Box.createVerticalStrut(height));}
    private static String html(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private static String wrap(String text,int width)
    {return "<html><table width='"+width+"' cellpadding='0' cellspacing='0'><tr><td align='center'>"+html(text)+"</td></tr></table></html>";}
    void update(AssignmentDashboardState.View view){latest=view;if(active)render();}
    @Override public void onActivate(){active=true;render();}
    @Override public void onDeactivate(){active=false;}
    private void render()
    {
        if(latest==null||sameDisplay(rendered,latest))return;
        boolean completed=latest.assignment==null&&latest.completed!=null;
        AssignmentDashboardState.Assignment task=completed?latest.completed:latest.assignment;
        card.setVisible(task!=null);
        if(task!=null)
        {
            heading.setText(completed?"HUNT COMPLETE":"CURRENT HUNT");heading.setForeground(completed?COMPLETE:MUTED);
            if(!task.boss.equals(artworkBoss))
            {artworkBoss=task.boss;artwork.setIcon(BossArtwork.icon(task.boss));artwork.setVisible(artwork.getIcon()!=null);}
            identity.setText(wrap(task.boss,175));identity.setToolTipText(task.boss);
            String type=task.classification;
            classification.setText(wrap(type.isEmpty()?(task.group?"Group":"Solo"):Character.toUpperCase(type.charAt(0))+type.substring(1),175));
            int shown=Math.min(task.progress,task.required), percentage=(int)((long)shown*100/task.required);
            percent.setText((completed?"COMPLETED":"HUNT PROGRESS")+"   "+percentage+"%");percent.setForeground(completed?COMPLETE:ACCENT);
            progress.setMaximum(task.required);progress.setValue(shown);progress.setString(task.progress+" / "+task.required+" KC");
            progress.setForeground(completed?COMPLETE:ACCENT);
            int left=Math.max(0,task.required-task.progress);
            remaining.setText(completed?"Hunt completed":left+(left==1?" kill remaining":" kills remaining"));
            rewardLabel.setText(completed?"Points awarded":"Hunt reward");
            reward.setText(wrap(task.reward==null?"Unavailable":String.format(java.util.Locale.ROOT,"%,d points",task.reward)+(task.group?" each":""),175));
        }
        message.setVisible(task==null||completed);
        message.setText(wrap(completed?"Request your next Hunt in Discord.":latest.connected?"You do not have a current Hunt. Request one in Discord.":"Log in and connect to Huntmaster to view your Current Hunt.",201));
        RankProgress rank=latest.rank;rankCard.setVisible(rank!=null||latest.rankMessage!=null);
        rankArtwork.setVisible(rank!=null);rankBar.setVisible(rank!=null);rankTotal.setVisible(rank!=null);rankNext.setVisible(rank!=null);
        if(rank!=null)
        {
            rankArtwork.setIcon(RankArtwork.icon(rank.icon));rankArtwork.setVisible(rankArtwork.getIcon()!=null);
            rankName.setText(wrap(rank.name,175));
            rankBar.setMaximum(10000);rankBar.setValue(rank.nextName==null?10000:(int)Math.max(0,Math.min(10000,(rank.points-rank.floor)*10000/(rank.ceiling-rank.floor))));
            rankBar.setString(rank.percentage()+"%");rankBar.setForeground(rank.nextName==null?COMPLETE:ACCENT);
            rankTotal.setText(wrap(rank.total(),175));rankNext.setText(wrap(rank.remaining(),175));
        }
        else rankName.setText(wrap(latest.rankMessage==null?"":latest.rankMessage,175));
        rankStatus.setVisible(rank!=null&&latest.rankMessage!=null&&!latest.rankMessage.isEmpty());
        rankStatus.setText(wrap(latest.rankMessage==null?"":latest.rankMessage,175));
        connection.setText(wrap(latest.connection,201));connection.setForeground(latest.connected?COMPLETE:MUTED);
        rendered=latest;revalidate();repaint();
    }

    /** Poll sequence changes alone do not require rebuilding Swing labels and layout. */
    private static boolean sameDisplay(AssignmentDashboardState.View a,AssignmentDashboardState.View b)
    {
        return a==b || a!=null&&b!=null&&a.connected==b.connected
            && Objects.equals(a.connection,b.connection)&&Objects.equals(a.rankMessage,b.rankMessage)
            && sameAssignment(a.assignment,b.assignment)&&sameAssignment(a.completed,b.completed)&&sameRank(a.rank,b.rank);
    }
    private static boolean sameAssignment(AssignmentDashboardState.Assignment a,AssignmentDashboardState.Assignment b)
    {
        return a==b || a!=null&&b!=null&&a.progress==b.progress&&a.required==b.required&&a.group==b.group
            && Objects.equals(a.id,b.id)&&Objects.equals(a.boss,b.boss)
            && Objects.equals(a.classification,b.classification)&&Objects.equals(a.reward,b.reward);
    }
    private static boolean sameRank(RankProgress a,RankProgress b)
    {
        return a==b || a!=null&&b!=null&&a.points==b.points&&a.floor==b.floor&&a.ceiling==b.ceiling
            && Objects.equals(a.name,b.name)&&Objects.equals(a.icon,b.icon)&&Objects.equals(a.nextName,b.nextName);
    }
    /** Native accessible progress model, with a predictable flat rendering in RuneLite's theme. */
    private static final class HuntBar extends JProgressBar
    {
        HuntBar(){setFont(BOLD);setStringPainted(true);setAlignmentX(CENTER_ALIGNMENT);setPreferredSize(new Dimension(180,25));setMaximumSize(new Dimension(Integer.MAX_VALUE,25));setForeground(ACCENT);setOpaque(false);}
        @Override protected void paintComponent(Graphics graphics)
        {
            Graphics2D g=(Graphics2D)graphics.create();
            try
            {
                g.setColor(ColorScheme.DARK_GRAY_COLOR);g.fillRect(0,0,getWidth(),getHeight());
                g.setColor(getForeground());g.fillRect(2,2,(int)((getWidth()-4)*getPercentComplete()),getHeight()-4);
                g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);g.drawRect(0,0,getWidth()-1,getHeight()-1);
                g.setFont(getFont());FontMetrics metrics=g.getFontMetrics();String text=getString();
                int x=(getWidth()-metrics.stringWidth(text))/2,y=(getHeight()-metrics.getHeight())/2+metrics.getAscent();
                g.setColor(Color.BLACK);g.drawString(text,x+1,y+1);g.setColor(Color.WHITE);g.drawString(text,x,y);
            }finally{g.dispose();}
        }
    }
}

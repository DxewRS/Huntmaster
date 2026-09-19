package com.huntmaster;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("huntmaster")
public interface BosscapeSettings extends Config
{
    @ConfigItem(keyName = "bosscapeInformation", position = 0,
        name = "<html><table width='200' cellpadding='0' cellspacing='0'>"
            + "<tr><td><b>HUNTMASTER</b></td></tr>"
            + "<tr><td height='10'></td></tr>"
            + "<tr><td>Boss assignment and kill<br>"
            + "verification for Bosscape.</td></tr>"
            + "<tr><td height='12'></td></tr>"
            + "<tr><td>Shares your RSN, relevant account<br>"
            + "information, and boss encounter<br>"
            + "evidence with the Huntmaster<br>"
            + "Discord Bot.</td></tr>"
            + "<tr><td height='16'></td></tr>"
            + "<tr><td><font color='#ff981f'><b>JOIN BOSSCAPE</b></font><br>"
            + "discord.gg/Bosscape</td></tr>"
            + "<tr><td height='10'></td></tr>"
            + "</table></html>",
        description = "Boss assignment and kill verification for Bosscape. Join: https://discord.gg/Bosscape")
    default void bosscapeInformation() {}
}

package com.huntmaster;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("huntmaster")
public interface BosscapeSettings extends Config
{
    @ConfigSection(name = "Display", description = "Local Hunt overlay settings", position = 0)
    String display = "display";

    @ConfigItem(keyName="showAssignmentProgressOverlay",name="Show hunt overlay",
        description="Show server-confirmed Hunt progress. Local display only; sends no additional data.",section=display,position=0)
    default boolean showAssignmentProgressOverlay() { return true; }

    @ConfigItem(keyName="huntOverlayVisibility",name="Visibility",section=display,position=1,
        description="After credited kill: show after your first server-credited kill, hide after 20 minutes without credit. While Hunt active: show whenever a current Hunt is connected.")
    default HuntOverlayVisibility huntOverlayVisibility() { return HuntOverlayVisibility.AFTER_CREDITED_KILL; }

    @ConfigSection(name = "Discord LFG / Queue Alerts", description = "Chat alerts from Huntmaster's Discord LFG / Queue system", position = 1)
    String recruitment = "recruitment";

    @ConfigItem(keyName="enableRecruitmentAlerts",name="Enable recruitment alerts",section=recruitment,position=0,
        description="Receive chat alerts for selected activities. Turning this off keeps your selections and does not affect Hunt tracking.")
    default boolean enableRecruitmentAlerts() { return true; }

    @ConfigItem(keyName = "recruitmentInformation", position = 1,
        name = "<html><table width='190' cellpadding='0' cellspacing='0'><tr><td>"
            + "Receive in-game chat alerts when players recruit through the Huntmaster Discord bot's LFG / Queue system."
            + "<br><br>Select activities below. These choices do not affect Hunt tracking.</td></tr></table></html>",
        section = recruitment,
        description = "New Discord LFG / Queue recruitment pings only, including your own. Missed alerts are not replayed on login or reconnect.")
    default void recruitmentInformation() {}

    @ConfigSection(name = "About Huntmaster", description = "Huntmaster and Bosscape information", position = 10, closedByDefault = true)
    String about = "about";

    @ConfigItem(keyName="bosscapeInformation",name="<html>Boss Hunts and kill verification<br>for Bosscape.<br><br>Join Bosscape:<br>discord.gg/Bosscape</html>",
        description="Boss Hunts, rewards and groups through Huntmaster in Bosscape Discord.",section=about,position=0)
    default void bosscapeInformation() {}

    @ConfigItem(keyName="releaseInformation",name="<html>Version " + ReleaseInfo.VERSION
        + "<br>Build:<br>" + ReleaseInfo.BUILD + "</html>",
        description="Release identifiers to include when asking Huntmaster staff for help.",section=about,position=1)
    default void releaseInformation() {}

    @ConfigItem(keyName="dataSharingInformation",position=11,
        name="<html><table width='190' cellpadding='0' cellspacing='0'><tr><td>Shares your RSN, relevant account information and encounter evidence with the Huntmaster Discord bot.</td></tr></table></html>",
        description="The bot receives your IP address when contacted. Hunt tracking and membership checks use your RSN, account information and encounter evidence.")
    default void dataSharingInformation() {}

    @ConfigSection(name = "Raids", description = "Choose recruitment alerts", position = 2, closedByDefault = true)
    String raids = "raids";

    @ConfigItem(keyName = "recruitment_toa", name = "Tombs of Amascut",
        description = "Show chat alerts for Tombs of Amascut recruitment through Huntmaster's Discord LFG / Queue system.",
        section = raids, position = 3)
    default boolean notifyToa() { return false; }

    @ConfigItem(keyName = "recruitment_tob", name = "Theatre of Blood",
        description = "Show chat alerts for Theatre of Blood recruitment through Huntmaster's Discord LFG / Queue system.",
        section = raids, position = 2)
    default boolean notifyTob() { return false; }

    @ConfigItem(keyName = "recruitment_cox", name = "Chambers of Xeric",
        description = "Show chat alerts for Chambers of Xeric recruitment through Huntmaster's Discord LFG / Queue system.",
        section = raids, position = 0)
    default boolean notifyCox() { return false; }

    @ConfigItem(keyName = "recruitment_tfa", name = "The Fractured Archive",
        description = "Show chat alerts for The Fractured Archive recruitment through Huntmaster's Discord LFG / Queue system.",
        section = raids, position = 1)
    default boolean notifyTfa() { return false; }

    @ConfigSection(name = "Wilderness Bosses", description = "Choose recruitment alerts", position = 3, closedByDefault = true)
    String wilderness = "wilderness";

    @ConfigItem(keyName = "recruitment_callisto", name = "Callisto",
        description = "Show chat alerts for Callisto recruitment through Huntmaster's Discord LFG / Queue system.",
        section = wilderness, position = 0)
    default boolean notifyCallisto() { return false; }

    @ConfigItem(keyName = "recruitment_vetion", name = "Vet'ion",
        description = "Show chat alerts for Vet'ion recruitment through Huntmaster's Discord LFG / Queue system.",
        section = wilderness, position = 5)
    default boolean notifyVetion() { return false; }

    @ConfigItem(keyName = "recruitment_venenatis", name = "Venenatis",
        description = "Show chat alerts for Venenatis recruitment through Huntmaster's Discord LFG / Queue system.",
        section = wilderness, position = 4)
    default boolean notifyVenenatis() { return false; }

    @ConfigItem(keyName = "recruitment_chaos-elemental", name = "Chaos Elemental",
        description = "Show chat alerts for Chaos Elemental recruitment through Huntmaster's Discord LFG / Queue system.",
        section = wilderness, position = 1)
    default boolean notifyChaosElemental() { return false; }

    @ConfigItem(keyName = "recruitment_scorpia", name = "Scorpia",
        description = "Show chat alerts for Scorpia recruitment through Huntmaster's Discord LFG / Queue system.",
        section = wilderness, position = 3)
    default boolean notifyScorpia() { return false; }

    @ConfigItem(keyName = "recruitment_kbd", name = "King Black Dragon",
        description = "Show chat alerts for King Black Dragon recruitment through Huntmaster's Discord LFG / Queue system.",
        section = wilderness, position = 2)
    default boolean notifyKbd() { return false; }

    @ConfigSection(name = "God Wars Dungeon", description = "Choose recruitment alerts", position = 4, closedByDefault = true)
    String godwars = "godwars";

    @ConfigItem(keyName = "recruitment_nex", name = "Nex",
        description = "Show chat alerts for Nex recruitment through Huntmaster's Discord LFG / Queue system.",
        section = godwars, position = 4)
    default boolean notifyNex() { return false; }

    @ConfigItem(keyName = "recruitment_graardor", name = "General Graardor",
        description = "Show chat alerts for General Graardor recruitment through Huntmaster's Discord LFG / Queue system.",
        section = godwars, position = 1)
    default boolean notifyGraardor() { return false; }

    @ConfigItem(keyName = "recruitment_zilyana", name = "Commander Zilyana",
        description = "Show chat alerts for Commander Zilyana recruitment through Huntmaster's Discord LFG / Queue system.",
        section = godwars, position = 0)
    default boolean notifyZilyana() { return false; }

    @ConfigItem(keyName = "recruitment_kreearra", name = "Kree'arra",
        description = "Show chat alerts for Kree'arra recruitment through Huntmaster's Discord LFG / Queue system.",
        section = godwars, position = 3)
    default boolean notifyKreearra() { return false; }

    @ConfigItem(keyName = "recruitment_kril", name = "K'ril Tsutsaroth",
        description = "Show chat alerts for K'ril Tsutsaroth recruitment through Huntmaster's Discord LFG / Queue system.",
        section = godwars, position = 2)
    default boolean notifyKril() { return false; }

    @ConfigSection(name = "Other Bosses", description = "Choose Discord LFG / Queue recruitment alerts", position = 5, closedByDefault = true)
    String bosses = "bosses";

    @ConfigItem(keyName = "recruitment_corp", name = "Corporeal Beast",
        description = "Show chat alerts for Corporeal Beast recruitment through Huntmaster's Discord LFG / Queue system.",
        section = bosses, position = 0)
    default boolean notifyCorp() { return false; }

    @ConfigItem(keyName = "recruitment_dagannoth-kings", name = "Dagannoth Kings",
        description = "Show chat alerts for Dagannoth Kings recruitment through Huntmaster's Discord LFG / Queue system.",
        section = bosses, position = 1)
    default boolean notifyDagannothKings() { return false; }

    @ConfigItem(keyName = "recruitment_mole", name = "Giant Mole",
        description = "Show chat alerts for Giant Mole recruitment through Huntmaster's Discord LFG / Queue system.",
        section = bosses, position = 3)
    default boolean notifyMole() { return false; }

    @ConfigItem(keyName = "recruitment_kalphite-queen", name = "Kalphite Queen",
        description = "Show chat alerts for Kalphite Queen recruitment through Huntmaster's Discord LFG / Queue system.",
        section = bosses, position = 4)
    default boolean notifyKalphiteQueen() { return false; }

    @ConfigItem(keyName = "recruitment_royal-titans", name = "Royal Titans",
        description = "Show chat alerts for Royal Titans recruitment through Huntmaster's Discord LFG / Queue system.",
        section = bosses, position = 5)
    default boolean notifyRoyalTitans() { return false; }

    @ConfigItem(keyName = "recruitment_sarachnis", name = "Sarachnis",
        description = "Show chat alerts for Sarachnis recruitment through Huntmaster's Discord LFG / Queue system.",
        section = bosses, position = 6)
    default boolean notifySarachnis() { return false; }

    @ConfigItem(keyName = "recruitment_scurrius", name = "Scurrius",
        description = "Show chat alerts for Scurrius recruitment through Huntmaster's Discord LFG / Queue system.",
        section = bosses, position = 7)
    default boolean notifyScurrius() { return false; }

    @ConfigItem(keyName = "recruitment_hueycoatl", name = "The Hueycoatl",
        description = "Show chat alerts for The Hueycoatl recruitment through Huntmaster's Discord LFG / Queue system.",
        section = bosses, position = 8)
    default boolean notifyHueycoatl() { return false; }

    @ConfigItem(keyName = "recruitment_nightmare", name = "The Nightmare",
        description = "Show chat alerts for The Nightmare recruitment through Huntmaster's Discord LFG / Queue system.",
        section = bosses, position = 9)
    default boolean notifyNightmare() { return false; }

    @ConfigItem(keyName = "recruitment_yama", name = "Yama",
        description = "Show chat alerts for Yama recruitment through Huntmaster's Discord LFG / Queue system.",
        section = bosses, position = 10)
    default boolean notifyYama() { return false; }

    @ConfigItem(keyName = "recruitment_gemstone-crab", name = "Gemstone Crab",
        description = "Show chat alerts for Gemstone Crab recruitment through Huntmaster's Discord LFG / Queue system.",
        section = bosses, position = 2)
    default boolean notifyGemstoneCrab() { return false; }

    @ConfigSection(name = "Skilling & Minigames", description = "Choose Discord LFG / Queue recruitment alerts", position = 6, closedByDefault = true)
    String activities = "activities";

    @ConfigItem(keyName = "recruitment_zalcano", name = "Zalcano",
        description = "Show chat alerts for Zalcano recruitment through Huntmaster's Discord LFG / Queue system.",
        section = activities, position = 5)
    default boolean notifyZalcano() { return false; }

    @ConfigItem(keyName = "recruitment_tempoross", name = "Tempoross",
        description = "Show chat alerts for Tempoross recruitment through Huntmaster's Discord LFG / Queue system.",
        section = activities, position = 3)
    default boolean notifyTempoross() { return false; }

    @ConfigItem(keyName = "recruitment_wintertodt", name = "Wintertodt",
        description = "Show chat alerts for Wintertodt recruitment through Huntmaster's Discord LFG / Queue system.",
        section = activities, position = 4)
    default boolean notifyWintertodt() { return false; }

    @ConfigItem(keyName = "recruitment_barbarian-assault", name = "Barbarian Assault",
        description = "Show chat alerts for Barbarian Assault recruitment through Huntmaster's Discord LFG / Queue system.",
        section = activities, position = 0)
    default boolean notifyBarbarianAssault() { return false; }

    @ConfigItem(keyName = "recruitment_soul-wars", name = "Soul Wars",
        description = "Show chat alerts for Soul Wars recruitment through Huntmaster's Discord LFG / Queue system.",
        section = activities, position = 2)
    default boolean notifySoulWars() { return false; }

    @ConfigItem(keyName = "recruitment_guardians-of-the-rift", name = "Guardians of the Rift",
        description = "Show chat alerts for Guardians of the Rift recruitment through Huntmaster's Discord LFG / Queue system.",
        section = activities, position = 1)
    default boolean notifyGuardiansOfTheRift() { return false; }
}

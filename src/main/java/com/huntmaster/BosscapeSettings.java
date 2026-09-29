package com.huntmaster;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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

    @ConfigItem(keyName = "recruitmentInformation", position = 1,
        name = "<html>GROUP RECRUITMENT ALERTS<br>Choose activities for in-game chat alerts.<br>Unchecked activities send no alerts.</html>",
        description = "New Bosscape recruitment pings only, including your own. No missed alerts on login.")
    default void recruitmentInformation() {}

    @ConfigSection(name = "Raids", description = "Choose recruitment alerts", position = 2, closedByDefault = true)
    String raids = "raids";

    @ConfigItem(keyName = "recruitment_toa", name = "Tombs of Amascut",
        description = "Show a chat message for new Tombs of Amascut recruitment pings.",
        section = raids, position = 0,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyToa() { return false; }

    @ConfigItem(keyName = "recruitment_tob", name = "Theatre of Blood",
        description = "Show a chat message for new Theatre of Blood recruitment pings.",
        section = raids, position = 1,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyTob() { return false; }

    @ConfigItem(keyName = "recruitment_cox", name = "Chambers of Xeric",
        description = "Show a chat message for new Chambers of Xeric recruitment pings.",
        section = raids, position = 2,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyCox() { return false; }

    @ConfigItem(keyName = "recruitment_tfa", name = "The Fractured Archive",
        description = "Planned: alerts begin when Bosscape adds this activity to its queue.",
        section = raids, position = 3,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyTfa() { return false; }

    @ConfigSection(name = "Wilderness Bosses", description = "Choose recruitment alerts", position = 3, closedByDefault = true)
    String wilderness = "wilderness";

    @ConfigItem(keyName = "recruitment_callisto", name = "Callisto",
        description = "Show a chat message for new Callisto recruitment pings.",
        section = wilderness, position = 0,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyCallisto() { return false; }

    @ConfigItem(keyName = "recruitment_vetion", name = "Vet'ion",
        description = "Show a chat message for new Vet'ion recruitment pings.",
        section = wilderness, position = 1,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyVetion() { return false; }

    @ConfigItem(keyName = "recruitment_venenatis", name = "Venenatis",
        description = "Show a chat message for new Venenatis recruitment pings.",
        section = wilderness, position = 2,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyVenenatis() { return false; }

    @ConfigItem(keyName = "recruitment_chaos-elemental", name = "Chaos Elemental",
        description = "Show a chat message for new Chaos Elemental recruitment pings.",
        section = wilderness, position = 3,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyChaosElemental() { return false; }

    @ConfigItem(keyName = "recruitment_scorpia", name = "Scorpia",
        description = "Show a chat message for new Scorpia recruitment pings.",
        section = wilderness, position = 4,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyScorpia() { return false; }

    @ConfigItem(keyName = "recruitment_kbd", name = "King Black Dragon",
        description = "Show a chat message for new King Black Dragon recruitment pings.",
        section = wilderness, position = 5,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyKbd() { return false; }

    @ConfigSection(name = "God Wars Dungeon", description = "Choose recruitment alerts", position = 4, closedByDefault = true)
    String godwars = "godwars";

    @ConfigItem(keyName = "recruitment_nex", name = "Nex",
        description = "Show a chat message for new Nex recruitment pings.",
        section = godwars, position = 0,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyNex() { return false; }

    @ConfigItem(keyName = "recruitment_graardor", name = "General Graardor",
        description = "Show a chat message for new General Graardor recruitment pings.",
        section = godwars, position = 1,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyGraardor() { return false; }

    @ConfigItem(keyName = "recruitment_zilyana", name = "Commander Zilyana",
        description = "Show a chat message for new Commander Zilyana recruitment pings.",
        section = godwars, position = 2,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyZilyana() { return false; }

    @ConfigItem(keyName = "recruitment_kreearra", name = "Kree'arra",
        description = "Show a chat message for new Kree'arra recruitment pings.",
        section = godwars, position = 3,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyKreearra() { return false; }

    @ConfigItem(keyName = "recruitment_kril", name = "K'ril Tsutsaroth",
        description = "Show a chat message for new K'ril Tsutsaroth recruitment pings.",
        section = godwars, position = 4,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyKril() { return false; }

    @ConfigSection(name = "Other Group Bosses", description = "Choose recruitment alerts", position = 5, closedByDefault = true)
    String bosses = "bosses";

    @ConfigItem(keyName = "recruitment_corp", name = "Corporeal Beast",
        description = "Show a chat message for new Corporeal Beast recruitment pings.",
        section = bosses, position = 0,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyCorp() { return false; }

    @ConfigItem(keyName = "recruitment_dagannoth-kings", name = "Dagannoth Kings",
        description = "Show a chat message for new Dagannoth Kings recruitment pings.",
        section = bosses, position = 1,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyDagannothKings() { return false; }

    @ConfigItem(keyName = "recruitment_mole", name = "Giant Mole",
        description = "Show a chat message for new Giant Mole recruitment pings.",
        section = bosses, position = 2,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyMole() { return false; }

    @ConfigItem(keyName = "recruitment_kalphite-queen", name = "Kalphite Queen",
        description = "Show a chat message for new Kalphite Queen recruitment pings.",
        section = bosses, position = 3,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyKalphiteQueen() { return false; }

    @ConfigItem(keyName = "recruitment_royal-titans", name = "Royal Titans",
        description = "Show a chat message for new Royal Titans recruitment pings.",
        section = bosses, position = 4,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyRoyalTitans() { return false; }

    @ConfigItem(keyName = "recruitment_sarachnis", name = "Sarachnis",
        description = "Show a chat message for new Sarachnis recruitment pings.",
        section = bosses, position = 5,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifySarachnis() { return false; }

    @ConfigItem(keyName = "recruitment_scurrius", name = "Scurrius",
        description = "Show a chat message for new Scurrius recruitment pings.",
        section = bosses, position = 6,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyScurrius() { return false; }

    @ConfigItem(keyName = "recruitment_hueycoatl", name = "The Hueycoatl",
        description = "Show a chat message for new The Hueycoatl recruitment pings.",
        section = bosses, position = 7,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyHueycoatl() { return false; }

    @ConfigItem(keyName = "recruitment_nightmare", name = "The Nightmare",
        description = "Show a chat message for new The Nightmare recruitment pings.",
        section = bosses, position = 8,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyNightmare() { return false; }

    @ConfigItem(keyName = "recruitment_yama", name = "Yama",
        description = "Show a chat message for new Yama recruitment pings.",
        section = bosses, position = 9,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyYama() { return false; }

    @ConfigItem(keyName = "recruitment_gemstone-crab", name = "Gemstone Crab",
        description = "Show a chat message for new Gemstone Crab recruitment pings.",
        section = bosses, position = 10,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyGemstoneCrab() { return false; }

    @ConfigSection(name = "Skilling Bosses & Activities", description = "Choose recruitment alerts", position = 6, closedByDefault = true)
    String activities = "activities";

    @ConfigItem(keyName = "recruitment_zalcano", name = "Zalcano",
        description = "Show a chat message for new Zalcano recruitment pings.",
        section = activities, position = 0,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyZalcano() { return false; }

    @ConfigItem(keyName = "recruitment_tempoross", name = "Tempoross",
        description = "Show a chat message for new Tempoross recruitment pings.",
        section = activities, position = 1,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyTempoross() { return false; }

    @ConfigItem(keyName = "recruitment_wintertodt", name = "Wintertodt",
        description = "Show a chat message for new Wintertodt recruitment pings.",
        section = activities, position = 2,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyWintertodt() { return false; }

    @ConfigItem(keyName = "recruitment_barbarian-assault", name = "Barbarian Assault",
        description = "Show a chat message for new Barbarian Assault recruitment pings.",
        section = activities, position = 3,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyBarbarianAssault() { return false; }

    @ConfigItem(keyName = "recruitment_soul-wars", name = "Soul Wars",
        description = "Show a chat message for new Soul Wars recruitment pings.",
        section = activities, position = 4,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifySoulWars() { return false; }

    @ConfigItem(keyName = "recruitment_guardians-of-the-rift", name = "Guardians of the Rift",
        description = "Show a chat message for new Guardians of the Rift recruitment pings.",
        section = activities, position = 5,
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers")
    default boolean notifyGuardiansOfTheRift() { return false; }
}

package com.huntmaster;

import com.google.gson.JsonObject;
import java.time.Instant;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;

/** Minimal own-account observations, sampled on the client thread. */
final class AccountSnapshot
{
	// Values documented by RuneLite's ACCOUNT_TYPE varbit (gameval IRONMAN).
	private static final String[] ACCOUNT_TYPES = {"NORMAL", "IRONMAN", "ULTIMATE_IRONMAN",
		"HARDCORE_IRONMAN", "GROUP_IRONMAN", "HARDCORE_GROUP_IRONMAN", "UNRANKED_GROUP_IRONMAN"};
	private static final java.util.Map<String, Quest> QUEST_BY_NAME = new java.util.HashMap<>();
	static
	{
		for (Quest quest : Quest.values()) QUEST_BY_NAME.putIfAbsent(quest.getName().toLowerCase(java.util.Locale.ROOT), quest);
	}
    private static final String[] QUESTS = {
        "Desert Treasure II - The Fallen Empire", "Regicide", "Dragon Slayer II", "Secrets of the North",
        "Priest in Peril", "The Heart of Darkness", "Song of the Elves", "The Final Dawn", "Perilous Moons",
        "The Ides of Milk", "Bone Voyage", "The Blood Moon Rises", "Fallen From Grace", "Death Plateau",
        "Horror from the Deep", "Children of the Sun", "A Kingdom Divided", "Troubled Tortugans", "Dragon Slayer I"
    };
    static JsonObject read(Client client)
    {
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", 1);
        result.addProperty("rsn", client.getLocalPlayer().getName());
        result.addProperty("observedAt", Instant.now().toString());
        JsonObject quests = new JsonObject();
        for (String name : QUESTS) observeQuest(client, quests, name, false);
        result.add("quests", quests);
        JsonObject miniquests = new JsonObject();
        observeQuest(client, miniquests, "The Frozen Door", false);
        observeQuest(client, miniquests, "Enter the Abyss", false);
        result.add("miniquests", miniquests);
        JsonObject started = new JsonObject();
        observeQuest(client, started, "Fairytale II - Cure a Queen", true);
        result.add("startedQuests", started);
        JsonObject access = new JsonObject();
        observeQuest(client, access, "His Faithful Servants", true);
        if (access.has("His Faithful Servants")) access.add("His Faithful Servants started", access.remove("His Faithful Servants"));
        result.add("access", access);
        JsonObject skills = new JsonObject();
        skills.addProperty("slayer", client.getRealSkillLevel(Skill.SLAYER));
        skills.addProperty("firemaking", client.getRealSkillLevel(Skill.FIREMAKING));
        skills.addProperty("fishing", client.getRealSkillLevel(Skill.FISHING));
        result.add("skills", skills);
        JsonObject account = new JsonObject();
        int accountType = client.getVarbitValue(net.runelite.api.gameval.VarbitID.IRONMAN);
        if (accountType >= 0 && accountType < ACCOUNT_TYPES.length) account.addProperty("type", ACCOUNT_TYPES[accountType]);
        result.add("account", account);
        JsonObject slayer = new JsonObject();
        int remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
        slayer.addProperty("killsRemaining", remaining);
        if (remaining > 0)
        {
            java.util.List<Integer> rows = client.getDBRowsByValue(net.runelite.api.gameval.DBTableID.SlayerTask.ID,
                net.runelite.api.gameval.DBTableID.SlayerTask.COL_ID, 0, client.getVarpValue(VarPlayerID.SLAYER_TARGET));
            if (rows != null && !rows.isEmpty())
            {
                String name = (String) client.getDBTableField(rows.get(0), net.runelite.api.gameval.DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)[0];
                if ("Bosses".equalsIgnoreCase(name))
                {
                    java.util.List<Integer> bossRows = client.getDBRowsByValue(net.runelite.api.gameval.DBTableID.SlayerTaskSublist.ID,
                        net.runelite.api.gameval.DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0,
                        client.getVarbitValue(net.runelite.api.gameval.VarbitID.SLAYER_TARGET_BOSSID));
                    name = null;
                    if (bossRows != null && !bossRows.isEmpty())
                    {
                        int row = (Integer) client.getDBTableField(bossRows.get(0), net.runelite.api.gameval.DBTableID.SlayerTaskSublist.COL_TASK, 0)[0];
                        name = (String) client.getDBTableField(row, net.runelite.api.gameval.DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)[0];
                    }
                }
                if (name != null) slayer.addProperty("task", name);
            }
        }
        result.add("slayer", slayer);
        return result;
    }
    private static void observeQuest(Client client, JsonObject destination, String name, boolean started)
    {
        Quest quest = QUEST_BY_NAME.get(name.toLowerCase(java.util.Locale.ROOT));
        if (quest == null) return;
        QuestState state = quest.getState(client);
        if (state != null) destination.addProperty(name, started ? state != QuestState.NOT_STARTED : state == QuestState.FINISHED);
    }
}

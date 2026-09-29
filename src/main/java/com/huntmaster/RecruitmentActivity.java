package com.huntmaster;

/** Stable queue IDs; preferences are local to RuneLite. */
enum RecruitmentActivity
{
    TOA("toa", "Tombs of Amascut"),
    TOB("tob", "Theatre of Blood"),
    COX("cox", "Chambers of Xeric"),
    TFA("tfa", "The Fractured Archive"),
    CALLISTO("callisto", "Callisto"),
    VETION("vetion", "Vet'ion"),
    VENENATIS("venenatis", "Venenatis"),
    CHAOS_ELEMENTAL("chaos-elemental", "Chaos Elemental"),
    SCORPIA("scorpia", "Scorpia"),
    KBD("kbd", "King Black Dragon"),
    NEX("nex", "Nex"),
    GRAARDOR("graardor", "General Graardor"),
    ZILYANA("zilyana", "Commander Zilyana"),
    KREEARRA("kreearra", "Kree'arra"),
    KRIL("kril", "K'ril Tsutsaroth"),
    CORP("corp", "Corporeal Beast"),
    DAGANNOTH_KINGS("dagannoth-kings", "Dagannoth Kings"),
    MOLE("mole", "Giant Mole"),
    KALPHITE_QUEEN("kalphite-queen", "Kalphite Queen"),
    ROYAL_TITANS("royal-titans", "Royal Titans"),
    SARACHNIS("sarachnis", "Sarachnis"),
    SCURRIUS("scurrius", "Scurrius"),
    HUEYCOATL("hueycoatl", "The Hueycoatl"),
    NIGHTMARE("nightmare", "The Nightmare"),
    YAMA("yama", "Yama"),
    GEMSTONE_CRAB("gemstone-crab", "Gemstone Crab"),
    ZALCANO("zalcano", "Zalcano"),
    TEMPOROSS("tempoross", "Tempoross"),
    WINTERTODT("wintertodt", "Wintertodt"),
    BARBARIAN_ASSAULT("barbarian-assault", "Barbarian Assault"),
    SOUL_WARS("soul-wars", "Soul Wars"),
    GUARDIANS_OF_THE_RIFT("guardians-of-the-rift", "Guardians of the Rift");
    final String id;
    final String displayName;
    RecruitmentActivity(String id, String displayName) { this.id = id; this.displayName = displayName; }
    String configKey() { return "recruitment_" + id; }
    static RecruitmentActivity fromId(String id)
    {
        for (RecruitmentActivity activity : values()) if (activity.id.equals(id)) return activity;
        return null;
    }
}

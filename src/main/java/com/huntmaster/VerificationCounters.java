package com.huntmaster;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

/** Fixed own-account kill totals. A rise is an observation, never a completion verdict. */
final class VerificationCounters
{
    /** TRACKING_BOSSES_KILLED: rises for a boss kill, and never says which boss. */
    static final int GLOBAL_KILLS = 4510;
    /** The bot ignores a boss rise this many ticks before its record opened. */
    static final int LEAD_TICKS = 4;
    /** Varp to the capture channels it may reach. This mirrors BOSS_VARPS in the bot. */
    private static final Map<Integer,Set<String>> CHANNELS = new HashMap<>();
    private static void one(String boss, int varp)
    {
        route(varp, boss);
    }
    private static void variants(String boss, int normal, int awakened)
    {
        route(normal, boss, boss + ":" + EncounterVariant.NORMAL.name());
        route(awakened, boss, boss + ":" + EncounterVariant.AWAKENED.name());
    }
    private static void route(int varp, String... channels)
    {
        Set<String> targets = CHANNELS.computeIfAbsent(varp, id -> new HashSet<>());
        Set<String> global = CHANNELS.computeIfAbsent(GLOBAL_KILLS, id -> new HashSet<>());
        for (String channel : channels) { targets.add(channel); global.add(channel); }
    }
    static
    {
        variants("Vardorvis", 3970, 3971);
        variants("Duke Sucellus", 3967, 3974);
        variants("The Leviathan", 3968, 3973);
        variants("The Whisperer", 3969, 3972);
        one("Zulrah", 1518);
        one("Vorkath", 1691);
        one("Phantom Muspah", 3752);
        one("Phosani's Nightmare", 2671);
        one("Amoxliatl", 4403);
        one("The Gauntlet", 2353);
        one("The Corrupted Gauntlet", 2354);
        one("Deranged Archaeologist", 1661);
        one("Maggot King", 5614);
        one("Mad Angel", 5712);
        one("Hespori", 2075);
        one("Skotizo", 1527);
        one("The Mimic", 2221);
        one("TzTok-Jad", 1522);
        one("TzKal-Zuk", 1585);
        one("Sol Heredit", 4187);
        one("General Graardor", 1504);
        one("Commander Zilyana", 1505);
        one("K'ril Tsutsaroth", 1506);
        one("Kree'arra", 1503);
        one("Corporeal Beast", 1517);
        one("Sarachnis", 2233);
        one("Giant Mole", 1515);
        one("Kalphite Queen", 1516);
        one("The Hueycoatl", 4404);
        one("The Nightmare", 2664);
        one("Yama", 4701);
        one("Scurrius", 4079);
        one("Wintertodt", 1528);
        one("Tempoross", 2934);
        one("Zalcano", 2352);
        one("Royal Titans", 4648);
        one("Nex", 3269);
        one("Abyssal Sire", 1526);
        one("Alchemical Hydra", 2074);
        one("Araxxor", 4260);
        one("Cerberus", 1525);
        one("Grotesque Guardians", 1669);
        one("Kraken", 1523);
        one("Thermonuclear Smoke Devil", 1524);
        one("Shellbane Gryphon", 4945);
        one("Artio", 3761);
        one("Callisto", 1510);
        one("Calvar'ion", 3763);
        one("Vet'ion", 1512);
        one("Spindel", 3762);
        one("Venenatis", 1511);
        one("Chaos Elemental", 1513);
        one("Chaos Fanatic", 1519);
        one("Crazy Archaeologist", 1521);
        one("King Black Dragon", 1514);
        one("Scorpia", 1520);
    }
    private static final int[] VARPS = CHANNELS.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();

    static boolean routes(String channel, int varp)
    {
        Set<String> targets = CHANNELS.get(varp);
        return targets != null && targets.contains(channel);
    }
    static Set<Integer> varps() { return java.util.Collections.unmodifiableSet(CHANNELS.keySet()); }

    static final class Change
    {
        final int varp, previous, current, tick;
        Change(int varp, int previous, int current, int tick)
        {
            this.varp=varp; this.previous=previous; this.current=current; this.tick=tick;
        }
    }

    private final Map<Integer,Integer> baselines=new HashMap<>();
    void reset() { baselines.clear(); }
    /** Only a rise from a known total is reported. The first read after a reset is its baseline. */
    void sample(IntUnaryOperator read, int tick, boolean collecting, Consumer<Change> sink)
    {
        for(int varp:VARPS)
        {
            int current=read.applyAsInt(varp);
            if(current<0) { baselines.remove(varp); continue; }
            Integer previous=baselines.put(varp,current);
            if(collecting && previous!=null && current>previous)
                sink.accept(new Change(varp,previous,current,tick));
        }
    }
}

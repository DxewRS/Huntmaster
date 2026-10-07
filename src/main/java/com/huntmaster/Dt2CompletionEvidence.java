package com.huntmaster;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarPlayerID;

/** Dedicated personal normal/awakened completion totals. Shared NPC IDs cannot identify the mode. */
final class Dt2CompletionEvidence
{
    static final String[] BOSSES = {"Duke Sucellus", "The Leviathan", "Vardorvis", "The Whisperer"};
    static final int[] NORMAL = {VarPlayerID.TOTAL_DUKE_SUCELLUS_KILLS, VarPlayerID.TOTAL_LEVIATHAN_KILLS,
        VarPlayerID.TOTAL_VARDORVIS_KILLS, VarPlayerID.TOTAL_WHISPERER_KILLS};
    static final int[] AWAKENED = {VarPlayerID.TOTAL_DUKE_SUCELLUS_AWAKENED_KILLS,
        VarPlayerID.TOTAL_LEVIATHAN_AWAKENED_KILLS, VarPlayerID.TOTAL_VARDORVIS_AWAKENED_KILLS,
        VarPlayerID.TOTAL_WHISPERER_AWAKENED_KILLS};
    static final class Completion
    {
        final String boss;
        final EncounterVariant variant;
        final int previous, current;
        Completion(String boss, EncounterVariant variant, int previous, int current)
        { this.boss= boss; this.variant=variant; this.previous=previous; this.current=current; }
    }
    private final Map<Integer, Integer> baselines = new HashMap<>();
    void reset() { baselines.clear(); }
    void sample(IntUnaryOperator read, boolean collecting, Consumer<Completion> sink)
    {
        for (int i=0; i<BOSSES.length; i++)
        {
            Completion normal=sample(BOSSES[i], EncounterVariant.NORMAL, NORMAL[i], read, collecting);
            Completion awakened=sample(BOSSES[i], EncounterVariant.AWAKENED, AWAKENED[i], read, collecting);
            // A simultaneous aggregate/awakened update is not evidence of a second normal kill.
            if(awakened!=null)sink.accept(awakened);
            else if(normal!=null)sink.accept(normal);
        }
    }
    private Completion sample(String boss, EncounterVariant variant, int varp, IntUnaryOperator read,
        boolean collecting)
    {
        int total=read.applyAsInt(varp);
        if (total<0) { baselines.remove(varp); return null; }
        Integer previous=baselines.put(varp,total);
        // Login synchronization, unchanged totals and decreases are not completions.
        // Preserve jumps as raw observations; only the bot may decide their meaning.
        if (collecting && previous!=null && total>previous)
            return new Completion(boss,variant,previous,total);
        return null;
    }
}

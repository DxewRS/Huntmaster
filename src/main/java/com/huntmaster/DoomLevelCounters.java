package com.huntmaster;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarPlayerID;

/** Fixed personal counter reads. A change is an observation, never a completion verdict. */
final class DoomLevelCounters
{
    enum Source
    {
        DOM_LEVEL_1_COMPLETIONS(VarPlayerID.DOM_LEVEL_1_COMPLETIONS, 1),
        DOM_LEVEL_2_COMPLETIONS(VarPlayerID.DOM_LEVEL_2_COMPLETIONS, 2),
        DOM_LEVEL_3_COMPLETIONS(VarPlayerID.DOM_LEVEL_3_COMPLETIONS, 3),
        DOM_LEVEL_4_COMPLETIONS(VarPlayerID.DOM_LEVEL_4_COMPLETIONS, 4),
        DOM_LEVEL_5_COMPLETIONS(VarPlayerID.DOM_LEVEL_5_COMPLETIONS, 5),
        DOM_LEVEL_6_COMPLETIONS(VarPlayerID.DOM_LEVEL_6_COMPLETIONS, 6),
        DOM_LEVEL_7_COMPLETIONS(VarPlayerID.DOM_LEVEL_7_COMPLETIONS, 7),
        DOM_LEVEL_8_COMPLETIONS(VarPlayerID.DOM_LEVEL_8_COMPLETIONS, 8),
        DOM_LEVEL_8_PLUS_COMPLETIONS(VarPlayerID.DOM_LEVEL_8_PLUS_COMPLETIONS, 9);
        final int varp, bucket;
        Source(int varp, int bucket) { this.varp=varp; this.bucket=bucket; }
        boolean matches(int level) { return bucket==9 ? level>=9 : level==bucket; }
    }
    static final class Observation
    {
        final Source source;
        final int previous, current, tickOffset;
        Observation(Source source, int previous, int current, int offset)
        {
            if(source==null || previous<0 || current<0 || offset<0 || offset>=128)
                throw new IllegalArgumentException("Invalid Doom counter observation");
            this.source=source; this.previous=previous; this.current=current; this.tickOffset=offset;
        }
        Observation at(int offset) { return new Observation(source,previous,current,offset); }
    }
    private final Map<Source,Integer> baselines=new HashMap<>();
    void reset() { baselines.clear(); }
    void sample(IntUnaryOperator read, boolean collecting, Consumer<Observation> sink)
    {
        for(Source source:Source.values())
        {
            int current=read.applyAsInt(source.varp);
            if(current<0) { baselines.remove(source); continue; }
            Integer previous=baselines.put(source,current);
            if(collecting && previous!=null && current!=previous)
                sink.accept(new Observation(source,previous,current,0));
        }
    }
}

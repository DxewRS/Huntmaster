package com.huntmaster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class Dt2CompletionEvidenceTest
{
    @Parameterized.Parameters(name="{0}")
    public static Iterable<Object[]> bosses()
    { return Arrays.asList(new Object[][]{{0},{1},{2},{3}}); }
    private final int index;
    public Dt2CompletionEvidenceTest(int index) { this.index=index; }
    private final Map<Integer,Integer> values=new HashMap<>();
    private final List<Dt2CompletionEvidence.Completion> completions=new ArrayList<>();
    private final Dt2CompletionEvidence detector=new Dt2CompletionEvidence();
    private void sample(boolean collecting)
    { detector.sample(id->values.getOrDefault(id,10),collecting,completions::add); }
    private void verify(EncounterVariant variant,int varp)
    {
        sample(true);assertTrue(completions.isEmpty());values.put(varp,11);sample(true);
        assertEquals(1,completions.size());Dt2CompletionEvidence.Completion completion=completions.get(0);
        assertEquals(Dt2CompletionEvidence.BOSSES[index],completion.boss);
        assertEquals(variant,completion.variant);assertEquals(10,completion.previous);assertEquals(11,completion.current);
        sample(true);assertEquals(1,completions.size());
        List<EncounterObservation.Snapshot> reports=new ArrayList<>();EncounterCapture capture=new EncounterCapture(reports::add);
        BossDetector boss=new BossDetector(BossDefinition.betaCandidate(completion.boss,"fixture","fixture"));
        capture.variantCompletion(boss,"PrivateAccount",null,100,1000,variant,10,11);capture.advance(300);
        assertEquals(completion.boss,reports.get(0).boss);assertEquals(variant,reports.get(0).encounterVariant);
        assertEquals(variant.name(),EncounterReportCodec.encodeObservation(reports.get(0)).get("encounterVariant").getAsString());
    }
    @Test public void normalIsExplicitAndNeverAwakened() { verify(EncounterVariant.NORMAL,Dt2CompletionEvidence.NORMAL[index]); }
    @Test public void awakenedKeepsCanonicalBoss() { verify(EncounterVariant.AWAKENED,Dt2CompletionEvidence.AWAKENED[index]); }
    @Test public void resetPauseDecreaseAndNegativeNeverInventCompletions()
    {
        sample(true);values.put(Dt2CompletionEvidence.AWAKENED[index],11);sample(false);sample(true);assertTrue(completions.isEmpty());
        detector.reset();values.put(Dt2CompletionEvidence.AWAKENED[index],500);sample(true);assertTrue(completions.isEmpty());
        values.put(Dt2CompletionEvidence.AWAKENED[index],1);sample(true);assertTrue(completions.isEmpty());
        values.put(Dt2CompletionEvidence.AWAKENED[index],-1);sample(true);
        values.put(Dt2CompletionEvidence.AWAKENED[index],2);sample(true);assertTrue(completions.isEmpty());
    }
    @Test public void simultaneousTotalsDoNotCreateNormalKill()
    {
        sample(true);values.put(Dt2CompletionEvidence.NORMAL[index],11);values.put(Dt2CompletionEvidence.AWAKENED[index],11);sample(true);
        assertEquals(1,completions.size());assertEquals(EncounterVariant.AWAKENED,completions.get(0).variant);
    }
}

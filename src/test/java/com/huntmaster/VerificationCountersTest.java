package com.huntmaster;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class VerificationCountersTest
{
    private static final int GLOBAL=VerificationCounters.GLOBAL_KILLS,MAGGOT=5614,LEVIATHAN=3968,AWAKENED=3973;
    private final List<EncounterObservation.Snapshot> reports=new ArrayList<>();
    private final EncounterCapture capture=new EncounterCapture(reports::add);
    private final BossDetector maggot=new BossDetector(BossDefinition.betaCandidate("Maggot King","maggot","fixture"));
    private final BossDetector vorkath=new BossDetector(BossDefinition.betaCandidate("Vorkath","vorkath","fixture"));
    private final BossDetector leviathan=new BossDetector(BossDefinition.betaCandidate("The Leviathan","leviathan","fixture"));
    private final String assignment=UUID.randomUUID().toString();
    private void death(BossDetector boss,int tick) { capture.primary(boss,"PrivateFixture",assignment,tick,100000L+tick*600,EncounterObservation.SignalKind.DEATH); }
    private void rise(int varp,int previous,int current,int tick) { capture.counterChange(new VerificationCounters.Change(varp,previous,current,tick)); }
    private JsonArray changes(EncounterObservation.Snapshot report)
    { return EncounterReportCodec.encodeObservation(report).getAsJsonObject("bossCounters").getAsJsonArray("changes"); }

    @Test public void firstReadIsABaselineAndOnlyARiseIsReported()
    {
        VerificationCounters counters=new VerificationCounters();Map<Integer,Integer> values=new HashMap<>();List<VerificationCounters.Change> seen=new ArrayList<>();
        counters.sample(id->values.getOrDefault(id,12),1,true,seen::add);assertTrue(seen.isEmpty());
        counters.sample(id->values.getOrDefault(id,12),2,true,seen::add);assertTrue(seen.isEmpty());
        values.put(GLOBAL,13);counters.sample(id->values.getOrDefault(id,12),3,false,seen::add);assertTrue(seen.isEmpty());
        values.put(GLOBAL,10);counters.sample(id->values.getOrDefault(id,12),4,true,seen::add);assertTrue(seen.isEmpty());
        values.put(GLOBAL,11);counters.sample(id->values.getOrDefault(id,12),5,true,seen::add);
        assertEquals(1,seen.size());assertEquals(GLOBAL,seen.get(0).varp);assertEquals(10,seen.get(0).previous);assertEquals(11,seen.get(0).current);assertEquals(5,seen.get(0).tick);
        values.put(GLOBAL,-1);counters.sample(id->values.getOrDefault(id,12),6,true,seen::add);values.put(GLOBAL,99);counters.sample(id->values.getOrDefault(id,12),7,true,seen::add);assertEquals(1,seen.size());
        counters.reset();values.put(GLOBAL,500);counters.sample(id->values.getOrDefault(id,12),8,true,seen::add);assertEquals(1,seen.size());
    }
    @Test public void readsOnlyTheFixedSet()
    {
        List<Integer> read=new ArrayList<>();new VerificationCounters().sample(id->{read.add(id);return 1;},1,true,c->{});
        assertEquals(VerificationCounters.varps().size(),read.size());assertTrue(read.containsAll(Arrays.asList(GLOBAL,MAGGOT,LEVIATHAN,AWAKENED)));
        for(int excluded:new int[]{5407,5408,1507,1508,1509})assertFalse(read.contains(excluded));
    }
    @Test public void everyMappedBossIsASupportedEncounter()
    {
        int mapped=0;for(String name:SupportedEncounters.NAMES)if(VerificationCounters.routes(name,GLOBAL))mapped++;
        assertEquals(56,mapped);
    }
    @Test public void riseJoinsTheOpenRecordWithoutChangingIt()
    {
        death(maggot,100);rise(GLOBAL,14,15,106);rise(MAGGOT,20,21,106);capture.advance(300);
        assertEquals(1,reports.size());EncounterObservation.Snapshot r=reports.get(0);
        assertEquals(1,r.signals.size());assertEquals(2,r.counterChanges.size());
        JsonObject first=changes(r).get(1).getAsJsonObject();
        assertEquals(MAGGOT,first.get("varp").getAsInt());assertEquals(20,first.get("previous").getAsInt());assertEquals(21,first.get("current").getAsInt());assertEquals(6,first.get("tickOffset").getAsInt());
    }
    @Test public void riseNeverOpensARecordOrReachesAnotherBoss()
    {
        rise(GLOBAL,14,15,100);rise(MAGGOT,20,21,100);capture.advance(300);assertTrue(reports.isEmpty());
        death(vorkath,400);rise(MAGGOT,21,22,402);capture.advance(600);
        assertEquals(1,reports.size());assertTrue(reports.get(0).counterChanges.isEmpty());
        assertFalse(EncounterReportCodec.encodeObservation(reports.get(0)).has("bossCounters"));
    }
    @Test public void aRecordOpeningJustAfterTheRiseStillCarriesIt()
    {
        rise(GLOBAL,14,15,100);rise(MAGGOT,20,21,100);capture.loot(maggot,"PrivateFixture",assignment,104,1000);capture.advance(300);
        assertEquals(1,reports.size());assertEquals(2,reports.get(0).counterChanges.size());assertEquals(-4,reports.get(0).counterChanges.get(0).tickOffset);
    }
    @Test public void anOldRiseIsNotBorrowedByALaterRecord()
    {
        rise(GLOBAL,14,15,100);rise(MAGGOT,20,21,100);death(maggot,105);capture.advance(300);
        assertTrue(reports.get(0).counterChanges.isEmpty());
    }
    @Test public void consecutiveKillsKeepTheirOwnRises()
    {
        death(maggot,100);rise(GLOBAL,14,15,106);rise(MAGGOT,20,21,106);
        death(maggot,108);rise(GLOBAL,15,16,114);rise(MAGGOT,21,22,114);capture.advance(300);
        assertEquals(2,reports.size());
        for(int i=0;i<2;i++)
        {
            assertEquals(2,reports.get(i).counterChanges.size());
            assertEquals(15+i,reports.get(i).counterChanges.get(0).current);assertEquals(6,reports.get(i).counterChanges.get(0).tickOffset);
        }
    }
    @Test public void dt2RisesFollowTheirVariant()
    {
        capture.primary(leviathan,"PrivateFixture",assignment,100,1000,EncounterObservation.SignalKind.DEATH);
        capture.variantCompletion(leviathan,"PrivateFixture",assignment,106,4600,EncounterVariant.NORMAL,50,51);
        rise(GLOBAL,14,15,106);rise(LEVIATHAN,50,51,106);capture.advance(300);
        assertEquals(2,reports.size());for(EncounterObservation.Snapshot r:reports)assertEquals(2,r.counterChanges.size());
        reports.clear();capture.variantCompletion(leviathan,"PrivateFixture",assignment,400,4600,EncounterVariant.NORMAL,51,52);
        rise(AWAKENED,3,4,402);rise(GLOBAL,15,16,402);capture.advance(600);
        assertEquals(1,reports.get(0).counterChanges.size());assertEquals(GLOBAL,reports.get(0).counterChanges.get(0).varp);
    }
    @Test public void interruptedAndClearedCapturesForgetPendingRises()
    {
        rise(GLOBAL,14,15,100);capture.interrupt(101,EncounterObservation.Reason.LOGOUT);death(maggot,102);
        rise(GLOBAL,15,16,103);capture.clearCounterChanges();death(vorkath,104);
        rise(GLOBAL,16,17,105);capture.clear();capture.advance(300);
        assertTrue(reports.isEmpty());
        death(maggot,400);capture.interrupt(401,EncounterObservation.Reason.LOGOUT);rise(MAGGOT,1,2,402);capture.advance(600);
        assertTrue(reports.get(0).counterChanges.isEmpty());assertFalse(reports.get(0).captureComplete);
    }
    @Test public void recordsAreBoundedAndIdenticalChangesCountOnce()
    {
        death(maggot,100);
        rise(GLOBAL,1,2,101);rise(GLOBAL,1,2,101);
        for(int i=0;i<10;i++)rise(GLOBAL,10+i,11+i,102+i);
        capture.advance(300);assertEquals(EncounterObservation.MAX_COUNTER_CHANGES,reports.get(0).counterChanges.size());
    }
    @Test public void sentOnlyToABotThatAdvertisesTheContract()
    {
        death(maggot,100);rise(GLOBAL,14,15,106);rise(MAGGOT,20,21,106);capture.advance(300);EncounterObservation.Snapshot r=reports.get(0);
        assertFalse(EncounterReportCodec.encodeObservation(r,true,true,false).has("bossCounters"));
        JsonObject sent=EncounterReportCodec.encodeObservation(r,true,true,true).getAsJsonObject("bossCounters");
        assertEquals(1,sent.get("version").getAsInt());assertEquals(2,sent.getAsJsonArray("changes").size());
        CollectionPolicy policy=new CollectionPolicy();JsonObject p=new Gson().fromJson("{\"version\":2,\"expiresAt\":10000,\"windows\":{}}",JsonObject.class);
        policy.accept(p,100);assertFalse(policy.supportsVerificationCounters(101));
        p.addProperty("verificationCounterVersion",1);policy.accept(p,100);assertTrue(policy.supportsVerificationCounters(101));assertFalse(policy.supportsVerificationCounters(10000));
        p.addProperty("verificationCounterVersion",2);policy.accept(p,100);assertFalse(policy.supportsVerificationCounters(101));
        p.addProperty("verificationCounterVersion",1);policy.accept(p,100);policy.accept(null,100);assertFalse(policy.supportsVerificationCounters(101));
    }
}

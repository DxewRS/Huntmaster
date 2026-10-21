package com.huntmaster;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankDashboardStateTest
{
    private final String first=UUID.randomUUID().toString(),second=UUID.randomUUID().toString();
    private JsonObject response(String id,int kc,long sequence)
    {
        JsonObject json=new JsonObject();json.addProperty("success",true);json.addProperty("rsn","Example");json.addProperty("sequence",sequence);json.addProperty("status",id==null?"no_assignment":"solo");
        if(id==null)json.add("assignment",JsonNull.INSTANCE);
        else {JsonObject task=new JsonObject();task.addProperty("id",id);task.addProperty("boss","Vardorvis");task.addProperty("progress",kc);task.addProperty("requiredKC",16);task.addProperty("rewardPoints",267);json.add("assignment",task);}
        return json;
    }
    @Test public void rankRefreshIsEventDrivenAndDoesNotAwardPotentialReward()
    {
        AssignmentDashboardState state=new AssignmentDashboardState();assertFalse(state.needsRankRefresh());state.reset("Example",1000);assertTrue(state.needsRankRefresh());
        JsonObject login=response(first,5,1);login.add("rank",RankProgressTest.sample());state.accept(login,1100,null,0,true);
        assertFalse(state.needsRankRefresh());assertEquals(12000,state.view().rank.points,0);
        state.accept(response(first,6,2),1200,first,1150);assertFalse(state.needsRankRefresh());assertEquals(12000,state.view().rank.points,0);
        state.accept(response(second,0,3),1300,null,0);assertTrue(state.needsRankRefresh());
        JsonObject newHunt=response(second,0,4);newHunt.add("rank",RankProgressTest.sample());state.accept(newHunt,1400,null,0,true);assertFalse(state.needsRankRefresh());
        state.accept(response(null,0,5),1500,second,1450);assertTrue(state.needsRankRefresh());assertEquals(12000,state.view().rank.points,0);
        JsonObject paid=response(null,0,6);JsonObject rank=RankProgressTest.sample();rank.addProperty("points",12267);paid.add("rank",rank);state.accept(paid,1600,null,0,true);
        assertFalse(state.needsRankRefresh());assertEquals(12267,state.view().rank.points,0);
        state.accept(response(null,0,7),1700,null,0);assertFalse(state.needsRankRefresh());
        state.connection("Huntmaster unavailable");assertEquals(12267,state.view().rank.points,0);
        state.reset("Other",1800);assertNull(state.view().rank);assertTrue(state.needsRankRefresh());
    }
    @Test public void oldBotsAndMalformedMetadataNeverBreakTrackingOrLoopRequests()
    {
        for(boolean malformed:new boolean[]{false,true}){
            AssignmentDashboardState state=new AssignmentDashboardState();state.reset("Example",1000);JsonObject json=response(first,1,1);
            if(malformed)json.addProperty("rank","bad metadata");
            assertTrue(state.accept(json,1100,null,0,true));assertNotNull(state.view().assignment);assertNull(state.view().rank);assertFalse(state.needsRankRefresh());
            state.accept(response(first,2,2),1200,null,0);assertFalse(state.needsRankRefresh());
        }
    }
    @Test public void lateRankResponseCannotOverwriteNewerHuntOrClearRefresh()
    {
        AssignmentDashboardState state=new AssignmentDashboardState();state.reset("Example",1000);
        JsonObject login=response(first,5,1);login.add("rank",RankProgressTest.sample());state.accept(login,1100,null,0,true);
        state.accept(response(second,0,3),1300,null,0);assertTrue(state.needsRankRefresh());
        JsonObject stale=response(first,6,2);stale.add("rank",RankProgressTest.sample());
        assertFalse(state.accept(stale,1400,null,0,true));assertTrue(state.needsRankRefresh());assertEquals(second,state.view().assignment.id);
        state.reset(null,1500);assertNull(state.view().rank);assertFalse(state.needsRankRefresh());assertFalse(state.accept(login,1600,null,0,true));
    }
    @Test public void savedRankRestoresOnlyForSameAccountAndMissingMetadataDoesNotEraseIt()
    {
        AssignmentDashboardState original=new AssignmentDashboardState();original.reset("Example",1000);
        JsonObject login=response(first,5,1);login.add("rank",RankProgressTest.sample());original.accept(login,1100,null,0,true);
        JsonObject cache=original.rankCache();assertNotNull(cache);
        AssignmentDashboardState restored=new AssignmentDashboardState();restored.reset("Example",1200);
        assertTrue(restored.restoreRankCache(cache,1200));assertEquals(12000,restored.view().rank.points,0);assertEquals("Last confirmed rank",restored.view().rankMessage);assertTrue(restored.needsRankRefresh(1200));
        restored.accept(response(first,6,2),1300,null,0,true);assertEquals(12000,restored.view().rank.points,0);assertFalse(restored.needsRankRefresh(1301));assertTrue(restored.needsRankRefresh(1300+AssignmentDashboardState.RANK_RETRY_MS));
        JsonObject bad=response(first,7,3);bad.addProperty("rank","bad");restored.accept(bad,1400,null,0,true);assertEquals(12000,restored.view().rank.points,0);assertEquals(1100,restored.rankCache().get("confirmedAt").getAsLong());
        restored.reset("Other",1500);assertFalse(restored.restoreRankCache(cache,1500));assertNull(restored.view().rank);
        restored.reset("Example",1600);assertFalse(restored.restoreRankCache(cache,1000));assertFalse(restored.restoreRankCache(cache,1101+AssignmentDashboardState.RANK_CACHE_MAX_AGE_MS));
        restored.reset(null,1700);assertFalse(restored.restoreRankCache(cache,1700));assertNull(restored.rankCache());
    }
    @Test public void refreshAtEventsAndFiveMinuteFallbackUsesExistingPollCadence()
    {
        AssignmentDashboardState state=new AssignmentDashboardState();state.reset("Example",1000);
        assertTrue(state.needsRankRefresh(1000));JsonObject login=response(null,0,1);login.add("rank",RankProgressTest.sample());state.accept(login,1100,null,0,true);
        assertNotNull(state.view().rank);assertFalse(state.needsRankRefresh(1100+AssignmentDashboardState.RANK_REFRESH_MS-1));assertTrue(state.needsRankRefresh(1100+AssignmentDashboardState.RANK_REFRESH_MS));
        state.connection("Huntmaster unavailable");assertTrue(state.needsRankRefresh(1200));assertEquals("Last confirmed rank",state.view().rankMessage);
        assertEquals("Seasoned Hunter",state.view().rank.name);
    }
}

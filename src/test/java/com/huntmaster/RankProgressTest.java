package com.huntmaster;

import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankProgressTest
{
    static JsonObject rank(double points,String name,String icon,double floor,String nextName,double ceiling)
    {
        JsonObject rank=new JsonObject(),current=new JsonObject();rank.addProperty("version",1);rank.addProperty("points",points);
        current.addProperty("name",name);current.addProperty("icon",icon);current.addProperty("requiredPoints",floor);rank.add("currentRank",current);
        if(nextName==null)rank.add("nextRank",com.google.gson.JsonNull.INSTANCE);
        else {JsonObject next=new JsonObject();next.addProperty("name",nextName);next.addProperty("requiredPoints",ceiling);rank.add("nextRank",next);}
        return rank;
    }
    static JsonObject sample(){return rank(12000,"Seasoned Hunter","seasoned-hunter.png",7500,"Veteran Hunter",15000);}
    @Test public void progressUsesServerRankIntervalAndEarnedPoints()
    {
        RankProgress rank=new RankProgress(sample());assertEquals(60,rank.percentage());
        assertEquals("12,000 total points",rank.total());assertEquals("3,000 points to Veteran Hunter",rank.remaining());
        RankProgress fraction=new RankProgress(rank(999.5,"New Blood","new-blood.png",0,"Novice Hunter",1000));
        assertEquals("0.5 points to Novice Hunter",fraction.remaining());assertEquals(99,fraction.percentage());
        RankProgress maximum=new RankProgress(rank(600000,"Godly Hunter","godly-hunter.png",500000,null,0));
        assertEquals(100,maximum.percentage());assertEquals("Highest rank reached",maximum.remaining());
    }
    @Test public void invalidOptionalPointsAndThresholdsAreRejected()
    {
        for(double points:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,9007199254740992d,16000}){
            JsonObject rank=sample();rank.addProperty("points",points);try{new RankProgress(rank);fail("accepted "+points);}catch(IllegalArgumentException expected){}
        }
        JsonObject rank=sample();rank.getAsJsonObject("currentRank").addProperty("requiredPoints",13000);
        try{new RankProgress(rank);fail();}catch(IllegalArgumentException expected){}
    }
}

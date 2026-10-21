package com.huntmaster;

import com.google.gson.JsonObject;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Optional server-owned rank thresholds; no local point awards or rank table. */
final class RankProgress
{
    final String name, icon, nextName;
    final double points, floor, ceiling;
    RankProgress(JsonObject json)
    {
        if (!"1".equals(json.get("version").getAsString())) throw new IllegalArgumentException("Unsupported rank");
        points=number(json,"points");
        JsonObject current=json.getAsJsonObject("currentRank");
        name=text(current,"name");icon=text(current,"icon");floor=number(current,"requiredPoints");
        if (floor>points) throw new IllegalArgumentException("Invalid current rank");
        if (json.get("nextRank").isJsonNull()) {nextName=null;ceiling=floor;}
        else
        {
            JsonObject next=json.getAsJsonObject("nextRank");
            nextName=text(next,"name");ceiling=number(next,"requiredPoints");
            if (ceiling<=points||ceiling<=floor) throw new IllegalArgumentException("Invalid next rank");
        }
    }
    int percentage(){return nextName==null?100:(int)Math.max(0,Math.min(100,(points-floor)*100/(ceiling-floor)));}
    String total(){return format(points)+" total points";}
    String remaining(){return nextName==null?"Highest rank reached":format(ceiling-points)+" points to "+nextName;}
    JsonObject json()
    {
        JsonObject result=new JsonObject(),current=new JsonObject();
        result.addProperty("version",1);result.addProperty("points",points);
        current.addProperty("name",name);current.addProperty("icon",icon);current.addProperty("requiredPoints",floor);result.add("currentRank",current);
        if(nextName==null)result.add("nextRank",com.google.gson.JsonNull.INSTANCE);
        else {JsonObject next=new JsonObject();next.addProperty("name",nextName);next.addProperty("requiredPoints",ceiling);result.add("nextRank",next);}
        return result;
    }
    private static String format(double value)
    {return new DecimalFormat("#,##0.######",DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);}
    private static double number(JsonObject json,String key)
    {
        double value=json.get(key).getAsDouble();
        if (!Double.isFinite(value)||value<0||value>9007199254740991d) throw new IllegalArgumentException("Invalid rank points");
        return value;
    }
    private static String text(JsonObject json,String key)
    {
        String value=json.get(key).getAsString();
        if(value.isBlank()||value.length()>64)throw new IllegalArgumentException("Invalid rank text");
        return value;
    }
}

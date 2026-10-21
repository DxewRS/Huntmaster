package com.huntmaster;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.UUID;

/** Server-owned Hunt progress and saved rank display. No local credit increments. */
final class AssignmentDashboardState
{
    static final long INACTIVITY_MS = 20 * 60 * 1000L;
    static final long RANK_REFRESH_MS = 5 * 60 * 1000L, RANK_RETRY_MS = 60 * 1000L, RANK_CACHE_MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000L;
    static final class Assignment
    {
        final String id, boss, classification, overlayText;
        final int progress, required;
        final Long reward;
        final boolean group;
        Assignment(JsonObject task, boolean group)
        { this(task,group,false); }
        Assignment(JsonObject task, boolean group, boolean completion)
        {
            id = UUID.fromString(task.get("id").getAsString()).toString();
            boss = task.get("boss").getAsString();
            if (boss.isBlank() || boss.length() > 128) throw new IllegalArgumentException("Invalid boss");
            progress = number(task, "progress"); required = number(task, "requiredKC");
            if (required < 1 || (!completion && progress > required)) throw new IllegalArgumentException("Invalid progress");
            reward = task.has("rewardPoints") && !task.get("rewardPoints").isJsonNull() ? (long) number(task,"rewardPoints") : null;
            classification = task.has("classification") ? task.get("classification").getAsString() : "";
            if (classification.length() > 40) throw new IllegalArgumentException("Invalid classification");
            this.group = group;
            overlayText = progress+"/"+required+" KC: "+boss;
        }
        private static int number(JsonObject task, String key)
        {
            String value = task.get(key).getAsString();
            if (!value.matches("[0-9]{1,9}")) throw new IllegalArgumentException("Invalid number");
            return Integer.parseInt(value);
        }
    }
    static final class View
    {
        final Assignment assignment, completed;
        final String connection;
        final boolean connected;
        final RankProgress rank;
        final String rankMessage;
        View(Assignment assignment, Assignment completed, String connection, boolean connected)
        { this(assignment,completed,connection,connected,null); }
        View(Assignment assignment, Assignment completed, String connection, boolean connected, RankProgress rank)
        { this(assignment,completed,connection,connected,rank,rank==null?null:""); }
        View(Assignment assignment, Assignment completed, String connection, boolean connected, RankProgress rank,String rankMessage)
        { this.assignment=assignment; this.completed=completed; this.connection=connection; this.connected=connected; this.rank=rank; this.rankMessage=rankMessage; }
    }
    private volatile View view = new View(null,null,"Log in to RuneScape",false);
    private long sequence, sessionStarted, lastCredit = -1, lastCreditedObservation = -1;
    private String rsn, completionCandidate;
    private boolean rankRefreshNeeded=true;
    private long rankConfirmedAt,nextRankRefreshAt;
    View view() { return view; }
    synchronized boolean needsRankRefresh(){return rsn!=null&&rankRefreshNeeded;}
    synchronized boolean needsRankRefresh(long now){return rsn!=null&&(rankRefreshNeeded||now>=nextRankRefreshAt);}
    synchronized void reset(String account, long now)
    { completionCandidate=null; rsn=account; sequence=0; sessionStarted=now; lastCredit=-1; lastCreditedObservation=-1; rankRefreshNeeded=true; rankConfirmedAt=0;nextRankRefreshAt=0;view=new View(null,null,account==null?"Log in to RuneScape":"Connecting...",false,null,account==null?null:"Loading your rank..."); }
    synchronized boolean restoreRankCache(JsonObject cache,long now)
    {
        if(rsn==null||view.rank!=null||cache==null)return false;
        try {
            if(!rsn.trim().equalsIgnoreCase(cache.get("rsn").getAsString().trim()))return false;
            long at=cache.get("confirmedAt").getAsLong();if(at<=0||at>now||now-at>RANK_CACHE_MAX_AGE_MS)return false;
            RankProgress rank=new RankProgress(cache.getAsJsonObject("rank"));rankConfirmedAt=at;
            view=new View(view.assignment,view.completed,view.connection,view.connected,rank,"Last confirmed rank");return true;
        }catch(RuntimeException ignored){return false;}
    }
    synchronized JsonObject rankCache()
    {
        if(rsn==null||view.rank==null||rankConfirmedAt<=0)return null;
        JsonObject cache=new JsonObject();cache.addProperty("rsn",rsn);cache.addProperty("confirmedAt",rankConfirmedAt);cache.add("rank",view.rank.json());return cache;
    }
    synchronized boolean accept(JsonObject response, long now, String creditedAssignment, long observedAt)
    { return accept(response,now,creditedAssignment,observedAt,false); }
    synchronized boolean accept(JsonObject response, long now, String creditedAssignment, long observedAt, boolean rankRequested)
    {
        if (rsn == null || !response.get("success").getAsBoolean() || !rsn.equalsIgnoreCase(response.get("rsn").getAsString())) return false;
        long next = response.has("sequence") ? response.get("sequence").getAsLong() : sequence+1;
        if (next < sequence)
        {
            // A newer poll can overtake an acknowledgement. Keep the newer view,
            // but still recognize that this session's own kill was credited.
            recognizeCredit(view.assignment,creditedAssignment,observedAt,now);
            return false;
        }
        String status=response.get("status").getAsString();
        if (!status.equals("solo") && !status.equals("group") && !status.equals("no_assignment")) return false;
        Assignment assignment = response.get("assignment").isJsonNull() ? null : new Assignment(response.getAsJsonObject("assignment"),status.equals("group"));
        boolean huntChanged=!Objects.equals(assignment==null?null:assignment.id,view.assignment==null?null:view.assignment.id);
        if (huntChanged) { lastCredit=-1; lastCreditedObservation=-1; rankRefreshNeeded=true; }
        recognizeCredit(assignment,creditedAssignment,observedAt,now);
        Assignment completed=assignment==null?view.completed:null;
        if(assignment==null && view.assignment!=null && response.has("completedAssignment") && !response.get("completedAssignment").isJsonNull())
        {
            try {
                Assignment confirmed=new Assignment(response.getAsJsonObject("completedAssignment"),false,true);
                if(confirmed.id.equals(completionCandidate) && confirmed.progress>=confirmed.required && confirmed.reward!=null)completed=confirmed;
            } catch(RuntimeException ignored) { /* Invalid optional metadata must not retain an active overlay. */ }
        }
        completionCandidate=assignment==null?null:assignment.id;
        RankProgress rank=view.rank;
        String rankMessage=huntChanged?"Updating rank...":view.rankMessage;
        if(rankRequested)
        {
            // Older bots omit this optional field. Keep saved data and retry at
            // a bounded interval on the existing poll, never a separate request.
            rankRefreshNeeded=false;
            nextRankRefreshAt=now+RANK_RETRY_MS;
            try {
                if(response.has("rank")&&!response.get("rank").isJsonNull()) {
                    rank=new RankProgress(response.getAsJsonObject("rank"));rankConfirmedAt=now;nextRankRefreshAt=now+RANK_REFRESH_MS;rankMessage="";
                } else rankMessage=rank==null?"Rank data unavailable":"Last confirmed rank";
            }
            catch(RuntimeException ignored){rankMessage=rank==null?"Rank data unavailable":"Last confirmed rank";}
        }
        sequence=next;
        view=new View(assignment,completed,"Connected to Huntmaster",true,rank,rankMessage);
        return true;
    }
    private void recognizeCredit(Assignment assignment,String creditedAssignment,long observedAt,long now)
    {
        if(assignment!=null && assignment.id.equals(creditedAssignment) && observedAt>=sessionStarted
            && observedAt<=now && observedAt>lastCreditedObservation)
        { lastCredit=now; lastCreditedObservation=observedAt; }
    }
    synchronized void connection(String message)
    { if (!view.connection.equals(message)) {if(view.connected)rankRefreshNeeded=true;view=new View(view.assignment,view.completed,message,false,view.rank,view.rank==null?view.rankMessage:"Last confirmed rank");} }
    synchronized void clearCompleted()
    { completionCandidate=null; view=new View(view.assignment,null,view.connection,view.connected,view.rank,view.rankMessage); }
    synchronized String overlay(long now, boolean enabled)
    { return overlay(now,enabled,HuntOverlayVisibility.AFTER_CREDITED_KILL); }
    synchronized String overlay(long now, boolean enabled, HuntOverlayVisibility visibility)
    {
        Assignment a=view.assignment;
        return enabled && view.connected && a!=null && a.progress<a.required
            && (visibility==HuntOverlayVisibility.WHILE_HUNT_ACTIVE || lastCredit>=0 && now>=lastCredit && now-lastCredit<INACTIVITY_MS)
            ? a.overlayText : null;
    }
}

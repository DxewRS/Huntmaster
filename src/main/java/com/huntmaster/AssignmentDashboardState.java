package com.huntmaster;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.UUID;

/** Shared immutable server view. No local progress increments or persisted visibility. */
final class AssignmentDashboardState
{
    static final long INACTIVITY_MS = 20 * 60 * 1000L;
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
        View(Assignment assignment, Assignment completed, String connection, boolean connected)
        { this.assignment=assignment; this.completed=completed; this.connection=connection; this.connected=connected; }
    }
    private volatile View view = new View(null,null,"Log in to RuneScape",false);
    private long sequence, sessionStarted, lastCredit = -1, lastCreditedObservation = -1;
    private String rsn, completionCandidate;
    View view() { return view; }
    synchronized void reset(String account, long now)
    { completionCandidate=null; rsn=account; sequence=0; sessionStarted=now; lastCredit=-1; lastCreditedObservation=-1; view=new View(null,null,account==null?"Log in to RuneScape":"Connecting...",false); }
    synchronized boolean accept(JsonObject response, long now, String creditedAssignment, long observedAt)
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
        if (!Objects.equals(assignment==null?null:assignment.id,view.assignment==null?null:view.assignment.id)) { lastCredit=-1; lastCreditedObservation=-1; }
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
        sequence=next;
        view=new View(assignment,completed,"Connected to Huntmaster",true);
        return true;
    }
    private void recognizeCredit(Assignment assignment,String creditedAssignment,long observedAt,long now)
    {
        if(assignment!=null && assignment.id.equals(creditedAssignment) && observedAt>=sessionStarted
            && observedAt<=now && observedAt>lastCreditedObservation)
        { lastCredit=now; lastCreditedObservation=observedAt; }
    }
    synchronized void connection(String message)
    { if (!view.connection.equals(message)) view=new View(view.assignment,view.completed,message,false); }
    synchronized void clearCompleted()
    { completionCandidate=null; view=new View(view.assignment,null,view.connection,view.connected); }
    synchronized String overlay(long now, boolean enabled)
    {
        Assignment a=view.assignment;
        return enabled && view.connected && a!=null && a.progress<a.required && lastCredit>=0 && now>=lastCredit && now-lastCredit<INACTIVITY_MS
            ? a.overlayText : null;
    }
}

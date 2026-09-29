package com.huntmaster;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.UUID;

/** Applies authenticated bot decisions; never derives a verdict from local signals. */
final class ServerVerification
{
    static final class Poll
    {
        final String rsn, assignment, session;
        Poll(String rsn, String assignment, String session) { this.rsn=rsn; this.assignment=assignment; this.session=session; }
    }
    private String session=UUID.randomUUID().toString(), context, lastNotice, notice;
    private long nextPoll;
    private Poll pending;
    private int failures;
    void reset() { session=UUID.randomUUID().toString(); context=null; pending=null; nextPoll=0; failures=0; lastNotice=null; notice=null; }
    void release() { pending=null; nextPoll=0; }
    boolean isCurrent(Poll poll) { return pending==poll; }
    Poll begin(String rsn, String assignment, long now)
    {
        String key=rsn+":"+assignment;
        if (!Objects.equals(context,key)) { reset(); context=key; }
        if (pending!=null || now<nextPoll) return null;
        nextPoll=now+10000;
        pending=new Poll(rsn,assignment,session);
        return pending;
    }
    boolean complete(Poll poll, JsonObject response, String currentAssignment)
    {
        if (poll!=pending) return false;
        pending=null;
        if (!Objects.equals(poll.assignment,currentAssignment) || response==null) return false;
        try
        {
            if (!response.get("success").getAsBoolean()) return false;
            String assignment=response.get("assignmentId").isJsonNull()?null:response.get("assignmentId").getAsString();
            if (!Objects.equals(assignment,poll.assignment)) return false;
            if (!response.get("failures").isJsonPrimitive() || !response.get("failures").getAsJsonPrimitive().isNumber()
                || !response.get("failures").getAsString().matches("[0-9]{1,6}")) return false;
            int count=response.get("failures").getAsInt();
            boolean paused=response.get("paused").getAsBoolean();
            if (count<0 || paused!=(count>=3)) return false;
            String id=response.get("noticeId").isJsonNull()?null:response.get("noticeId").getAsString();
            String message=response.get("message").isJsonNull()?null:response.get("message").getAsString();
            if (message!=null && (message.length()>500 || id==null || message.contains("<") || message.contains(">") || message.chars().anyMatch(c -> c < 32 || c == 127))) return false;
            failures=Math.min(3,count);
            notice=null;
            if (id!=null && !id.equals(lastNotice)) { lastNotice=id; notice=message; }
            return true;
        }
        catch (RuntimeException ex) { return false; }
    }
    int failures() { return failures; }
    String takeNotice() { String result=notice; notice=null; return result; }
}

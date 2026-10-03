package com.huntmaster;

import com.google.gson.JsonObject;

/** Session-only, allowlisted support information; never includes account names or request bodies. */
final class CollectorDiagnostics
{
    static final String BUILD = "collector-v2-diagnostics-1";
    private String id = "none", outcome = "No reports this session", reason = "none", reportPolicy="none";
    void reset() { id="none"; outcome="No reports this session"; reason="none";reportPolicy="none"; }
    void queued(String reportId) { queued(reportId,"packaged-default"); }
    void queued(String reportId,String revision) { id=java.util.UUID.fromString(reportId).toString(); outcome="Evidence queued"; reason="awaiting_delivery"; reportPolicy=token(revision); }
    void response(String reportId, boolean accepted, int http, JsonObject credit)
    {
        if (!id.equals(reportId)) return;
        if (!accepted) { outcome=http==400||http==409||http==413?"Evidence rejected by server":"Evidence not acknowledged"; reason="http_"+http; return; }
        String status=field(credit,"status");
        reason=credit!=null && credit.has("reason") ? field(credit,"reason") : status;
        outcome="progress_updated".equals(status)||"task_completed".equals(status) ? "Kill credited"
            : "unknown".equals(status) ? "Evidence received; credit unknown" : "Evidence received; no new credit";
    }
    private static String field(JsonObject object,String key)
    {
        try {
            String value=object.get(key).getAsString();
            return java.util.Arrays.asList("progress_updated","task_completed","pending_evidence","observation_only","no_assignment",
                "duplicate_counter","duplicate","boss_mismatch","assignment_invalidated","no_task","reports_blocked",
                "personal_counter_missing","capture_interrupted","full_clear_evidence_required","counter_or_encounter_support_insufficient",
                "supported_evidence","variant_evidence_required","tracking_mode_mismatch","credit_held").contains(value)?value:"unknown";
        } catch(RuntimeException ex){return "unknown";}
    }
    static String token(String value) { return value!=null && value.matches("[a-zA-Z0-9_.-]{1,80}") ? value : "unknown"; }
    String summary() { return outcome + ("none".equals(reason)?"":" ("+reason.replace('_',' ')+")"); }
    String copy(String state,int queued,String revision)
    {
        return "Huntmaster build: "+BUILD+"\nCollector: 2\nCollection: "+state+"\nQueued reports: "+queued
            +"\nCurrent policy: "+token(revision)+"\nLatest report: "+id+"\nReport policy: "+reportPolicy+"\nReport status: "+outcome+"\nReason: "+reason;
    }
}

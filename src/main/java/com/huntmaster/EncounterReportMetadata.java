package com.huntmaster;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Small, immutable delivery hints derived from the original payload. Never persisted or sent. */
final class EncounterReportMetadata
{
    private static final EncounterReportMetadata INVALID = new EncounterReportMetadata(null);
    final String rsn, route;
    final boolean readableAccount;
    private final boolean parsed, collector, doom, doomCounters, variant;

    private EncounterReportMetadata(JsonObject report)
    {
        parsed = report != null;
        collector = parsed && report.has("collectorVersion");
        doom = parsed && report.has("doomState");
        doomCounters = parsed && report.has("doomCounters");
        variant = parsed && report.has("encounterVariant");
        String account = null, deliveryRoute = null;
        boolean readable = false;
        if (parsed)
        {
            try { account = report.get("rsn").getAsString(); readable = true; }
            catch (RuntimeException ignored) { /* Preserve the existing malformed-entry handling. */ }
            try { deliveryRoute = EncounterReportCodec.deliveryRoute(report); }
            catch (RuntimeException ignored) { /* Invalid routing is quarantined during delivery. */ }
        }
        rsn = account; readableAccount = readable; route = deliveryRoute;
    }

    static EncounterReportMetadata read(String payload)
    {
        try { return new EncounterReportMetadata(new JsonParser().parse(payload).getAsJsonObject()); }
        catch (RuntimeException ignored) { return INVALID; }
    }

    boolean belongsTo(String account)
    {
        return readableAccount && account != null && rsn != null && account.trim().equalsIgnoreCase(rsn.trim());
    }

    boolean eligible(String account, CollectionPolicy policy, long now)
    {
        // Re-evaluate the current account/capabilities every time. Cached hints never grant credit.
        // Malformed accounts reach the same delivery quarantine, after the original capability gates.
        return !parsed || (!collector || policy.supported())
            && (!doom || policy.supportsDoomEvidence(now))
            && (!doomCounters || policy.supportsDoomCounters(now))
            && (!variant || policy.supportsEncounterVariants(now))
            && (!readableAccount || belongsTo(account));
    }
}

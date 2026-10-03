package com.huntmaster;
import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class CollectorDiagnosticsTest
{
    @Test public void previousAccountFinalizationCannotPopulateCurrentDiagnostics() throws Exception
    {
        ImmediateReportDeliveryTest.Harness h=new ImmediateReportDeliveryTest.Harness();
        ImmediateReportDeliveryTest.set(h.plugin,"assignmentRsn","Different");h.finalizeReport();
        CollectorDiagnostics d=(CollectorDiagnostics)ImmediateReportDeliveryTest.field("collectorDiagnostics").get(h.plugin);
        assertEquals("No reports this session",d.summary());
    }
    @Test public void receiptIsNotCreditAndOldCallbacksCannotReplaceLatestReport()
    {
        CollectorDiagnostics d=new CollectorDiagnostics();String first=java.util.UUID.randomUUID().toString(),second=java.util.UUID.randomUUID().toString();
        d.queued(first);d.response(first,true,200,null);assertTrue(d.summary().contains("credit unknown"));
        JsonObject decision=new Gson().fromJson("{\"status\":\"pending_evidence\",\"reason\":\"personal_counter_missing\"}",JsonObject.class);
        d.response(first,true,200,decision);assertTrue(d.summary().contains("no new credit"));
        decision.addProperty("status","progress_updated");d.response(first,true,200,decision);assertTrue(d.summary().startsWith("Kill credited"));
        d.queued(second);d.response(first,true,200,decision);assertTrue(d.summary().startsWith("Evidence queued"));
        d.reset();assertFalse(d.copy("active",0,"default-v2").contains(first));assertFalse(d.copy("active",0,"default-v2").contains(second));
    }
    @Test public void unknownOrMalformedServerTextCannotReachClipboard()
    {
        CollectorDiagnostics d=new CollectorDiagnostics();String id=java.util.UUID.randomUUID().toString();d.queued(id);
        JsonObject decision=new JsonObject();decision.addProperty("status","secret-player-token");decision.add("reason",new JsonObject());
        d.response(id,true,200,decision);assertFalse(d.copy("active",0,"default-v2").contains("secret-player-token"));
        assertTrue(d.copy("active",0,"default-v2").contains(CollectorDiagnostics.BUILD));
        d.response(id,false,503,null);assertTrue(d.summary().contains("not acknowledged"));
    }
    @Test public void futureRequirementShowsUpdateNotice()
    {
        CollectionPolicy p=new CollectionPolicy();p.accept(new Gson().fromJson("{\"version\":2,\"minimumVersion\":3}",JsonObject.class),100);
        assertTrue(p.updateRequired());assertFalse(p.supported());p.accept(null,100);assertFalse(p.updateRequired());
    }
    @Test public void reportUsesCaptureStartRevision()
    {
        java.util.List<EncounterObservation.Snapshot> rows=new java.util.ArrayList<>();EncounterCapture c=new EncounterCapture(rows::add);
        BossDetector d=new BossDetector(BossDefinition.betaCandidate("Nex","nex","Your Nex kill count is:"));
        c.revision("review-1");c.primary(d,"Example",null,100,1000,EncounterObservation.SignalKind.DEATH);
        c.revision("review-2");c.advance(300);
        JsonObject report=EncounterReportCodec.encodeObservation(rows.get(0));assertEquals("review-1",report.get("collectionPolicyRevision").getAsString());
        assertEquals(CollectorDiagnostics.BUILD,report.get("pluginBuild").getAsString());
    }
}

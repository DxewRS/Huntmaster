package com.huntmaster;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class CollectionPolicyTest
{
    @Test public void futureRequirementShowsUpdateNotice()
    {
        CollectionPolicy p=new CollectionPolicy();p.accept(new Gson().fromJson("{\"version\":2,\"minimumVersion\":3}",JsonObject.class),100);
        assertTrue(p.updateRequired());assertFalse(p.supported());p.accept(null,100);assertFalse(p.updateRequired());
    }
    private JsonObject policy(String windows) { return new Gson().fromJson("{\"version\":2,\"expiresAt\":10000,\"windows\":"+windows+"}",JsonObject.class); }
    @Test public void boundedKnownWindowsExpireAndMalformedValuesFallBack()
    {
        CollectionPolicy p=new CollectionPolicy(); p.accept(policy("{\"Nex\":80}"),100);
        assertTrue(p.supported());assertEquals(Integer.valueOf(80),p.windows(101).get("Nex"));
        assertTrue(p.windows(10000).isEmpty());
        for(String value:new String[]{"{\"Nex\":129}","{\"Nex\":9}","{\"Nex\":12.5}","{\"arbitrary varp\":40}"}){
            p.accept(policy(value),100);assertTrue(p.windows(101).isEmpty());
        }
        p.accept(null,100);assertFalse(p.supported());assertTrue(p.windows(101).isEmpty());
    }

    @Test public void futureBossEntriesDoNotDiscardKnownWindowsOrCapabilities()
    {
        CollectionPolicy p=new CollectionPolicy();JsonObject value=policy("{\"Nex\":80,\"Future boss\":{\"futureFormat\":true}}");
        value.addProperty("doomEvidenceVersion",1);value.addProperty("doomCounterEvidenceVersion",1);
        value.addProperty("encounterVariantVersion",1);value.addProperty("revision","future-compatible");
        p.accept(value,100);
        assertTrue(p.supported());assertEquals(1,p.windows(101).size());assertEquals(Integer.valueOf(80),p.windows(101).get("Nex"));
        assertFalse(p.windows(101).containsKey("Future boss"));assertTrue(p.supportsDoomCounters(101));
        assertTrue(p.supportsEncounterVariants(101));assertEquals("future-compatible",p.revision(101));
        assertTrue(p.windows(10000).isEmpty());assertFalse(p.supportsDoomCounters(10000));
    }

    @Test public void futureEntriesNeverRelaxKnownBoundsProtocolOrPayloadLimits()
    {
        CollectionPolicy p=new CollectionPolicy();
        p.accept(policy("{\"Future boss\":80,\"Nex\":129}"),100);assertTrue(p.windows(101).isEmpty());
        JsonObject tooMany=policy("{}");for(int i=0;i<65;i++)tooMany.getAsJsonObject("windows").addProperty("Future "+i,80);
        p.accept(tooMany,100);assertTrue(p.windows(101).isEmpty());
        JsonObject expired=policy("{\"Nex\":80}");expired.addProperty("expiresAt",100+86400001L);
        p.accept(expired,100);assertTrue(p.windows(101).isEmpty());
        JsonObject newer=policy("{\"Nex\":80}");newer.addProperty("minimumVersion",3);p.accept(newer,100);
        assertTrue(p.updateRequired());assertFalse(p.supported());assertTrue(p.windows(101).isEmpty());
        newer=policy("{\"Nex\":80}");newer.addProperty("version",3);p.accept(newer,100);assertFalse(p.supported());
    }
}

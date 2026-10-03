package com.huntmaster;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class CollectionPolicyTest
{
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
}

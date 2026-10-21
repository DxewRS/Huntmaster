package com.huntmaster;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncounterReportMetadataTest
{
    private final Gson gson = new Gson();

    private CollectionPolicy policy()
    {
        CollectionPolicy policy = new CollectionPolicy();
        policy.accept(gson.fromJson("{\"version\":2,\"expiresAt\":10000,\"windows\":{},"
            + "\"doomEvidenceVersion\":1,\"doomCounterEvidenceVersion\":1,\"encounterVariantVersion\":1}", JsonObject.class), 1000);
        return policy;
    }

    @Test public void originalPayloadAndLegacyObservationRoutesStayIntact()
    {
        EncounterReportQueue queue = new EncounterReportQueue();
        String payload = "{ \"rsn\": \" Player \", \"trackingMode\": \"server_observation\" }";
        assertTrue(queue.add("one", payload, 1000, 1000, true));
        EncounterReportQueue.Entry entry = queue.next(1000);
        assertEquals(payload, entry.payload);
        assertEquals("one", entry.id);
        assertTrue(entry.delivery.belongsTo("player"));
        assertFalse(entry.delivery.belongsTo("another"));
        assertEquals("/api/runelite/observations", entry.delivery.route);
        assertEquals("/api/runelite/encounter-reports",
            EncounterReportMetadata.read("{\"rsn\":\"Player\",\"trackingMode\":\"beta_candidate\"}").route);
    }

    @Test public void currentAccountCapabilitiesAndExpiryAreNeverCachedAsPermission()
    {
        EncounterReportQueue queue = new EncounterReportQueue();
        String requirements = ",\"collectorVersion\":2,\"doomState\":{},\"doomCounters\":{},\"encounterVariant\":{}";
        queue.add("other", "{\"rsn\":\"Other\"" + requirements + "}", 1000, 1000, true);
        queue.add("current", "{\"rsn\":\"Player\"" + requirements + "}", 1000, 1000, true);
        CollectionPolicy policy = new CollectionPolicy();
        assertNull(queue.next(1000, e -> e.delivery.eligible("Player", policy, 1000)));
        JsonObject advertised = gson.fromJson("{\"version\":2,\"expiresAt\":10000,\"windows\":{}}", JsonObject.class);
        policy.accept(advertised, 1000);
        for (String field : new String[]{"doomEvidenceVersion", "doomCounterEvidenceVersion", "encounterVariantVersion"})
        {
            assertNull(queue.next(1000, e -> e.delivery.eligible("Player", policy, 1000)));
            advertised.addProperty(field, 1); policy.accept(advertised, 1000);
        }
        EncounterReportQueue.Entry current = queue.next(1000, e -> e.delivery.eligible("Player", policy, 1000));
        assertEquals("current", current.id);
        EncounterReportMetadata cached = current.delivery;
        current.nextAttemptAt = 2000;
        assertNull(queue.next(1001, e -> e.delivery.eligible("Player", policy, 1001)));
        assertSame(current, queue.next(2000, e -> e.delivery.eligible("Player", policy, 2000)));
        assertSame(cached, current.delivery);
        assertEquals("other", queue.next(2000, e -> e.delivery.eligible("Other", policy, 2000)).id);
        assertNull(queue.next(10000, e -> e.delivery.eligible("Player", policy, 10000)));
        assertEquals(2, queue.snapshot().length);
    }

    @Test public void persistedEntriesRebuildHintsWithoutChangingIdsPayloadsOrRetention()
    {
        EncounterReportQueue queue = new EncounterReportQueue();
        String payload = "{\"rsn\":\"Player\",\"trackingMode\":\"server_observation\"}";
        queue.add("stable-id", payload, 1000, 1000, true);
        queue.next(1000).inFlight = true;
        String saved = gson.toJson(queue.snapshot());
        assertFalse(saved.contains("delivery")); assertFalse(saved.contains("inFlight"));
        EncounterReportQueue.Entry savedEntry = gson.fromJson(saved, EncounterReportQueue.Entry[].class)[0];
        assertNull(savedEntry.delivery);
        EncounterReportQueue restored = new EncounterReportQueue();
        long later = 1000 + 3 * EncounterReportQueue.RETENTION_MS;
        assertTrue(restored.add(savedEntry.id, savedEntry.payload, savedEntry.createdAt, later, savedEntry.creditCandidate));
        EncounterReportQueue.Entry entry = restored.next(later, e -> e.delivery.eligible("Player", new CollectionPolicy(), later));
        assertEquals("stable-id", entry.id); assertEquals(payload, entry.payload);
        assertEquals("/api/runelite/observations", entry.delivery.route);
        assertFalse(entry.inFlight);
    }

    @Test public void malformedFieldsKeepCapabilityGatesAndDeliveryQuarantine()
    {
        EncounterReportMetadata malformedAccount = EncounterReportMetadata.read("{\"rsn\":null,\"doomState\":{}}");
        assertFalse(malformedAccount.eligible("Player", new CollectionPolicy(), 1000));
        assertTrue(malformedAccount.eligible("Player", policy(), 1000));
        assertFalse(malformedAccount.readableAccount);
        assertFalse(malformedAccount.belongsTo("Player"));
        EncounterReportMetadata malformedRoute = EncounterReportMetadata.read("{\"rsn\":\"Player\",\"trackingMode\":{}}");
        assertTrue(malformedRoute.eligible("Player", new CollectionPolicy(), 1000));
        assertNull(malformedRoute.route);
        EncounterReportMetadata malformedJson = EncounterReportMetadata.read("{bad");
        assertTrue(malformedJson.eligible("Player", new CollectionPolicy(), 1000));
        assertNull(malformedJson.route);
    }
}

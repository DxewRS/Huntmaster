package com.huntmaster;

import org.junit.Test;
import static org.junit.Assert.*;

public class EncounterReportQueueTest
{
	@Test public void acknowledgedAndExpiredReportsReleaseDiagnosticCapacity()
	{
		EncounterReportQueue q = new EncounterReportQueue();
		for (int i = 0; i < 100; i++) assertTrue(q.add("old" + i, "{}", 1000, 1000));
		q.acknowledge("old0");
		q.acknowledge("old0"); // Duplicate acknowledgement must not free another slot.
		assertTrue(q.add("replacement", "{}", 1000, 1000));
		assertFalse(q.add("overflow", "{}", 1000, 1000));
		long later = 1000 + EncounterReportQueue.RETENTION_MS;
		for (int i = 0; i < 100; i++) assertTrue(q.add("new" + i, "{}", later, later));
		assertFalse(q.add("overflow", "{}", later, later));
		q.clear();
		assertTrue(q.add("after-clear", "{}", later, later));
	}

	@Test public void durableCreditBytesStillLimitDiagnosticsAndUseUtf8Size()
	{
		EncounterReportQueue q = new EncounterReportQueue();
		String payload = "\u00e9".repeat(4096); // 8192 UTF-8 bytes, 4096 Java characters.
		for (int i = 0; i < 128; i++) assertTrue(q.add("kill" + i, payload, 1000, 1000, true));
		assertFalse(q.add("diagnostic", "{}", 1000, 1000));
		q.acknowledge("kill0");
		assertTrue(q.add("diagnostic", payload, 1000, 1000));
		assertFalse(q.add("extra", "{}", 1000, 1000));
		q.acknowledge("diagnostic");
		assertTrue(q.add("extra", "{}", 1000, 1000));
		assertEquals(128, q.snapshot().length);
	}

	@Test public void savedCreditSurvivesDiagnosticCapacityExpiryAndCancelledDelivery()
	{
		EncounterReportQueue q = new EncounterReportQueue();
		for (int i = 0; i < 100; i++) assertTrue(q.add("diagnostic" + i, "{}", 1000, 1000));
		assertTrue(q.add("saved-kill", "{\"trackingMode\":\"beta_candidate\"}", 1000, 1000, true));
		EncounterReportQueue.Entry credit = q.next(1000);
		assertEquals("saved-kill", credit.id);
		credit.inFlight = true;
		q.releaseInFlight();
		assertSame(credit, q.next(1000 + EncounterReportQueue.RETENTION_MS));
		assertEquals(1, q.snapshot().length);
		EncounterReportQueue restored = new EncounterReportQueue();
		assertTrue(restored.add(credit.id, credit.payload, credit.createdAt,
			1000 + 3 * EncounterReportQueue.RETENTION_MS, true));
		assertEquals(credit.payload, restored.next(1000 + 3 * EncounterReportQueue.RETENTION_MS).payload);
		restored.acknowledge(credit.id);
		assertNull(restored.next(1000 + 3 * EncounterReportQueue.RETENTION_MS));
	}
	@Test public void retryRetainsStablePayloadAndSerializesRequests()
	{
		EncounterReportQueue q = new EncounterReportQueue();
		assertTrue(q.add("one", "{}", 1000, 1000)); assertTrue(q.add("two", "{}", 1000, 1000));
		EncounterReportQueue.Entry first = q.next(1000); first.inFlight = true; assertNull(q.next(1000));
		first.inFlight = false; first.nextAttemptAt = 61000;
		assertEquals("two", q.next(1000).id); q.acknowledge("two");
		assertNull(q.next(1000)); assertSame(first, q.next(61000)); assertEquals("{}", first.payload);
	}
	@Test public void limitsAndExpiryNeverReplaceExistingReports()
	{
		EncounterReportQueue q = new EncounterReportQueue();
		for (int i = 0; i < 100; i++) assertTrue(q.add("id" + i, "{}", 1000, 1000));
		assertFalse(q.add("overflow", "{}", 1000, 1000)); assertFalse(q.add("id0", "changed", 1000, 1000));
		assertEquals("{}", q.next(1000).payload);
		assertNull(q.next(1000 + EncounterReportQueue.RETENTION_MS)); assertEquals(0, q.snapshot().length);
	}
	@Test public void oversizedExpiredAndFutureReportsAreRejected()
	{
		EncounterReportQueue q = new EncounterReportQueue();
		assertFalse(q.add("big", "x".repeat(8193), 1000, 1000));
		assertFalse(q.add("future", "{}", 1001, 1000));
		assertFalse(q.add("old", "{}", 1000, 1000 + EncounterReportQueue.RETENTION_MS));
	}
	@Test public void heldCreditWaitsThenExpiresAfterThirtyDays()
	{
		EncounterReportQueue q = new EncounterReportQueue();
		assertTrue(q.add("credit", "{}", 1000, 1000, true));
		EncounterReportQueue.Entry entry = q.snapshot()[0];
		long revision = q.revision();
		q.reject(entry, 2000, false);
		q.reject(entry, 2000, false);
		assertEquals(0, entry.heldAt);
		assertEquals(0, entry.nextAttemptAt);
		q.reject(entry, 3000, false);
		assertEquals(3000, entry.heldAt);
		assertTrue(q.revision() > revision);
		assertNull(q.next(3000));
		assertEquals("credit", q.next(3000 + EncounterReportQueue.HELD_RETRY_MS).id);
		q.prune(3000 + EncounterReportQueue.HELD_RETENTION_MS - 1);
		assertEquals(1, q.snapshot().length);
		q.prune(3000 + EncounterReportQueue.HELD_RETENTION_MS);
		assertEquals(0, q.snapshot().length);
	}
	@Test public void restoreKeepsFutureCreditButNotFutureDiagnostics()
	{
		EncounterReportQueue.Entry saved = new EncounterReportQueue.Entry();
		saved.id = "report"; saved.payload = "{}"; saved.createdAt = 5000; saved.rejections = 2; saved.heldAt = 900; saved.nextAttemptAt = 9000;
		EncounterReportQueue q = new EncounterReportQueue();
		assertFalse(q.restore(saved, 1000, false));
		assertTrue(q.restore(saved, 1000, true));
		EncounterReportQueue.Entry restored = q.snapshot()[0];
		assertEquals(2, restored.rejections);
		assertEquals(900, restored.heldAt);
		assertEquals(0, restored.nextAttemptAt);
	}
	@Test public void explicitlyClearingQueueRemovesEntries()
	{
		EncounterReportQueue q = new EncounterReportQueue(); q.add("one", "{}", 1000, 1000);
		q.clear(); assertNull(q.next(1000));
	}

 @Test public void collectionStopsBeforeMemoryGrowthButSavedEventsSurvive(){
  EncounterReportQueue q=new EncounterReportQueue();
  for(int i=0;i<1000;i++)assertTrue(q.add("saved"+i,"{}",1000,1000,true));
  assertTrue(q.collectionBackpressured());assertEquals(1000,q.snapshot().length);
  q.acknowledge("saved0");assertFalse(q.collectionBackpressured());assertEquals(999,q.snapshot().length);
 }
 @Test public void anotherAccountsQueueDoesNotDelayEligibleDelivery(){
  EncounterReportQueue q=new EncounterReportQueue();q.add("other","{}",1000,1000,true);q.add("current","{}",1000,1000,true);
  assertEquals("current",q.next(1000,e->e.id.equals("current")).id);assertEquals(2,q.snapshot().length);
 }
}

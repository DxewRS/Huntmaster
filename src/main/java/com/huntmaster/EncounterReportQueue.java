package com.huntmaster;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EncounterReportQueue
{
	static final long RETENTION_MS = 24 * 60 * 60 * 1000L;
	static final int MAX_REPORTS = 100;
	static final int MAX_BYTES = 1024 * 1024;
	// Held credit stops blocking tracking but stays saved and retries slowly.
	static final int HOLD_AFTER_REJECTIONS = 3;
	static final long HELD_RETRY_MS = 60 * 60 * 1000L;
	static final long HELD_RETENTION_MS = 30 * RETENTION_MS;
	// Thirty days alone must not expire a report nobody got to retry.
	static final int HELD_EXPIRY_ATTEMPTS = 24;
	static final int MAX_HELD_REPORTS = 2000;
	static final long MAX_HELD_BYTES = 8L * MAX_BYTES;
	// A report that fails alone stays saved and pending, but stops counting as an outage.
	static final int QUIET_AFTER_FAILURES = 3;
	static final int MAX_QUIET_REPORTS = 3;
	static final long QUIET_RETRY_MS = 5 * 60 * 1000L;
	static final class Entry
	{
		String id;
		String payload;
		long createdAt;
		long nextAttemptAt;
		boolean creditCandidate;
		int rejections;
		long heldAt;
		int temporaryFailures;
		transient boolean inFlight;
		transient int bytes;
		transient EncounterReportMetadata delivery;
	}
	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private final List<Entry> dropped = new ArrayList<>();
	private long revision;
	private long payloadBytes;
	private int diagnosticCount;
	long revision() { return revision; }
	// Stop new collection before a persistent outage can grow memory indefinitely.
	// Already captured/saved events always remain deliverable. Held reports have their own limits.
	boolean collectionBackpressured()
	{
		int active = 0;
		long activeBytes = 0;
		for (Entry entry : entries.values()) if (entry.heldAt == 0) { active++; activeBytes += entry.bytes; }
		return active >= 1000 || activeBytes >= 8L * MAX_BYTES;
	}
	boolean add(String id, String payload, long createdAt, long now)
	{
		return add(id, payload, createdAt, now, false);
	}
	boolean add(String id, String payload, long createdAt, long now, boolean creditCandidate)
	{
		return createdAt <= now && insert(id, payload, createdAt, now, creditCandidate);
	}
	// Saved credit stays deliverable even if the PC clock has since moved backwards.
	boolean restore(Entry saved, long now, boolean creditCandidate)
	{
		if ((!creditCandidate && saved.createdAt > now) || !insert(saved.id, saved.payload, saved.createdAt, now, creditCandidate)) return false;
		Entry entry = entries.get(saved.id);
		entry.rejections = saved.rejections;
		entry.heldAt = saved.heldAt;
		entry.temporaryFailures = saved.temporaryFailures;
		// A restart must not reset a backoff, and a wrong clock must not stretch one.
		entry.nextAttemptAt = Math.min(saved.nextAttemptAt, now + HELD_RETRY_MS);
		return true;
	}
	private boolean insert(String id, String payload, long createdAt, long now, boolean creditCandidate)
	{
		prune(now);
		if (id == null || payload == null || createdAt <= 0 || (!creditCandidate && now - createdAt >= RETENTION_MS)
			|| entries.containsKey(id)) return false;
		int size = payload.getBytes(StandardCharsets.UTF_8).length;
		if (size > 8192) return false;
		// Credit delivery has the same durable semantics as pending verified KC.
		// Diagnostic retention and capacity must never discard a saved kill.
		if (!creditCandidate && (diagnosticCount >= MAX_REPORTS || payloadBytes + size > MAX_BYTES)) return false;
		Entry entry = new Entry(); entry.id = id; entry.payload = payload; entry.createdAt = createdAt;
		entry.creditCandidate = creditCandidate; entry.bytes = size;
		entry.delivery = EncounterReportMetadata.read(payload);
		entries.put(id, entry);
		payloadBytes += size;
		if (!creditCandidate) diagnosticCount++;
		revision++;
		return true;
	}
	void prune(long now)
	{
		int held = 0;
		long heldBytes = 0;
		java.util.Iterator<Entry> iterator = entries.values().iterator();
		while (iterator.hasNext())
		{
			Entry entry = iterator.next();
			boolean expired = entry.heldAt != 0 && now - entry.heldAt >= HELD_RETENTION_MS && entry.rejections >= HELD_EXPIRY_ATTEMPTS;
			if (expired || (!entry.creditCandidate && now - entry.createdAt >= RETENTION_MS))
			{
				iterator.remove();
				removed(entry);
				if (expired) dropped.add(entry);
			}
			else if (entry.heldAt != 0) { held++; heldBytes += entry.bytes; }
		}
		if (held > MAX_HELD_REPORTS || heldBytes > MAX_HELD_BYTES) evictHeld(held, heldBytes);
	}
	// Evict a batch, oldest first, so a full store is not rewritten for every further report.
	private void evictHeld(int held, long heldBytes)
	{
		java.util.Iterator<Entry> iterator = entries.values().iterator();
		while (iterator.hasNext() && (held > MAX_HELD_REPORTS * 9 / 10 || heldBytes > MAX_HELD_BYTES * 9 / 10))
		{
			Entry entry = iterator.next();
			if (entry.heldAt == 0) continue;
			iterator.remove();
			removed(entry);
			dropped.add(entry);
			held--;
			heldBytes -= entry.bytes;
		}
	}
	Entry next(long now) { return next(now, entry -> true); }
	Entry next(long now, java.util.function.Predicate<Entry> eligible)
	{
		prune(now);
		// One diagnostic request at a time, independently of KC delivery.
		if (entries.values().stream().anyMatch(entry -> entry.inFlight)) return null;
		for (Entry entry : entries.values()) if (entry.creditCandidate && entry.heldAt == 0 && entry.nextAttemptAt <= now && eligible.test(entry)) return entry;
		for (Entry entry : entries.values()) if (entry.heldAt == 0 && entry.nextAttemptAt <= now && eligible.test(entry)) return entry;
		for (Entry entry : entries.values()) if (entry.nextAttemptAt <= now && eligible.test(entry)) return entry;
		return null;
	}
	private void removed(Entry entry)
	{
		payloadBytes -= entry.bytes;
		if (!entry.creditCandidate) diagnosticCount--;
		revision++;
	}
	void reject(Entry entry, long now, boolean holdNow)
	{
		entry.rejections++;
		if (entry.heldAt == 0 && (holdNow || entry.rejections >= HOLD_AFTER_REJECTIONS)) entry.heldAt = now;
		if (entry.heldAt != 0) entry.nextAttemptAt = now + HELD_RETRY_MS;
		revision++;
	}
	void temporaryFailure(Entry entry, long now)
	{
		entry.temporaryFailures++;
		if (quiet(entry)) entry.nextAttemptAt = now + Math.min(QUIET_RETRY_MS,
			60_000L << Math.min(entry.temporaryFailures - QUIET_AFTER_FAILURES + 1, 3));
		revision++;
	}
	static boolean quiet(Entry entry) { return entry.temporaryFailures >= QUIET_AFTER_FAILURES; }
	/** Reports removed for good; they stay here until the plugin has backed them up. */
	Entry[] dropped() { return dropped.toArray(new Entry[0]); }
	void forgetDropped(int count) { dropped.subList(0, count).clear(); }
	int held(String account)
	{
		int count = 0;
		for (Entry entry : entries.values()) if (entry.heldAt != 0 && entry.delivery.belongsTo(account)) count++;
		return count;
	}
	void acknowledge(String id)
	{
		Entry entry = entries.remove(id);
		if (entry != null) removed(entry);
	}
	void clear()
	{
		if (entries.isEmpty()) return;
		entries.clear(); payloadBytes = 0; diagnosticCount = 0; revision++;
	}
	void releaseInFlight() { entries.values().forEach(entry -> entry.inFlight = false); }
	Entry[] snapshot() { return entries.values().toArray(new Entry[0]); }
}

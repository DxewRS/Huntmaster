package com.huntmaster;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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
	static final class Entry
	{
		String id;
		String payload;
		long createdAt;
		long nextAttemptAt;
		boolean creditCandidate;
		int rejections;
		long heldAt;
		transient boolean inFlight;
		transient EncounterReportMetadata delivery;
	}
	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private long revision;
	private long payloadBytes;
	private int diagnosticCount;
	long revision() { return revision; }
	// Stop new collection before a persistent outage can grow memory indefinitely.
	// Already captured/saved events always remain deliverable.
	boolean collectionBackpressured() { return entries.size() >= 1000 || payloadBytes >= 8L * MAX_BYTES; }
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
		entry.creditCandidate = creditCandidate;
		entry.delivery = EncounterReportMetadata.read(payload);
		entries.put(id, entry);
		payloadBytes += size;
		if (!creditCandidate) diagnosticCount++;
		revision++;
		return true;
	}
	void prune(long now)
	{
		java.util.Iterator<Entry> iterator = entries.values().iterator();
		while (iterator.hasNext())
		{
			Entry entry = iterator.next();
			if ((!entry.creditCandidate && now - entry.createdAt >= RETENTION_MS)
				|| (entry.heldAt != 0 && now - entry.heldAt >= HELD_RETENTION_MS))
			{
				iterator.remove();
				removed(entry);
			}
		}
	}
	Entry next(long now) { return next(now, entry -> true); }
	Entry next(long now, java.util.function.Predicate<Entry> eligible)
	{
		prune(now);
		// One diagnostic request at a time, independently of KC delivery.
		if (entries.values().stream().anyMatch(entry -> entry.inFlight)) return null;
		for (Entry entry : entries.values()) if (entry.creditCandidate && entry.nextAttemptAt <= now && eligible.test(entry)) return entry;
		for (Entry entry : entries.values()) if (entry.nextAttemptAt <= now && eligible.test(entry)) return entry;
		return null;
	}
	private void removed(Entry entry)
	{
		payloadBytes -= entry.payload.getBytes(StandardCharsets.UTF_8).length;
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

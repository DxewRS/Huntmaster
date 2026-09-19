package com.huntmaster;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class EncounterReportQueue
{
	static final long RETENTION_MS = 24 * 60 * 60 * 1000L;
	static final int MAX_REPORTS = 100;
	static final int MAX_BYTES = 1024 * 1024;
	static final class Entry
	{
		String id;
		String payload;
		long createdAt;
		long nextAttemptAt;
		boolean creditCandidate;
		transient boolean inFlight;
	}
	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private long revision;
	private long payloadBytes;
	private int diagnosticCount;
	long revision() { return revision; }
	boolean add(String id, String payload, long createdAt, long now)
	{
		return add(id, payload, createdAt, now, false);
	}
	boolean add(String id, String payload, long createdAt, long now, boolean creditCandidate)
	{
		prune(now);
		if (id == null || payload == null || createdAt <= 0 || createdAt > now || (!creditCandidate && now - createdAt >= RETENTION_MS)
			|| entries.containsKey(id)) return false;
		int size = payload.getBytes(StandardCharsets.UTF_8).length;
		if (size > 8192) return false;
		// Credit delivery has the same durable semantics as pending verified KC.
		// Diagnostic retention and capacity must never discard a saved kill.
		if (!creditCandidate && (diagnosticCount >= MAX_REPORTS || payloadBytes + size > MAX_BYTES)) return false;
		Entry entry = new Entry(); entry.id = id; entry.payload = payload; entry.createdAt = createdAt;
		entry.creditCandidate = creditCandidate;
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
			if (!entry.creditCandidate && now - entry.createdAt >= RETENTION_MS)
			{
				iterator.remove();
				removed(entry);
			}
		}
	}
	Entry next(long now)
	{
		prune(now);
		// One diagnostic request at a time, independently of KC delivery.
		if (entries.values().stream().anyMatch(entry -> entry.inFlight)) return null;
		for (Entry entry : entries.values()) if (entry.creditCandidate && entry.nextAttemptAt <= now) return entry;
		for (Entry entry : entries.values()) if (entry.nextAttemptAt <= now) return entry;
		return null;
	}
	private void removed(Entry entry)
	{
		payloadBytes -= entry.payload.getBytes(StandardCharsets.UTF_8).length;
		if (!entry.creditCandidate) diagnosticCount--;
		revision++;
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

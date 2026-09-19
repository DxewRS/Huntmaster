package com.huntmaster;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Client-thread diagnostic routing; never mutates verification state. */
final class EncounterCapture
{
	private final List<EncounterObservation> records = new ArrayList<>();
	private final EncounterSignalBuffer history = new EncounterSignalBuffer();
	private EncounterObservation current;
	private boolean primarySeen;
	private int primaryTick = -1;
	private Integer counterTotal;
	private boolean verdict;
	private String context;
	private int lastTick = -1;
	private final Consumer<EncounterObservation.Snapshot> sink;
	EncounterCapture(Consumer<EncounterObservation.Snapshot> sink) { this.sink = sink; }

	private EncounterObservation ensure(BossDetector detector, String rsn, String assignment, int tick, long now)
	{
		return ensure(detector, rsn, assignment, tick, now, java.util.Collections.emptyList());
	}
	private EncounterObservation ensure(BossDetector detector, String rsn, String assignment, int tick, long now,
		List<EncounterSignalBuffer.Event> before)
	{
		advance(tick);
		String key = rsn + ":" + assignment + ":" + detector.getName();
		if (!key.equals(context))
		{
			interrupt(tick, EncounterObservation.Reason.ASSIGNMENT_CHANGED);
			context = key;
		}
		if (current == null || current.isClosed())
		{
			if (records.size() >= 128)
			{
				EncounterObservation oldest = records.remove(0);
				oldest.interrupt(tick, EncounterObservation.Reason.SIGNAL_LIMIT);
				sink.accept(oldest.snapshot());
			}
			int start = before.isEmpty() ? tick : before.get(0).tick;
			long time = before.isEmpty() ? now : before.get(0).time;
			current = new EncounterObservation(UUID.randomUUID(), UUID.fromString(assignment), rsn,
				detector.getName(), detector.getDetectorType(), detector.getDetectorVersion(), time, start,
				Math.min(128, detector.getPendingWindowTicks() + tick - start), detector.getDefinition().isEvidenceOnly());
			for (EncounterSignalBuffer.Event event : before)
			{
				if (event.kind == EncounterObservation.SignalKind.LOOT)
					current.recordLoot(event.tick, EncounterObservation.LootAttribution.UNCERTAIN);
				else current.recordPrimary(event.kind, event.tick);
			}
			records.add(current);
			primarySeen = false;
			counterTotal = null;
			verdict = false;
		}
		return current;
	}
	void primary(BossDetector d, String rsn, String assignment, int tick, long now, EncounterObservation.SignalKind kind)
	{
		if (current != null && primarySeen && primaryTick == tick && (rsn + ":" + assignment + ":" + d.getName()).equals(context)) return;
		if (current != null && (primarySeen || verdict)) current = null;
		ensure(d, rsn, assignment, tick, now).recordPrimary(kind, tick);
		history.add(context, tick, now, kind);
		primarySeen = true;
		primaryTick = tick;
	}
	void counter(BossDetector d, String rsn, String assignment, int tick, long now,
		EncounterObservation.CounterSource source, Integer previous, int total)
	{
		advance(tick);
		String key = rsn + ":" + assignment + ":" + d.getName();
		List<EncounterSignalBuffer.Event> before = history.consume(key, tick);
		if (current != null && counterTotal != null && counterTotal != total) current = null;
		ensure(d, rsn, assignment, tick, now, before).recordCounter(tick, source, previous, total);
		counterTotal = total;
	}
	void loot(BossDetector d, String rsn, String assignment, int tick, long now)
	{
		EncounterObservation target = ensure(d, rsn, assignment, tick, now);
		history.add(context, tick, now, EncounterObservation.SignalKind.LOOT);
		target.recordLoot(tick, records.size() == 1 ? EncounterObservation.LootAttribution.MATCHING_ENCOUNTER
			: EncounterObservation.LootAttribution.UNCERTAIN);
	}
	void verified(UUID eventId, EncounterObservation.Method method)
	{
		if (current != null && !current.isClosed() && !verdict)
		{
			current.markVerified(eventId, method);
			verdict = true;
		}
	}
	void uncertain(EncounterObservation.Outcome outcome)
	{
		if (current != null && !current.isClosed() && !verdict)
		{
			current.markUncertain(outcome, outcome == EncounterObservation.Outcome.AMBIGUOUS
				? EncounterObservation.Reason.COUNTER_JUMP : EncounterObservation.Reason.WINDOW_EXPIRED);
			verdict = true;
		}
	}
	void advance(int tick)
	{
		if (lastTick > tick) interrupt(tick, EncounterObservation.Reason.CLOCK_RESET);
		lastTick = tick;
		java.util.Iterator<EncounterObservation> iterator = records.iterator();
		while (iterator.hasNext())
		{
			EncounterObservation record = iterator.next();
			record.closeIfExpired(tick);
			if (record.isClosed()) { iterator.remove(); sink.accept(record.snapshot()); }
		}
	}
	void interrupt(int tick, EncounterObservation.Reason reason)
	{
		for (EncounterObservation record : records)
		{
			record.interrupt(tick, reason);
			sink.accept(record.snapshot());
		}
		records.clear(); current = null; context = null;
		history.clear();
	}
	void clear() { records.clear(); current = null; context = null; lastTick = -1; history.clear(); }
	boolean hasActiveCapture() { return current != null && !current.isClosed(); }
}

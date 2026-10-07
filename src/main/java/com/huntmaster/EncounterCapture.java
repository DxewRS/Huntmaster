package com.huntmaster;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Client-thread diagnostic routing; never mutates verification state. */
final class EncounterCapture
{
	private final java.util.Map<String, EncounterCapture> channels;
	private EncounterCapture selected;
	private int configuredWindow;
    private String policyRevision="packaged-default";
    void revision(String value) { policyRevision=value; }
 private java.util.Map<String,Integer> windows=java.util.Collections.emptyMap();
 void windows(java.util.Map<String,Integer> value) { windows=value; }
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
	private int lastDoomMessageTick = -1;
	private int lastDoomLevel = -1;
	private final java.util.Map<DoomLevelCounters.Source,String> lastDoomCounters=new java.util.EnumMap<>(DoomLevelCounters.Source.class);
	EncounterCapture(Consumer<EncounterObservation.Snapshot> sink) { this(sink, true); }
 private EncounterCapture(Consumer<EncounterObservation.Snapshot> sink, boolean root) { this.sink=sink; channels=root?new java.util.LinkedHashMap<>():null; }
 private EncounterCapture channel(BossDetector d) {
  if (!channels.containsKey(d.getName())) { if(channels.size()>=96) throw new IllegalStateException("Capture capacity"); channels.put(d.getName(),new EncounterCapture(sink,false)); }
  selected=channels.get(d.getName()); selected.configuredWindow=windows.getOrDefault(d.getName(),configuredWindow); selected.policyRevision=policyRevision; return selected;
 }
 void select(BossDetector d) { if(channels!=null)channel(d); }
 void variantCompletion(BossDetector d,String rsn,String assignment,int tick,long now,
     EncounterVariant variant,int previous,int total) {
  if (!EncounterVariant.supports(d.getName()) || variant==null) throw new IllegalArgumentException("Invalid DT2 variant");
  // Separate channels prevent normal/awakened counters, NPC support and delayed events mixing.
  if(channels!=null) {
   String key=d.getName()+":"+variant.name();
   if(!channels.containsKey(key)) {
    if(channels.size()>=96)throw new IllegalStateException("Capture capacity");
    channels.put(key,new EncounterCapture(sink,false));
   }
   selected=channels.get(key);selected.configuredWindow=windows.getOrDefault(d.getName(),configuredWindow);
   selected.policyRevision=policyRevision;
   selected.variantCompletion(d,rsn,assignment,tick,now,variant,previous,total);return;
  }
  if(previous<0 || total<=previous)throw new IllegalArgumentException("Invalid completion counter");
  primary(d,rsn,assignment,tick,now,EncounterObservation.SignalKind.COMPLETION);
  current.variant(variant);
  counter(d,rsn,assignment,tick,now,EncounterObservation.CounterSource.COMPLETION_VARP,previous,total);
 }
 void notice(BossDetector d,String rsn,String assignment,int tick,long now,String source,Integer value) {
  if(channels!=null){channel(d).notice(d,rsn,assignment,tick,now,source,value);return;}
  ensure(d,rsn,assignment,tick,now).recordPrimary(EncounterObservation.SignalKind.DIAGNOSTIC,tick);
  diagnostic(source,value,tick);
 }

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
			if (records.size() >= 8)
			{
				EncounterObservation oldest = records.remove(0);
				oldest.interrupt(tick, EncounterObservation.Reason.SIGNAL_LIMIT);
				sink.accept(oldest.snapshot());
			}
			int start = before.isEmpty() ? tick : before.get(0).tick;
			long time = before.isEmpty() ? now : before.get(0).time;
			current = new EncounterObservation(UUID.randomUUID(), assignment == null ? null : UUID.fromString(assignment), rsn,
				detector.getName(), detector.getDetectorType(), detector.getDetectorVersion(), time, start,
				Math.min(128, Math.max(configuredWindow,("The Nightmare".equals(detector.getName()) ? Math.max(24, detector.getPendingWindowTicks()) : detector.getPendingWindowTicks())) + tick - start), detector.getDefinition().isEvidenceOnly());
			for (EncounterSignalBuffer.Event event : before)
			{
				if (event.kind == EncounterObservation.SignalKind.LOOT)
					current.recordLoot(event.tick, EncounterObservation.LootAttribution.UNCERTAIN);
				else current.recordPrimary(event.kind, event.tick);
			}
			records.add(current);
            current.policyRevision=policyRevision;
			primarySeen = false;
			counterTotal = null;
			verdict = false;
		}
		return current;
	}
	void primary(BossDetector d, String rsn, String assignment, int tick, long now, EncounterObservation.SignalKind kind)
	{
		if(channels!=null){channel(d).primary(d,rsn,assignment,tick,now,kind);return;}
		if (current != null && primarySeen && primaryTick == tick && (rsn + ":" + assignment + ":" + d.getName()).equals(context)) return;
		// A new encounter must not inherit unconsumed support from a prior kill.
		history.clear();
		if (current != null && (primarySeen || verdict)) current = null;
		ensure(d, rsn, assignment, tick, now).recordPrimary(kind, tick);
		history.add(context, tick, now, kind);
		primarySeen = true;
		primaryTick = tick;
	}
	void counter(BossDetector d, String rsn, String assignment, int tick, long now,
		EncounterObservation.CounterSource source, Integer previous, int total)
	{
		if(channels!=null){channel(d).counter(d,rsn,assignment,tick,now,source,previous,total);return;}
		if (DoomCompletionEvidence.BOSS.equals(d.getName()))
		{
			// Initial synchronization is a baseline, never an encounter.
			if (source != EncounterObservation.CounterSource.COMPLETION_VARP || previous == null || previous == total) return;
			prepareDoomContext(rsn, assignment, tick);
			EncounterObservation target = doomPartner(tick, false, null, null);
			if (target == null) { current = null; target = ensure(d, rsn, assignment, tick, now); }
			target.recordCounter(tick, source, previous, total);
			return;
		}
		advance(tick);
		String key = rsn + ":" + assignment + ":" + d.getName();
		List<EncounterSignalBuffer.Event> before = history.consume(key, tick);
		if (current != null && counterTotal != null && counterTotal != total) current = null;
		ensure(d, rsn, assignment, tick, now, before).recordCounter(tick, source, previous, total);
		counterTotal = total;
	}

	void doomCompletion(BossDetector d, String rsn, String assignment, int tick, long now, DoomCompletionEvidence evidence)
	{
		if (channels != null) { channel(d).doomCompletion(d, rsn, assignment, tick, now, evidence); return; }
		if (!DoomCompletionEvidence.BOSS.equals(d.getName())) throw new IllegalArgumentException("Expected Doom detector");
		prepareDoomContext(rsn, assignment, tick);
		// The same notification can be repeated across game-message categories in one tick.
		if (lastDoomMessageTick == tick && lastDoomLevel == evidence.completedLevel) return;
		EncounterObservation target = doomPartner(tick, true, evidence, null);
		if (target == null) { current = null; target = ensure(d, rsn, assignment, tick, now); }
		target.recordDoomCompletion(evidence, tick);
		lastDoomMessageTick = tick;
		lastDoomLevel = evidence.completedLevel;
	}

	private void prepareDoomContext(String rsn, String assignment, int tick)
	{
		advance(tick);
		if (context != null && !context.equals(rsn + ":" + assignment + ":" + DoomCompletionEvidence.BOSS))
			interrupt(tick, EncounterObservation.Reason.ASSIGNMENT_CHANGED);
	}

	void doomCounter(BossDetector d, String rsn, String assignment, int tick, long now, DoomLevelCounters.Observation evidence)
	{
		if(channels!=null) { channel(d).doomCounter(d,rsn,assignment,tick,now,evidence); return; }
		if(!DoomCompletionEvidence.BOSS.equals(d.getName()))throw new IllegalArgumentException("Expected Doom detector");
		prepareDoomContext(rsn,assignment,tick);
		String identity=evidence.previous+":"+evidence.current;
		if(identity.equals(lastDoomCounters.get(evidence.source)))return;
		EncounterObservation target=doomPartner(tick,false,null,evidence);
		if(target==null) { current=null; target=ensure(d,rsn,assignment,tick,now); }
		target.recordDoomCounter(evidence,tick);
		lastDoomCounters.put(evidence.source,identity);
	}
	private EncounterObservation doomPartner(int tick, boolean message, DoomCompletionEvidence completion, DoomLevelCounters.Observation counter)
	{
		EncounterObservation candidate = null;
		for (EncounterObservation record : records)
		{
			if (!record.acceptsTick(tick)) continue;
			boolean compatible=message
				? !record.hasDoomCompletion() && (record.hasCounter() || record.hasDoomCounter()) && record.matchesDoomCounter(completion.completedLevel)
				: counter!=null
				? !record.hasDoomCounter() && (record.hasCounter() || record.hasDoomCompletion()) && record.matchesDoomLevel(counter.source)
				: !record.hasCounter() && (record.hasDoomCompletion() || record.hasDoomCounter());
			if(!compatible)continue;
			// Any other unmatched evidence makes the temporal association ambiguous.
			if (candidate != null) return null;
			candidate = record;
		}
		return candidate;
	}

	void interruptDoom(int tick)
	{
		if (channels == null) return;
		EncounterCapture doom = channels.get(DoomCompletionEvidence.BOSS);
		if (doom != null) doom.interrupt(tick, EncounterObservation.Reason.LOGOUT);
	}
	void interruptVariants(int tick)
	{
		if(channels==null)return;
		for(java.util.Map.Entry<String,EncounterCapture> entry:channels.entrySet())
			if(entry.getKey().endsWith(":NORMAL") || entry.getKey().endsWith(":AWAKENED"))
				entry.getValue().interrupt(tick,EncounterObservation.Reason.LOGOUT);
	}
	void loot(BossDetector d, String rsn, String assignment, int tick, long now)
	{
		if(channels!=null){channel(d).loot(d,rsn,assignment,tick,now);return;}
		EncounterObservation target = ensure(d, rsn, assignment, tick, now);
		// Once a counter is attached, subsequent loot belongs to that record only.
		if (counterTotal == null) history.add(context, tick, now, EncounterObservation.SignalKind.LOOT);
		target.recordLoot(tick, records.size() == 1 ? EncounterObservation.LootAttribution.MATCHING_ENCOUNTER
			: EncounterObservation.LootAttribution.UNCERTAIN);
	}
	void chestState(int mask, int age, int tick)
	{
		if(channels!=null){if(selected!=null)selected.chestState(mask,age,tick);return;}
		if (current != null && primarySeen && primaryTick == tick) current.recordChestState(mask, age, tick);
	}
	void diagnostic(String source, Integer total, int tick)
	{
		if(channels!=null){if(selected!=null)selected.diagnostic(source,total,tick);return;}
		if (current != null) current.diagnostic(source, total, tick);
	}
	void uncertain(EncounterObservation.Outcome outcome)
	{
		if(channels!=null){if(selected!=null)selected.uncertain(outcome);return;}
		if (current != null && !current.isClosed() && !verdict)
		{
			current.markUncertain(outcome, outcome == EncounterObservation.Outcome.AMBIGUOUS
				? EncounterObservation.Reason.COUNTER_JUMP : EncounterObservation.Reason.WINDOW_EXPIRED);
			verdict = true;
		}
	}
	void advance(int tick)
	{
		if(channels!=null){for(EncounterCapture c:channels.values())c.advance(tick);return;}
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
		if(channels!=null){for(EncounterCapture c:channels.values())c.interrupt(tick,reason);return;}
		for (EncounterObservation record : records)
		{
			record.interrupt(tick, reason);
			sink.accept(record.snapshot());
		}
		records.clear(); current = null; context = null;
		lastDoomMessageTick = -1; lastDoomLevel = -1;
		lastDoomCounters.clear();
		history.clear();
	}
	void clear() { if(channels!=null){for(EncounterCapture c:channels.values())c.clear();channels.clear();selected=null;return;} records.clear(); current = null; context = null; lastTick = -1; lastDoomMessageTick = -1; lastDoomLevel = -1; lastDoomCounters.clear(); history.clear(); }
}

package com.huntmaster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Pure diagnostic record. It never decides or awards Huntmaster credit. */
final class EncounterObservation
{
	static final int MAX_SIGNALS = 32;
	static final int MAX_WINDOW_TICKS = 128;
	enum SignalKind { DEATH, COMPLETION, ACTIVITY_COMPLETION, COUNTER, LOOT }
	enum CounterSource { KC_MESSAGE, RS_PROFILE, COMPLETION_VARP }
	enum LootAttribution { MATCHING_ENCOUNTER, UNCERTAIN }
	enum Outcome { VERIFIED, UNRESOLVED, AMBIGUOUS, INTERRUPTED }
	enum Method { DEATH_AND_COUNTER, DEATH_AND_LOOT, COUNTER_AND_LOOT, COMPLETION_AND_COUNTER, ACTIVITY_AND_COUNTER }
	enum Reason { SUFFICIENT_EVIDENCE, WINDOW_EXPIRED, COUNTER_JUMP, LOGOUT, ASSIGNMENT_CHANGED, SHUTDOWN, SIGNAL_LIMIT, CLOCK_RESET }

	static final class Signal
	{
		final SignalKind kind;
		final int tickOffset;
		final CounterSource source;
		final Integer previous;
		final Integer current;
		final LootAttribution attribution;

		private Signal(SignalKind kind, int tickOffset, CounterSource source,
				Integer previous, Integer current, LootAttribution attribution)
		{
			this.kind = kind;
			this.tickOffset = tickOffset;
			this.source = source;
			this.previous = previous;
			this.current = current;
			this.attribution = attribution;
		}

		private boolean sameAs(Signal other)
		{
			return kind == other.kind && tickOffset == other.tickOffset && source == other.source
					&& Objects.equals(previous, other.previous) && Objects.equals(current, other.current)
					&& attribution == other.attribution;
		}
	}

	static final class Snapshot
	{
		final boolean evidenceOnly;
		final UUID reportId;
		final UUID assignmentId;
		final String rsn;
		final String boss;
		final BossDetectorType detectorType;
		final String detectorVersion;
		final long observedAt;
		final int windowTicks;
		final int durationTicks;
		final Outcome outcome;
		final Method method;
		final Reason reason;
		final Reason interruptionReason;
		final UUID creditEventId;
		final boolean captureComplete;
		final List<Signal> signals;

		private Snapshot(EncounterObservation record)
		{
			evidenceOnly = record.evidenceOnly;
			reportId = record.reportId;
			assignmentId = record.assignmentId;
			rsn = record.rsn;
			boss = record.boss;
			detectorType = record.detectorType;
			detectorVersion = record.detectorVersion;
			observedAt = record.observedAt;
			windowTicks = record.windowTicks;
			durationTicks = record.durationTicks;
			outcome = record.outcome;
			method = record.method;
			reason = record.reason;
			interruptionReason = record.interruptionReason;
			creditEventId = record.creditEventId;
			captureComplete = record.interruptionReason == null;
			signals = Collections.unmodifiableList(new ArrayList<>(record.signals));
		}
	}

	private final boolean evidenceOnly;
	private final UUID reportId;
	private final UUID assignmentId;
	private final String rsn;
	private final String boss;
	private final BossDetectorType detectorType;
	private final String detectorVersion;
	private final long observedAt;
	private final int startTick;
	private final int windowTicks;
	private final List<Signal> signals = new ArrayList<>();
	private Outcome outcome;
	private Method method;
	private Reason reason;
	private Reason interruptionReason;
	private UUID creditEventId;
	private int durationTicks;
	private int latestTickOffset;
	private boolean closed;

	EncounterObservation(UUID reportId, UUID assignmentId, String rsn, String boss,
			BossDetectorType detectorType, String detectorVersion, long observedAt, int startTick, int windowTicks)
	{
		this(reportId, assignmentId, rsn, boss, detectorType, detectorVersion, observedAt, startTick, windowTicks, false);
	}

	EncounterObservation(UUID reportId, UUID assignmentId, String rsn, String boss,
			BossDetectorType detectorType, String detectorVersion, long observedAt, int startTick, int windowTicks, boolean evidenceOnly)
	{
		this.evidenceOnly = evidenceOnly;
		this.reportId = Objects.requireNonNull(reportId);
		this.assignmentId = Objects.requireNonNull(assignmentId);
		this.rsn = text(rsn, 64);
		this.boss = text(boss, 128);
		this.detectorType = Objects.requireNonNull(detectorType);
		this.detectorVersion = text(detectorVersion, 128);
		if (observedAt <= 0 || startTick < 0 || windowTicks < 1 || windowTicks > MAX_WINDOW_TICKS)
		{
			throw new IllegalArgumentException("Invalid encounter observation bounds");
		}
		this.observedAt = observedAt;
		this.startTick = startTick;
		this.windowTicks = windowTicks;
	}

	private static String text(String value, int limit)
	{
		if (value == null || value.trim().isEmpty() || value.length() > limit)
		{
			throw new IllegalArgumentException("Invalid observation metadata");
		}
		return value;
	}

	boolean recordPrimary(SignalKind kind, int tick)
	{
		if (kind != SignalKind.DEATH && kind != SignalKind.COMPLETION && kind != SignalKind.ACTIVITY_COMPLETION)
		{
			throw new IllegalArgumentException("Expected a primary encounter signal");
		}
		for (Signal existing : signals)
		{
			if (existing.kind == kind)
			{
				// A later primary signal belongs in another record, not this kill.
				return false;
			}
		}
		return add(new Signal(kind, offset(tick), null, null, null, null));
	}

	boolean recordCounter(int tick, CounterSource source, Integer previous, int current)
	{
		Objects.requireNonNull(source);
		if (current < 0 || previous != null && previous < 0)
		{
			throw new IllegalArgumentException("Invalid counter total");
		}
		for (Signal existing : signals)
		{
			if (existing.kind == SignalKind.COUNTER && !Objects.equals(existing.current, current))
			{
				return false;
			}
		}
		return add(new Signal(SignalKind.COUNTER, offset(tick), source, previous, current, null));
	}

	boolean recordLoot(int tick, LootAttribution attribution)
	{
		return add(new Signal(SignalKind.LOOT, offset(tick), null, null, null, Objects.requireNonNull(attribution)));
	}

	private int offset(int tick)
	{
		long offset = (long) tick - startTick;
		return offset < 0 || offset >= windowTicks ? -1 : (int) offset;
	}

	private boolean add(Signal signal)
	{
		if (closed || signal.tickOffset < 0)
		{
			return false;
		}
		for (Signal existing : signals)
		{
			if (existing.sameAs(signal))
			{
				return false;
			}
		}
		if (signals.size() >= MAX_SIGNALS)
		{
			interrupt(startTick + signal.tickOffset, Reason.SIGNAL_LIMIT);
			return false;
		}
		signals.add(signal);
		latestTickOffset = Math.max(latestTickOffset, signal.tickOffset);
		return true;
	}

	void markVerified(UUID creditEventId, Method method)
	{
		if (evidenceOnly) throw new IllegalStateException("Evidence-only capture cannot claim credit");
		Objects.requireNonNull(creditEventId);
		Objects.requireNonNull(method);
		setVerdict(Outcome.VERIFIED, Reason.SUFFICIENT_EVIDENCE);
		this.creditEventId = creditEventId;
		this.method = method;
	}

	void markUncertain(Outcome outcome, Reason reason)
	{
		if (outcome != Outcome.UNRESOLVED && outcome != Outcome.AMBIGUOUS
				|| reason != Reason.WINDOW_EXPIRED && reason != Reason.COUNTER_JUMP)
		{
			throw new IllegalArgumentException("Invalid uncertain verdict");
		}
		setVerdict(outcome, reason);
	}

	private void setVerdict(Outcome outcome, Reason reason)
	{
		if (closed || this.outcome != null)
		{
			throw new IllegalStateException("Encounter verdict already fixed");
		}
		this.outcome = outcome;
		this.reason = reason;
	}

	boolean closeIfExpired(int tick)
	{
		if (closed || (long) tick - startTick < windowTicks)
		{
			return false;
		}
		if (outcome == null)
		{
			outcome = Outcome.UNRESOLVED;
			reason = Reason.WINDOW_EXPIRED;
		}
		durationTicks = windowTicks;
		closed = true;
		return true;
	}

	void interrupt(int tick, Reason interruption)
	{
		if (interruption != Reason.LOGOUT && interruption != Reason.ASSIGNMENT_CHANGED
				&& interruption != Reason.SHUTDOWN && interruption != Reason.SIGNAL_LIMIT && interruption != Reason.CLOCK_RESET)
		{
			throw new IllegalArgumentException("Invalid capture interruption");
		}
		if (closed)
		{
			return;
		}
		interruptionReason = interruption;
		if (outcome == null)
		{
			outcome = Outcome.INTERRUPTED;
			reason = interruption;
		}
		durationTicks = (int) Math.min(windowTicks, Math.max(latestTickOffset, (long) tick - startTick));
		closed = true;
	}

	boolean isClosed() { return closed; }

	Snapshot snapshot()
	{
		if (!closed)
		{
			throw new IllegalStateException("Capture must be finalized before reporting");
		}
		return new Snapshot(this);
	}
}

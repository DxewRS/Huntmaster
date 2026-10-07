package com.huntmaster;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.Locale;

final class EncounterReportCodec
{
	private static final java.time.format.DateTimeFormatter TIMESTAMP =
		new java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter();
	private EncounterReportCodec() { }
	static String deliveryRoute(JsonObject report)
	{
		return report.has("trackingMode") && !report.get("trackingMode").isJsonNull()
			&& "server_observation".equals(report.get("trackingMode").getAsString())
			? "/api/runelite/observations" : "/api/runelite/encounter-reports";
	}
	private static JsonObject encodeSignals(EncounterObservation.Snapshot s)
	{
		JsonObject body = new JsonObject();
		body.addProperty("schemaVersion", 1);
		body.addProperty("collectorVersion", "2");
        body.addProperty("pluginBuild", CollectorDiagnostics.BUILD);
        body.addProperty("collectionPolicyRevision", s.policyRevision);
		body.addProperty("trackingMode", "server_observation");
		body.addProperty("reportId", s.reportId.toString());
		body.addProperty("assignmentId", s.assignmentId == null ? null : s.assignmentId.toString());
		body.addProperty("rsn", s.rsn);
		body.addProperty("boss", s.boss);
		body.addProperty("detectorType", s.detectorType.name());
		body.addProperty("detectorVersion", s.detectorVersion);
		body.addProperty("observedAt", TIMESTAMP.format(Instant.ofEpochMilli(s.observedAt)));
		body.addProperty("durationTicks", s.durationTicks);
		body.addProperty("windowTicks", s.windowTicks);
		body.addProperty("outcome", lower(s.outcome));
		body.addProperty("captureStatus", s.captureComplete ? "complete" : "interrupted");
		body.addProperty("reason", lower(s.reason));
		if (s.interruptionReason != null) body.addProperty("interruptionReason", lower(s.interruptionReason));
		JsonArray signals = new JsonArray();
		for (EncounterObservation.Signal signal : s.signals)
		{
			JsonObject item = new JsonObject();
			item.addProperty("kind", lower(signal.kind));
			item.addProperty("tickOffset", signal.tickOffset);
			if (signal.source != null)
			{
				item.addProperty("source", lower(signal.source));
				if (signal.previous == null) item.add("previous", JsonNull.INSTANCE);
				else item.addProperty("previous", signal.previous);
				item.addProperty("current", signal.current);
			}
			if (signal.attribution != null) item.addProperty("attribution", lower(signal.attribution));
			signals.add(item);
		}
		body.add("signals", signals);
		return body;
	}
	static JsonObject encodeObservation(EncounterObservation.Snapshot snapshot)
	{
		return encodeObservation(snapshot, true);
	}
	static JsonObject encodeObservation(EncounterObservation.Snapshot snapshot, boolean variantSupported)
	{
		return encodeObservation(snapshot,variantSupported,true);
	}
	static JsonObject encodeObservation(EncounterObservation.Snapshot snapshot, boolean variantSupported, boolean doomCountersSupported)
	{
		JsonObject body = encodeSignals(snapshot);
		if (snapshot.encounterVariant != null)
		{
			if (!EncounterVariant.supports(snapshot.boss)) throw new IllegalArgumentException("Invalid variant boss");
			// Normal legacy delivery remains available. Never erase AWAKENED identity.
			if (variantSupported || snapshot.encounterVariant == EncounterVariant.AWAKENED)
				body.addProperty("encounterVariant", snapshot.encounterVariant.name());
		}
		JsonObject diagnostics = new JsonObject();
		diagnostics.addProperty("version", 1);
		JsonArray counters = new JsonArray();
		for (EncounterObservation.Diagnostic d : snapshot.diagnostics)
		{
			JsonObject item = new JsonObject();
			item.addProperty("source", d.source);
			item.addProperty("tickOffset", d.tickOffset);
			if (d.total == null) item.add("total", JsonNull.INSTANCE); else item.addProperty("total", d.total);
			counters.add(item);
		}
		diagnostics.add("counters", counters);
		body.add("diagnostics", diagnostics);
		if (snapshot.doomState != null)
		{
			DoomCompletionEvidence evidence = snapshot.doomState;
			JsonObject doom = new JsonObject();
			doom.addProperty("version", 1);
			doom.addProperty("completedLevel", evidence.completedLevel);
			doom.addProperty("extended", evidence.extended);
			doom.addProperty("source", evidence.source);
			doom.addProperty("completionMessageObserved", true);
			doom.addProperty("tickOffset", evidence.tickOffset);
			body.add("doomState", doom);
		}
		if(snapshot.doomCounter!=null && doomCountersSupported)
		{
			DoomLevelCounters.Observation evidence=snapshot.doomCounter;
			JsonObject counter=new JsonObject();
			counter.addProperty("source",evidence.source.name());
			counter.addProperty("previous",evidence.previous);
			counter.addProperty("current",evidence.current);
			counter.addProperty("tickOffset",evidence.tickOffset);
			JsonObject countersState=new JsonObject(); countersState.addProperty("version",1);
			JsonArray observations=new JsonArray(); observations.add(counter);
			countersState.add("counters",observations); body.add("doomCounters",countersState);
		}
        if (snapshot.chestState != null) {
            JsonObject chest = new JsonObject();
            chest.addProperty("version", 1);
            chest.addProperty("mask", snapshot.chestState[0]);
            chest.addProperty("ageTicks", snapshot.chestState[1]);
            chest.addProperty("tickOffset", snapshot.chestState[2]);
            body.add("chestState", chest);
        }
		body.addProperty("outcome", snapshot.captureComplete ? "unresolved" : "interrupted");
		body.addProperty("reason", snapshot.captureComplete ? "window_expired" : lower(snapshot.interruptionReason));
		return body;
	}
	private static String lower(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
}

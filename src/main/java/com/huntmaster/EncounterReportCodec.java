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
	private static final java.util.regex.Pattern DAGANNOTH_VERSION =
		java.util.regex.Pattern.compile("dagannoth-(rex|prime|supreme)-beta-v1");
	private EncounterReportCodec() { }
	static JsonObject encode(EncounterObservation.Snapshot s)
	{
		JsonObject body = new JsonObject();
		body.addProperty("schemaVersion", 1);
		body.addProperty("trackingMode", ("generic-beta-v1".equals(s.detectorVersion)
			|| "dedicated-total-beta-v1".equals(s.detectorVersion)
			|| ("Dagannoth Kings".equals(s.boss) && DAGANNOTH_VERSION.matcher(s.detectorVersion).matches())) ? "beta_candidate"
			: s.evidenceOnly ? "evidence_only" : "verified_credit");
		body.addProperty("reportId", s.reportId.toString());
		body.addProperty("assignmentId", s.assignmentId.toString());
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
		if (s.method != null) body.addProperty("verificationMethod", lower(s.method));
		if (s.creditEventId != null) body.addProperty("creditEventId", s.creditEventId.toString());
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
	private static String lower(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
}

package com.huntmaster;

/** Local counts of completed verification attempts, not HTTP requests or physical kills. */
final class VerificationTelemetry
{
	enum Outcome { VERIFIED, UNRESOLVED, AMBIGUOUS }
	private static final long MAX_COUNT = 1_000_000_000_000L;
	private long verified;
	private long unresolved;
	private long ambiguous;

	void record(Outcome outcome)
	{
		switch (outcome)
		{
			case VERIFIED: verified = Math.min(MAX_COUNT, verified + 1); break;
			case UNRESOLVED: unresolved = Math.min(MAX_COUNT, unresolved + 1); break;
			case AMBIGUOUS: ambiguous = Math.min(MAX_COUNT, ambiguous + 1); break;
			default: throw new IllegalArgumentException("Unknown outcome");
		}
	}

	void restore(long savedVerified, long savedUnresolved, long savedAmbiguous)
	{
		verified = clamp(savedVerified);
		unresolved = clamp(savedUnresolved);
		ambiguous = clamp(savedAmbiguous);
	}

	private static long clamp(long value)
	{
		return Math.max(0, Math.min(MAX_COUNT, value));
	}

	long getVerified() { return verified; }
	long getUnresolved() { return unresolved; }
	long getAmbiguous() { return ambiguous; }
	long getCompletedAttempts() { return verified + unresolved + ambiguous; }

	Double getVerificationRate()
	{
		return getCompletedAttempts() == 0 ? null : (double) verified / getCompletedAttempts();
	}
}

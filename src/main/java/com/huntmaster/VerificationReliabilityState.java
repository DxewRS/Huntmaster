package com.huntmaster;

/** Holds the bot-provided pause state; never counts local verification attempts. */
final class VerificationReliabilityState
{
	static final int FAILURE_THRESHOLD = 3;
	private int failures;

	boolean isPaused()
	{
		return failures >= FAILURE_THRESHOLD;
	}

	int getFailures()
	{
		return failures;
	}

	void restore(int savedFailures)
	{
		failures = Math.max(0, Math.min(FAILURE_THRESHOLD, savedFailures));
	}
}

package com.huntmaster;

/** Counts consecutive failed verification attempts, independently of HTTP outages. */
final class VerificationReliabilityState
{
	static final int FAILURE_THRESHOLD = 3;
	private int failures;

	boolean recordFailure()
	{
		if (isPaused())
		{
			return false;
		}
		failures++;
		return true;
	}

	void recordVerified()
	{
		// A late signal must not undo an established pause.
		if (!isPaused())
		{
			failures = 0;
		}
	}

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

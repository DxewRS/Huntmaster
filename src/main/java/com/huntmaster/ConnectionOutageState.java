package com.huntmaster;

/** Outage time is independent of queued-event age and survives client restarts. */
final class ConnectionOutageState
{
	static final long GRACE_PERIOD_MS = 10 * 60 * 1000L;
	private long startedAt;

	void restore(long savedStartedAt, long now)
	{
		startedAt = savedStartedAt > 0 ? Math.min(savedStartedAt, now) : 0;
	}

	boolean fail(long now)
	{
		if (startedAt != 0)
		{
			return false;
		}
		startedAt = now;
		return true;
	}

	boolean isPaused(long now)
	{
		return startedAt != 0 && now - startedAt >= GRACE_PERIOD_MS;
	}

	long getStartedAt()
	{
		return startedAt;
	}

	void recover()
	{
		startedAt = 0;
	}
}

package com.huntmaster;

/** Outage time is independent of queued-event age and survives client restarts. */
final class ConnectionOutageState
{
	static final long GRACE_PERIOD_MS = 10 * 60 * 1000L;
	static final long WARNING_DELAY_MS = 5 * 60 * 1000L;
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

	int noticeStage(long now)
	{
		if (isPaused(now)) return 2;
		return startedAt != 0 && now - startedAt >= WARNING_DELAY_MS ? 1 : 0;
	}

	long getStartedAt()
	{
		return startedAt;
	}

	void recover()
	{
		startedAt = 0;
	}

	boolean recover(long now)
	{
		boolean wasPaused = isPaused(now);
		recover();
		return wasPaused;
	}
}

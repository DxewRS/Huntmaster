package com.huntmaster;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionOutageStateTest
{
	private static final long START = 1_000_000L;

	@Test
	public void connectedStateDoesNotPause()
	{
		assertFalse(new ConnectionOutageState().isPaused(Long.MAX_VALUE));
	}

	@Test
	public void repeatedFailuresDoNotExtendGracePeriod()
	{
		ConnectionOutageState state = new ConnectionOutageState();
		assertTrue(state.fail(START));
		assertFalse(state.fail(START + 30_000));
		assertEquals(START, state.getStartedAt());
		assertFalse(state.isPaused(START + ConnectionOutageState.GRACE_PERIOD_MS - 1));
		assertTrue(state.isPaused(START + ConnectionOutageState.GRACE_PERIOD_MS));
	}

	@Test
	public void restartPreservesRemainingGracePeriod()
	{
		ConnectionOutageState restored = new ConnectionOutageState();
		restored.restore(START, START + 5 * 60_000);
		assertEquals(START, restored.getStartedAt());
		assertFalse(restored.isPaused(START + 9 * 60_000));
		assertTrue(restored.isPaused(START + 10 * 60_000));
	}

	@Test
	public void restartAfterDeadlineStaysPaused()
	{
		ConnectionOutageState restored = new ConnectionOutageState();
		restored.restore(START, START + 20 * 60_000);
		assertTrue(restored.isPaused(START + 20 * 60_000));
	}

	@Test
	public void recoveryStartsFreshGracePeriodForNextOutage()
	{
		ConnectionOutageState state = new ConnectionOutageState();
		state.fail(START);
		state.recover();
		assertEquals(0, state.getStartedAt());
		assertFalse(state.isPaused(START + 20 * 60_000));
		assertTrue(state.fail(START + 30 * 60_000));
		assertFalse(state.isPaused(START + 39 * 60_000));
	}

	@Test
	public void invalidRestoredTimestampsDoNotBreakDeadline()
	{
		ConnectionOutageState state = new ConnectionOutageState();
		state.restore(-1, START);
		assertEquals(0, state.getStartedAt());
		state.restore(START + 60_000, START);
		assertEquals(START, state.getStartedAt());
		assertTrue(state.isPaused(START + ConnectionOutageState.GRACE_PERIOD_MS));
	}
}

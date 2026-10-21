package com.huntmaster;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConnectionOutageStateTest
{
	private static final long START = 1_000_000L;

	@Test public void warningAndPauseHaveSeparateExactDeadlines()
	{
		ConnectionOutageState state=new ConnectionOutageState();
		assertEquals(0,state.noticeStage(Long.MAX_VALUE));
		state.fail(START);
		assertEquals(0,state.noticeStage(START+ConnectionOutageState.WARNING_DELAY_MS-1));
		assertEquals(1,state.noticeStage(START+ConnectionOutageState.WARNING_DELAY_MS));
		assertEquals(1,state.noticeStage(START+ConnectionOutageState.GRACE_PERIOD_MS-1));
		assertEquals(2,state.noticeStage(START+ConnectionOutageState.GRACE_PERIOD_MS));
	}

	@Test public void recoveryNoticeIsOnlyDueAfterTenMinuteDeadline()
	{
		for(long duration:new long[]{1,299999,300000,599999})
		{
			ConnectionOutageState state=new ConnectionOutageState();state.fail(START);
			assertFalse(state.recover(START+duration));assertEquals(0,state.getStartedAt());
		}
		ConnectionOutageState state=new ConnectionOutageState();state.restore(START,START+900000);
		assertTrue(state.recover(START+900000));assertEquals(0,state.noticeStage(START+900001));
		state.fail(START+1000000);assertFalse(state.recover(START+1000001));
	}

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

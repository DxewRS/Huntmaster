package com.huntmaster;

import org.junit.Test;
import static org.junit.Assert.*;

public class BotEndpointTest
{
	@Test public void onlyDevelopmentUsesLocalBot()
	{
		assertEquals("http://127.0.0.1:8787", BotEndpoint.resolve(true, null));
		assertNull(BotEndpoint.resolve(false, null));
		assertNull(BotEndpoint.resolve(false, ""));
		assertEquals("https://huntmaster.example.com", BotEndpoint.resolve(false, "https://huntmaster.example.com/"));
	}
	@Test public void invalidPublicOriginsCannotReceiveAccountData()
	{
		for (String address : new String[] {"http://huntmaster.example.com", "https://127.0.0.1",
			"https://localhost", "https://[::1]", "https://user:secret@huntmaster.example.com",
			"https://huntmaster.example.com:8787", "https://huntmaster.example.com/path",
			"https://huntmaster.example.com?token=x", "https://huntmaster.example.com#fragment", "invalid"})
		{
			assertNull(address, BotEndpoint.resolve(false, address));
		}
	}
}

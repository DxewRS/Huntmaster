package com.huntmaster;

import okhttp3.Request;
import org.junit.Test;
import static org.junit.Assert.*;

public class PluginLinkTest
{
	@Test public void unrelatedOriginsCannotReceiveReports()
	{
		PluginLink link = new PluginLink();

		for (String origin : new String[] {"https://other.example.com", "http://huntmaster.example.com", "https://huntmaster.example.com:8787"})
		{
			try
			{
				link.authenticate(new Request.Builder().url(origin).build(), "https://huntmaster.example.com");
				fail("unexpected origin accepted");
			}
			catch (IllegalArgumentException expected) { assertEquals("unexpected_bot_origin", expected.getMessage()); }
		}
	}
	@Test public void developmentDoesNotInventOrReuseAuthorization()
	{
		Request request = new PluginLink().authenticate(new Request.Builder().url("http://127.0.0.1:8787/health")
			.header("Authorization", "unexpected").build(), "http://127.0.0.1:8787");
		assertNull(request.header("Authorization"));
	}
}

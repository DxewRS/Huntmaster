package com.huntmaster;

import net.runelite.client.eventbus.EventBus;
import org.junit.Test;

public class HuntmasterEventRegistrationTest
{
	@Test
	public void allPluginSubscribersRegisterWithRuneLite()
	{
		EventBus bus = new EventBus();
		HuntmasterPlugin plugin = new HuntmasterPlugin();
		bus.register(plugin);
		bus.unregister(plugin);
	}
}

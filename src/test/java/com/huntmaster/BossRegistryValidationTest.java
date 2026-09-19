package com.huntmaster;

import org.junit.Test;

public class BossRegistryValidationTest
{
	private BossDetector detector(String name, String prefix, int window, String version, BossDetectorType type)
	{
		return new BossDetector(new BossDefinition(name, name.toLowerCase(), prefix, window, version, type));
	}
	@Test public void currentDefinitionsPassProtocolChecks() { BossRegistry.validateDetectors(BossRegistry.createDetectors()); }
	@Test(expected = IllegalStateException.class) public void oversizedWindowRejected()
	{ BossRegistry.validateDetectors(new BossDetector[]{detector("Test", "Your Test:", 129, "v1", BossDetectorType.STANDARD_NPC)}); }
	@Test(expected = IllegalStateException.class) public void invalidVersionRejected()
	{ BossRegistry.validateDetectors(new BossDetector[]{detector("Test", "Your Test:", 10, "contains spaces", BossDetectorType.STANDARD_NPC)}); }
	@Test(expected = IllegalStateException.class) public void missingTypeRejected()
	{ BossRegistry.validateDetectors(new BossDetector[]{detector("Test", "Your Test:", 10, "v1", null)}); }
	@Test(expected = IllegalStateException.class) public void prefixOverlapRejected()
	{ BossRegistry.validateDetectors(new BossDetector[]{detector("First", "Your Test", 10, "v1", BossDetectorType.STANDARD_NPC),
		detector("Second", "Your Test extended", 10, "v1", BossDetectorType.STANDARD_NPC)}); }
	@Test(expected = IllegalStateException.class) public void completionWithoutCounterRejected()
	{ BossRegistry.validateDetectors(new BossDetector[]{detector("Test", "Your Test:", 10, "v1", BossDetectorType.COMPLETION)}); }
}

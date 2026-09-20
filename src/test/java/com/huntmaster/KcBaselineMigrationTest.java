package com.huntmaster;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.runelite.client.RuneLite;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class KcBaselineMigrationTest
{
    private Filepath root;
    private KcBaselineStore store;

    @Before public void isolateRuneLiteHome() throws Exception
    {
        // Gradle sets both properties before RuneLite initializes its static paths.
        // Refuse to mutate anything when run from an unconfigured IDE test runner.
        String configured = System.getProperty("huntmaster.testHome");
        assertNotNull("Run via Gradle with an isolated test home", configured);
        Path home = Paths.get(configured).toAbsolutePath().normalize();
        assertEquals(home, Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize());
        assertEquals(home.resolve(".runelite"), RuneLite.RUNELITE_DIR.toPath().toAbsolutePath().normalize());
        root = Filepath.Unchecked.getRooted(RuneLite.RUNELITE_DIR.toPath());
        root.createDirectories();
        clearFixtures();
        store = new HuntmasterPlugin().createBaselineStore();
    }

    @After public void cleanup() throws Exception
    {
        if (store != null) store.close();
        if (root != null) clearFixtures();
    }

    @Test public void existingLegacyCheckpointMigratesAndSurvivesRestart() throws Exception
    {
        Filepath legacy = root.join("huntmaster", "kc-baselines");
        legacy.createDirectories();
        legacy.joinSegment(KcBaselineStoreTest.filename("account", "boss")).write("257");
        assertFalse(root.join("plugin-data", "huntmaster").exists());
        assertEquals(Integer.valueOf(257), load().get("boss"));
        assertFalse(root.join("huntmaster").exists());
        assertTrue(root.join("plugin-data", "huntmaster", "kc-baselines",
            KcBaselineStoreTest.filename("account", "boss")).exists());
        store.close();
        store = new HuntmasterPlugin().createBaselineStore();
        assertEquals(Integer.valueOf(257), load().get("boss"));
    }

    @Test public void existingNewDirectoryDoesNotOverwriteEitherCopy() throws Exception
    {
        Filepath legacy = root.join("huntmaster", "kc-baselines");
        Filepath current = root.join("plugin-data", "huntmaster", "kc-baselines");
        legacy.createDirectories(); current.createDirectories();
        String name = KcBaselineStoreTest.filename("account", "boss");
        legacy.joinSegment(name).write("257"); current.joinSegment(name).write("290");
        assertEquals(Integer.valueOf(290), load().get("boss"));
        try (java.io.BufferedReader reader = legacy.joinSegment(name).openBufferedReader())
        { assertEquals("257", reader.readLine()); }
    }

    @Test public void freshInstallationSavesAndLoads() throws Exception
    {
        assertTrue(load().isEmpty());
        store.save("account", "boss", 1);
        assertEquals(Integer.valueOf(1), load().get("boss"));
    }

    @Test public void blockedMigrationLeavesLegacyDataAndCanRetry() throws Exception
    {
        Filepath legacy = root.join("huntmaster", "kc-baselines");
        legacy.createDirectories();
        legacy.joinSegment(KcBaselineStoreTest.filename("account", "boss")).write("257");
        root.join("plugin-data").write("blocks destination directory");
        assertTrue(load().isEmpty());
        assertTrue(legacy.exists());
        root.join("plugin-data").delete();
        assertEquals(Integer.valueOf(257), load().get("boss"));
    }

    private Map<String, Integer> load() throws Exception
    {
        CompletableFuture<Map<String, Integer>> loaded = new CompletableFuture<>();
        store.load("account", Collections.singleton("boss"), loaded::complete);
        return loaded.get(5, TimeUnit.SECONDS);
    }

    private void clearFixtures() throws Exception
    {
        for (String name : new String[]{"huntmaster", "plugin-data"})
        {
            Filepath fixture = root.joinSegment(name);
            if (fixture.exists()) fixture.deleteRecursively();
        }
    }
}

package com.huntmaster;

import java.nio.file.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.util.Filepath;
import org.junit.Test;
import static org.junit.Assert.*;

public class KcBaselineStoreTest
{
    @Test public void missingBaselinesRemainNullable()
    {
        assertNull(KcBaselineStore.latest(null, null));
        assertEquals(Integer.valueOf(259), KcBaselineStore.latest(259, null));
        assertEquals(Integer.valueOf(259), KcBaselineStore.latest(null, 259));
    }
    @Test public void observedTotalSurvivesRestartAndAccountsStaySeparate() throws Exception
    {
        Path directory = Files.createTempDirectory("huntmaster-baseline-test");
        KcBaselineStore first = store(directory);
        KcBaselineStore restarted = store(directory);
        try
        {
            first.write("account-one", "giant mole", 256);
            first.write("account-one", "giant mole", 257);
            assertEquals(Integer.valueOf(257), restarted.read("account-one", "giant mole"));
            assertNull(restarted.read("account-two", "giant mole"));
            assertNull(restarted.read("account-one", "brutus"));
            assertEquals(Integer.valueOf(257), KcBaselineStore.latest(256, restarted.read("account-one", "giant mole")));
            assertEquals(Integer.valueOf(258), KcBaselineStore.latest(258, restarted.read("account-one", "giant mole")));
            try (java.util.stream.Stream<Path> files = Files.list(directory)) { assertEquals(1, files.count()); }
        }
        finally
        {
            first.close(); restarted.close();
            try (java.util.stream.Stream<Path> files = Files.list(directory))
            { for (Path file : (Iterable<Path>) files::iterator) Files.delete(file); }
            Files.delete(directory);
        }
    }

    @Test public void rejectsMalformedCheckpointsAndLeavesNoTemporaryFiles() throws Exception
    {
        Path directory = Files.createTempDirectory("huntmaster-baseline-invalid");
        KcBaselineStore store = store(directory);
        Path checkpoint = directory.resolve(filename("account", "boss"));
        try
        {
            for (String invalid : new String[]{"", "bad", "-1", "2147483648", "12345678901234567"})
            {
                Files.writeString(checkpoint, invalid);
                try { store.read("account", "boss"); fail("Accepted invalid checkpoint"); }
                catch (IOException | IllegalArgumentException expected) { }
            }
            store.write("account", "boss", Integer.MAX_VALUE);
            assertEquals(Integer.valueOf(Integer.MAX_VALUE), store.read("account", "boss"));
            try { store.write("account", "boss", -1); fail("Accepted negative total"); }
            catch (IllegalArgumentException expected) { }
            assertEquals(Integer.valueOf(Integer.MAX_VALUE), store.read("account", "boss"));
        }
        finally { store.close(); Filepath.Unchecked.getRooted(directory).deleteRecursively(); }
    }

    @Test public void failedReplacementCleansTemporaryFileWithoutRemovingDestination() throws Exception
    {
        Path directory = Files.createTempDirectory("huntmaster-baseline-failure");
        KcBaselineStore store = store(directory);
        Path destination = directory.resolve(filename("account", "boss"));
        Files.createDirectory(destination);
        Files.writeString(destination.resolve("keep"), "original");
        try
        {
            try { store.write("account", "boss", 123); fail("Replaced a nonempty directory"); }
            catch (IOException expected) { }
            assertEquals("original", Files.readString(destination.resolve("keep")));
            try (java.util.stream.Stream<Path> files = Files.list(directory)) { assertEquals(1, files.count()); }
        }
        finally { store.close(); Filepath.Unchecked.getRooted(directory).deleteRecursively(); }
    }

    @Test public void directoryResolutionIsLazyOnWorkerAndRetriesAfterFailure() throws Exception
    {
        Path directory = Files.createTempDirectory("huntmaster-baseline-worker");
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Thread> resolvingThread = new AtomicReference<>();
        Thread caller = Thread.currentThread();
        KcBaselineStore store = new KcBaselineStore(() -> {
            resolvingThread.set(Thread.currentThread());
            if (calls.incrementAndGet() == 1) throw new IOException("Transient directory failure");
            return Filepath.Unchecked.getRooted(directory);
        });
        try
        {
            assertEquals(0, calls.get());
            assertTrue(load(store).isEmpty());
            assertNotSame(caller, resolvingThread.get());
            store.save("account", "boss", 123);
            assertEquals(Integer.valueOf(123), load(store).get("boss"));
            assertEquals(2, calls.get());
            assertEquals(Integer.valueOf(123), load(store).get("boss"));
            assertEquals(2, calls.get());
        }
        finally { store.close(); Filepath.Unchecked.getRooted(directory).deleteRecursively(); }
    }

    @Test public void encodedNamesStayWithinTheDirectory() throws Exception
    {
        Path directory = Files.createTempDirectory("huntmaster-baseline-names");
        KcBaselineStore store = store(directory);
        try
        {
            store.write("../account\\name", "boss:/name", 7);
            assertEquals(Integer.valueOf(7), store.read("../account\\name", "boss:/name"));
            assertTrue(Files.exists(directory.resolve(filename("../account\\name", "boss:/name"))));
        }
        finally { store.close(); Filepath.Unchecked.getRooted(directory).deleteRecursively(); }
    }

    private static Map<String, Integer> load(KcBaselineStore store) throws Exception
    {
        CompletableFuture<Map<String, Integer>> loaded = new CompletableFuture<>();
        store.load("account", Collections.singleton("boss"), loaded::complete);
        return loaded.get(5, TimeUnit.SECONDS);
    }

    private static KcBaselineStore store(Path directory)
    {
        // Only test fixtures use Unchecked to wrap isolated temporary paths.
        return new KcBaselineStore(() -> Filepath.Unchecked.getRooted(directory));
    }

    static String filename(String profile, String boss)
    {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString((profile + "\n" + boss).getBytes(StandardCharsets.UTF_8)) + ".kc";
    }
}

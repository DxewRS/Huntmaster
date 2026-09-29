package com.huntmaster;

import java.nio.file.Paths;
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
        Filepath directory = temporaryDirectory("huntmaster-baseline-test");
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
            try (java.util.stream.Stream<Filepath> files = directory.walk(1)) { assertEquals(1, files.skip(1).count()); }
        }
        finally
        {
            first.close(); restarted.close();
            directory.deleteRecursively();
        }
    }

    @Test public void rejectsMalformedCheckpointsAndLeavesNoTemporaryFiles() throws Exception
    {
        Filepath directory = temporaryDirectory("huntmaster-baseline-invalid");
        KcBaselineStore store = store(directory);
        Filepath checkpoint = directory.joinSegment(filename("account", "boss"));
        try
        {
            for (String invalid : new String[]{"", "bad", "-1", "2147483648", "12345678901234567"})
            {
                checkpoint.write(invalid);
                try { store.read("account", "boss"); fail("Accepted invalid checkpoint"); }
                catch (IOException | IllegalArgumentException expected) { }
            }
            store.write("account", "boss", Integer.MAX_VALUE);
            assertEquals(Integer.valueOf(Integer.MAX_VALUE), store.read("account", "boss"));
            try { store.write("account", "boss", -1); fail("Accepted negative total"); }
            catch (IllegalArgumentException expected) { }
            assertEquals(Integer.valueOf(Integer.MAX_VALUE), store.read("account", "boss"));
        }
        finally { store.close(); directory.deleteRecursively(); }
    }

    @Test public void failedReplacementCleansTemporaryFileWithoutRemovingDestination() throws Exception
    {
        Filepath directory = temporaryDirectory("huntmaster-baseline-failure");
        KcBaselineStore store = store(directory);
        Filepath destination = directory.joinSegment(filename("account", "boss"));
        destination.createDirectory();
        destination.joinSegment("keep").write("original");
        try
        {
            try { store.write("account", "boss", 123); fail("Replaced a nonempty directory"); }
            catch (IOException expected) { }
            assertEquals("original", readString(destination.joinSegment("keep")));
            try (java.util.stream.Stream<Filepath> files = directory.walk(1)) { assertEquals(1, files.skip(1).count()); }
        }
        finally { store.close(); directory.deleteRecursively(); }
    }

    @Test public void directoryResolutionIsLazyOnWorkerAndRetriesAfterFailure() throws Exception
    {
        Filepath directory = temporaryDirectory("huntmaster-baseline-worker");
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Thread> resolvingThread = new AtomicReference<>();
        Thread caller = Thread.currentThread();
        KcBaselineStore store = new KcBaselineStore(() -> {
            resolvingThread.set(Thread.currentThread());
            if (calls.incrementAndGet() == 1) throw new IOException("Transient directory failure");
            return directory;
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
        finally { store.close(); directory.deleteRecursively(); }
    }

    @Test public void encodedNamesStayWithinTheDirectory() throws Exception
    {
        Filepath directory = temporaryDirectory("huntmaster-baseline-names");
        KcBaselineStore store = store(directory);
        try
        {
            store.write("../account\\name", "boss:/name", 7);
            assertEquals(Integer.valueOf(7), store.read("../account\\name", "boss:/name"));
            assertTrue(directory.joinSegment(filename("../account\\name", "boss:/name")).exists());
        }
        finally { store.close(); directory.deleteRecursively(); }
    }

    private static Map<String, Integer> load(KcBaselineStore store) throws Exception
    {
        CompletableFuture<Map<String, Integer>> loaded = new CompletableFuture<>();
        store.load("account", Collections.singleton("boss"), loaded::complete);
        return loaded.get(5, TimeUnit.SECONDS);
    }

    private static KcBaselineStore store(Filepath directory)
    {
        // Only test fixtures use Unchecked to wrap isolated temporary paths.
        return new KcBaselineStore(() -> directory);
    }

    private static Filepath temporaryDirectory(String prefix) throws IOException
    {
        return Filepath.Unchecked.getRooted(Paths.get(System.getProperty("java.io.tmpdir"))).createTempDir(prefix);
    }
    private static String readString(Filepath file) throws IOException
    {
        try (java.io.BufferedReader reader=file.openBufferedReader()) { return reader.readLine(); }
    }
    @Test public void shutdownDrainsQueuedCheckpointWrites() throws Exception
    {
        Filepath directory=temporaryDirectory("huntmaster-shutdown");
        KcBaselineStore store=store(directory);
        CompletableFuture<Map<String,Integer>> loaded=new CompletableFuture<>();
        try
        {
            store.save("account","boss",123);
            store.load("account",Collections.singleton("boss"),loaded::complete);
            store.close();
            assertEquals(Integer.valueOf(123),loaded.get(5,TimeUnit.SECONDS).get("boss"));
            store.save("account","boss",124); // A stale callback after disable is harmless.
            assertEquals(Integer.valueOf(123),store.read("account","boss"));
        }
        finally { store.close();directory.deleteRecursively(); }
    }
    static String filename(String profile, String boss)
    {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString((profile + "\n" + boss).getBytes(StandardCharsets.UTF_8)) + ".kc";
    }
}

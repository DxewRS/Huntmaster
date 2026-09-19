package com.huntmaster;

import java.nio.file.*;
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
        KcBaselineStore first = new KcBaselineStore(directory);
        KcBaselineStore restarted = new KcBaselineStore(directory);
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
}

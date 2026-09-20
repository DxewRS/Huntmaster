package com.huntmaster;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/** Local observed totals, never credits. All production disk access runs on one worker. */
@Slf4j
final class KcBaselineStore
{
    @FunctionalInterface
    interface DirectorySupplier
    {
        Filepath get() throws IOException;
    }

    private final DirectorySupplier directorySupplier;
    // Resolved lazily on the worker: getPluginDirectory may migrate legacy data.
    private Filepath directory;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "huntmaster-kc-baselines");
        thread.setDaemon(true);
        return thread;
    });

    KcBaselineStore(DirectorySupplier directorySupplier) { this.directorySupplier = directorySupplier; }

    private Filepath directory() throws IOException
    {
        if (directory == null) directory = directorySupplier.get();
        return directory;
    }

    void load(String profile, Collection<String> bosses, Consumer<Map<String, Integer>> callback)
    {
        worker.execute(() -> {
            Map<String, Integer> totals = new HashMap<>();
            for (String boss : bosses)
            {
                try { Integer total = read(profile, boss); if (total != null) totals.put(boss, total); }
                catch (IOException | IllegalArgumentException ex) { log.debug("Could not read Huntmaster KC checkpoint", ex); }
            }
            callback.accept(totals);
        });
    }

    void save(String profile, String boss, int total)
    {
        if (profile == null || total < 0) return;
        worker.execute(() -> {
            try { write(profile, boss, total); }
            catch (IOException ex) { log.debug("Could not save Huntmaster KC checkpoint", ex); }
        });
    }

    Integer read(String profile, String boss) throws IOException
    {
        Filepath file = file(profile, boss);
        if (!file.exists()) return null;
        if (file.size() > 16) throw new IOException("Invalid checkpoint size");
        byte[] bytes;
        try (InputStream input = file.openInputStream())
        {
            // Bound the read even if another process changes the file after size().
            bytes = input.readNBytes(17);
        }
        if (bytes.length > 16) throw new IOException("Invalid checkpoint size");
        int total = Integer.parseInt(new String(bytes, StandardCharsets.UTF_8).trim());
        if (total < 0) throw new IOException("Invalid checkpoint total");
        return total;
    }

    void write(String profile, String boss, int total) throws IOException
    {
        if (total < 0) throw new IllegalArgumentException("Invalid checkpoint total");
        Filepath directory = directory();
        directory.createDirectories();
        Filepath destination = file(profile, boss);
        Filepath temporary = directory.createTempFile("baseline-", ".tmp");
        try
        {
            temporary.write(Integer.toString(total));
            try (java.nio.channels.FileChannel channel = temporary.openFileChannel(StandardOpenOption.WRITE))
            { channel.force(true); }
            try { temporary.moveTo(destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { temporary.moveTo(destination, StandardCopyOption.REPLACE_EXISTING); }
        }
        finally { temporary.deleteIfExists(); }
    }

    static Integer latest(Integer saved, Integer observed)
    {
        if (observed == null) return saved;
        if (saved == null) return observed;
        return Math.max(saved, observed);
    }

    private Filepath file(String profile, String boss) throws IOException
    {
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString((profile + "\n" + boss).getBytes(StandardCharsets.UTF_8));
        return directory().joinSegment(key + ".kc");
    }

    void close() { worker.shutdownNow(); }
}

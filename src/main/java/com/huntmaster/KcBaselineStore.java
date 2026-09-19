package com.huntmaster;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/** Local observed totals, never credits. All production disk access runs on one worker. */
@Slf4j
final class KcBaselineStore
{
    private final Path directory;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "huntmaster-kc-baselines");
        thread.setDaemon(true);
        return thread;
    });

    KcBaselineStore(Path directory) { this.directory = directory; }

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
        Path file = file(profile, boss);
        if (!Files.exists(file)) return null;
        if (Files.size(file) > 16) throw new IOException("Invalid checkpoint size");
        int total = Integer.parseInt(Files.readString(file, StandardCharsets.UTF_8).trim());
        if (total < 0) throw new IOException("Invalid checkpoint total");
        return total;
    }

    void write(String profile, String boss, int total) throws IOException
    {
        if (total < 0) throw new IllegalArgumentException("Invalid checkpoint total");
        Files.createDirectories(directory);
        Path destination = file(profile, boss);
        Path temporary = Files.createTempFile(directory, "baseline-", ".tmp");
        try
        {
            Files.writeString(temporary, Integer.toString(total), StandardCharsets.UTF_8);
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE))
            { channel.force(true); }
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
        }
        finally { Files.deleteIfExists(temporary); }
    }

    static Integer latest(Integer saved, Integer observed)
    {
        if (observed == null) return saved;
        if (saved == null) return observed;
        return Math.max(saved, observed);
    }

    private Path file(String profile, String boss)
    {
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString((profile + "\n" + boss).getBytes(StandardCharsets.UTF_8));
        return directory.resolve(key + ".kc");
    }

    void close() { worker.shutdownNow(); }
}

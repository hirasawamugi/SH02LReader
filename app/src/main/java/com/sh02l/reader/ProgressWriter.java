package com.sh02l.reader;

import com.sh02l.reader.core.DocumentLocation;
import com.sh02l.reader.core.ReadingPosition;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serial durable writes, coalescing only unstarted positions for the same book. */
final class ProgressWriter {
    interface Listener {
        void committed(String id, ReadingPosition position, Exception error);
    }

    private final BookRepository repository;
    private final StorageManager storage;
    private final Listener listener;
    private final LinkedHashMap<String, ReadingPosition> pending = new LinkedHashMap<>();
    private final Set<String> failed = new HashSet<>();
    private final Map<String, Long> generations = new LinkedHashMap<>();
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "reader-progress"));
    private boolean running, closed;

    ProgressWriter(BookRepository repository, Listener listener) {
        this(repository, null, listener);
    }

    ProgressWriter(BookRepository repository, StorageManager storage, Listener listener) {
        this.repository = repository;
        this.storage = storage;
        this.listener = listener;
    }

    void save(String id, long offset) {
        BookRepository.Book book = repository.get(id);
        if (book == null) return;
        save(
                id,
                new ReadingPosition(
                        DocumentLocation.txt(offset),
                        book.length == 0 ? 0 : (double) offset / book.length));
    }

    synchronized void save(String id, ReadingPosition position) {
        if (closed) return;
        pending.put(id, position);
        failed.remove(id);
        start();
    }

    synchronized void retry() {
        if (closed) return;
        failed.clear();
        start();
    }

    synchronized void forget(String id) {
        pending.remove(id);
        failed.remove(id);
        generations.put(id, generations.getOrDefault(id, 0L) + 1);
    }

    private synchronized boolean current(String id, long generation) {
        return generations.getOrDefault(id, 0L) == generation;
    }

    private void start() {
        if (!running && !pending.isEmpty()) {
            running = true;
            executor.execute(this::drain);
        }
    }

    private void drain() {
        while (true) {
            String id;
            ReadingPosition position;
            long generation;
            synchronized (this) {
                Map.Entry<String, ReadingPosition> entry = null;
                for (Map.Entry<String, ReadingPosition> candidate : pending.entrySet()) {
                    if (!failed.contains(candidate.getKey())) {
                        entry = candidate;
                        break;
                    }
                }
                if (entry == null) {
                    running = false;
                    notifyAll();
                    return;
                }
                id = entry.getKey();
                position = entry.getValue();
                generation = generations.getOrDefault(id, 0L);
                pending.remove(id);
            }
            Exception error = null;
            try {
                if (!repository.saveProgressIf(id, position, () -> current(id, generation)))
                    continue;
            } catch (Exception first) {
                error = first;
                if (current(id, generation) && storage != null && storage.releaseReserve()) {
                    // Only one immediate retry. A persistent failure waits for save/retry.
                    try {
                        if (!repository.saveProgressIf(id, position, () -> current(id, generation)))
                            continue;
                        error = null;
                    } catch (Exception again) {
                        error = again;
                    }
                }
            }
            if (error != null)
                synchronized (this) {
                    // A position enqueued during this write is newer and must win.
                    if (current(id, generation) && !pending.containsKey(id)) {
                        pending.put(id, position);
                        failed.add(id);
                    }
                }
            if (!current(id, generation)) continue;
            if (listener != null)
                try {
                    listener.committed(id, position, error);
                } catch (RuntimeException ignored) {
                    /* Listener failures must not strand durable writes. */
                }
        }
    }

    /** Background callers can wait for draining; retained failed positions return false. */
    synchronized boolean awaitIdle(long timeoutMs) throws InterruptedException {
        if (timeoutMs < 0) throw new IllegalArgumentException("timeout");
        long remaining = timeoutMs, started = System.nanoTime();
        while (running && remaining > 0) {
            wait(remaining);
            remaining = timeoutMs - (System.nanoTime() - started) / 1000000L;
        }
        return !running && pending.isEmpty();
    }

    synchronized void close() {
        closed = true;
        executor.shutdown();
        notifyAll();
    }
}

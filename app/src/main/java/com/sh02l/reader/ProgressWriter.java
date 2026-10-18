package com.sh02l.reader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serial durable writes, coalescing only unstarted positions for the same book. */
final class ProgressWriter {
    interface Listener { void committed(String id, long offset, Exception error); }
    private final BookRepository repository;
    private final Listener listener;
    private final LinkedHashMap<String, Long> pending = new LinkedHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "reader-progress"));
    private boolean running, closed;
    ProgressWriter(BookRepository repository, Listener listener) { this.repository = repository; this.listener = listener; }
    synchronized void save(String id, long offset) {
        if (closed) return;
        pending.put(id, offset);
        if (!running) { running = true; executor.execute(this::drain); }
    }
    private void drain() {
        while (true) {
            String id; long offset;
            synchronized (this) {
                if (pending.isEmpty()) { running = false; return; }
                Map.Entry<String, Long> entry = pending.entrySet().iterator().next();
                id = entry.getKey(); offset = entry.getValue(); pending.remove(id);
            }
            Exception error = null;
            try { repository.saveProgress(id, offset); } catch (Exception failed) { error = failed; }
            listener.committed(id, offset, error);
        }
    }
    synchronized void close() { closed = true; executor.shutdown(); }
}

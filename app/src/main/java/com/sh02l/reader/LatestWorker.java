package com.sh02l.reader;

import java.util.concurrent.*;

/** One running request and at most one pending request. New navigation cancels stale work. */
final class LatestWorker implements AutoCloseable {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), runnable -> new Thread(runnable, "reader-work"));
    private Future<?> active;
    synchronized void submit(Runnable task) {
        cancel(); if (!executor.isShutdown()) active = executor.submit(task);
    }
    synchronized void cancel() {
        if (active != null) active.cancel(true);
        executor.getQueue().clear(); active = null;
    }
    @Override public synchronized void close() { cancel(); executor.shutdownNow(); }
}

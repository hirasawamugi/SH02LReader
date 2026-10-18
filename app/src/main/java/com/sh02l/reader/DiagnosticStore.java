package com.sh02l.reader;

import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/** A single writer with one coalesced pending snapshot; key events never wait for disk. */
final class DiagnosticStore {
    interface Listener { void saved(File file, IOException error); }
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final AtomicReference<String> pending = new AtomicReference<>();
    private final File target;
    private boolean running, closed;
    private Listener completion;
    DiagnosticStore(File directory) { target = new File(directory, "latest.txt"); }
    File file() { return target; }

    synchronized void save(String snapshot, Listener listener) {
        if (closed) return;
        pending.set(snapshot);
        if (listener != null) completion = listener;
        if (running) return;
        running = true;
        writer.execute(() -> {
            IOException error = null;
            Listener finished;
            for (;;) {
                String text = pending.getAndSet(null);
                if (text != null) {
                    error = null;
                    AtomicFile file = new AtomicFile(target);
                    FileOutputStream output = null;
                    try {
                        File parent = target.getParentFile();
                        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) throw new IOException("Cannot create diagnostic directory");
                        output = file.startWrite();
                        output.write(text.getBytes(StandardCharsets.UTF_8));
                        file.finishWrite(output);
                    } catch (IOException failure) { error = failure; if (output != null) file.failWrite(output); }
                }
                synchronized (DiagnosticStore.this) {
                    if (pending.get() == null) {
                        running = false; finished = completion; completion = null; break;
                    }
                }
            }
            if (finished != null) finished.saved(target, error);
        });
    }
    synchronized void close() { closed = true; writer.shutdown(); }
}

package com.sh02l.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AtomicFile;

import com.sh02l.reader.core.text.TextImporter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

/** Publishes a bounded processed image before atomically replacing its configuration reference. */
final class BackgroundStore {
    private static final Object TRANSACTION = new Object();
    private boolean cleanupAllowed = true;
    private final File root;
    private final AtomicFile config;

    BackgroundStore(Context context) {
        root = new File(context.getFilesDir(), "appearance");
        config = new AtomicFile(new File(root, "theme.json"));
    }

    synchronized ThemeSpec load(int legacyTheme, int legacyStyle) {
        synchronized (TRANSACTION) {
            try {
                ensure();
                try (InputStream in = config.openRead()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] b = new byte[1024];
                    int n;
                    while ((n = in.read(b)) != -1) {
                        if (out.size() + n > 8192) throw new IOException("Theme too large");
                        out.write(b, 0, n);
                    }
                    return ThemeSpec.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
                }
            } catch (Exception error) {
                return new ThemeSpec(legacyTheme, legacyStyle);
            }
        }
    }

    private void ensure() throws IOException {
        if (Files.isSymbolicLink(root.toPath()) || (!root.isDirectory() && !root.mkdirs()))
            throw new IOException("Appearance storage unavailable");
    }

    File file(String name) throws IOException {
        ensure();
        File file = new File(root, name);
        if (name.isEmpty()
                || !file.getCanonicalFile().getParentFile().equals(root.getCanonicalFile())
                || Files.isSymbolicLink(file.toPath()))
            throw new IOException("Unsafe appearance path");
        return file;
    }

    File copySource(InputStream source) throws IOException {
        File target = null;
        try (InputStream in = source) {
            target = file(".source-" + UUID.randomUUID());
            try (FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[16384];
                long bytes = 0;
                int n;
                while ((n = in.read(buffer)) != -1) {
                    TextImporter.check(null);
                    bytes += n;
                    if (root.getUsableSpace() < n + 8L * 1024 * 1024)
                        throw new IOException("背景存储空间不足");
                    if (bytes > 32L * 1024 * 1024) throw new IOException("背景图片超过 32 MiB");
                    out.write(buffer, 0, n);
                }
                out.getFD().sync();
            }
            CoverDecoder.inspect(target);
            return target;
        } catch (IOException | RuntimeException error) {
            if (target != null) target.delete();
            throw error;
        }
    }

    synchronized ThemeSpec save(ThemeSpec value, Bitmap processed) throws IOException {
        synchronized (TRANSACTION) {
            ensure();
            ThemeSpec next = value.copy();
            File newAsset = null;
            FileOutputStream out = null;
            try {
                if (processed != null) {
                    newAsset = file("bg-" + UUID.randomUUID() + ".png");
                    try (FileOutputStream image = new FileOutputStream(newAsset)) {
                        if (!processed.compress(Bitmap.CompressFormat.PNG, 100, image))
                            throw new IOException("Cannot save background");
                        image.getFD().sync();
                    }
                    DurableFiles.syncDirectory(root);
                    next.asset = newAsset.getName();
                }
                TextImporter.check(null);
                out = config.startWrite();
                out.write(next.json().getBytes(StandardCharsets.UTF_8));
                config.finishWrite(out);
                out = null;
                // AtomicFile fsyncs the content; directory publication is also persisted before
                // cleanup.
                try {
                    DurableFiles.syncDirectory(root);
                } catch (IOException ignored) {
                    cleanupAllowed = false;
                    // Configuration already committed: retain the old image until next startup.
                }
                return next;
            } catch (Exception error) {
                if (out != null) config.failWrite(out);
                throw new IOException("外观保存失败，旧设置仍可恢复", error);
            }
        }
    }

    synchronized void cleanup(String retained) throws IOException {
        synchronized (TRANSACTION) {
            if (!cleanupAllowed) return;
            ensure();
            File[] files = root.listFiles();
            if (files == null) throw new IOException("Cannot inspect appearance storage");
            for (File file : files) {
                String name = file.getName();
                if ((name.startsWith(".source-") || name.matches("bg-[a-f0-9-]{36}\\.png"))
                        && !name.equals(retained)
                        && Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))
                    Files.delete(file.toPath());
            }
        }
    }

    long bytes() {
        long bytes = 0;
        File[] files = root.listFiles();
        if (files != null) for (File file : files) if (file.isFile()) bytes += file.length();
        return bytes;
    }
}

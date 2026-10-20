package com.sh02l.reader;

import android.content.Context;

import java.io.*;
import java.nio.file.*;

/** Owns the private book namespace and enforces non-following file operations. */
final class BookFileStore {
    final File root;

    BookFileStore(Context context) {
        root = new File(context.getFilesDir(), "books");
    }

    void ensure() throws IOException {
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Cannot create library");
        if (Files.isSymbolicLink(root.toPath())) throw new IOException("Unsafe library path");
    }

    File directory(String id) {
        if (id == null || !id.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("book id");
        return new File(root, id);
    }

    void check(File folder) throws IOException {
        if (Files.isSymbolicLink(root.toPath())
                || !Files.isDirectory(folder.toPath(), LinkOption.NOFOLLOW_LINKS)
                || !folder.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))
            throw new IOException("Unsafe private book directory");
    }

    static boolean regularChild(File folder, File file) throws IOException {
        return Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)
                && file.getCanonicalFile().getParentFile().equals(folder.getCanonicalFile());
    }

    File originalIn(File folder) throws IOException {
        check(folder);
        File file = new File(folder, "original.bin");
        if (!regularChild(folder, file)) throw new IOException("Private original unavailable");
        return file;
    }

    void remove(File folder) throws IOException {
        if (!Files.exists(folder.toPath(), LinkOption.NOFOLLOW_LINKS)) return;
        check(folder);
        removeTree(folder);
    }

    private static void removeTree(File folder) throws IOException {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(folder.toPath())) {
            for (Path child : children) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) removeTree(child.toFile());
                else Files.delete(child);
            }
        }
        Files.delete(folder.toPath());
    }
}

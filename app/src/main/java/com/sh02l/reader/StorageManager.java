package com.sh02l.reader;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Explicit storage actions operate only on this application's private copies. */
final class StorageManager {
    private static final int RESERVE_BYTES = 256 * 1024;
    static final class BookUsage {
        final BookRepository.Book book;
        final long originalBytes, cacheBytes, coverBytes;
        BookUsage(BookRepository.Book book, long originalBytes, long cacheBytes, long coverBytes) {
            this.book = book; this.originalBytes = originalBytes; this.cacheBytes = cacheBytes; this.coverBytes = coverBytes;
        }
        long totalBytes() { return originalBytes + cacheBytes + coverBytes; }
    }
    static final class Snapshot {
        final long originals, caches, covers, imports, free;
        final List<BookUsage> books;
        Snapshot(long originals, long caches, long covers, long imports, long free, List<BookUsage> books) {
            this.originals = originals; this.caches = caches; this.covers = covers; this.imports = imports; this.free = free;
            this.books = Collections.unmodifiableList(new ArrayList<>(books));
        }
    }
    private final Context context;
    private final BookRepository repository;
    private final File files, coverRoot, reserve;
    StorageManager(Context context, BookRepository repository) {
        this.context = context.getApplicationContext(); this.repository = repository;
        files = this.context.getFilesDir(); coverRoot = new File(files, "covers"); reserve = new File(files, "progress-reserve.bin");
    }
    Snapshot scan() throws IOException {
        long originals = 0, caches = 0, covers = 0, imports = 0;
        List<BookUsage> usage = new ArrayList<>();
        synchronized (repository) {
            for (BookRepository.Book book : repository.list()) {
                interrupted();
                BookUsage row = usage(book); usage.add(row);
                originals += row.originalBytes; caches += row.cacheBytes; covers += row.coverBytes;
            }
        }
        for (File file : listImportFiles()) imports += file.length();
        usage.sort((left, right) -> {
            int order = Long.compare(right.totalBytes(), left.totalBytes());
            return order != 0 ? order : left.book.id.compareTo(right.book.id);
        });
        return new Snapshot(originals, caches, covers, imports, files.getUsableSpace(), usage);
    }
    long bookBytes(String id) throws IOException {
        synchronized (repository) {
            BookRepository.Book book = repository.get(id);
            return book == null ? 0 : usage(book).totalBytes();
        }
    }
    private BookUsage usage(BookRepository.Book book) throws IOException {
        File folder = repository.directory(book.id);
        long original = childBytes(folder, new File(folder, "original.bin")), cache = 0;
        for (String name : BookRepository.CACHE_NAMES) cache += childBytes(folder, new File(folder, name));
        File cover = new File(coverRoot, book.id);
        long coverBytes = 0;
        if (!Files.isSymbolicLink(coverRoot.toPath()) && Files.isDirectory(cover.toPath(), LinkOption.NOFOLLOW_LINKS)
                && cover.getCanonicalFile().getParentFile().equals(coverRoot.getCanonicalFile())) coverBytes = treeBytes(cover);
        return new BookUsage(book, original, cache, coverBytes);
    }
    private static long childBytes(File folder, File file) throws IOException {
        if (Files.isSymbolicLink(folder.toPath()) || Files.isSymbolicLink(folder.getParentFile().toPath())) return 0;
        return BookRepository.regularChild(folder, file) ? file.length() : 0;
    }
    private static long treeBytes(File folder) throws IOException {
        long bytes = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder.toPath())) {
            for (Path entry : entries) {
                interrupted();
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) bytes += treeBytes(entry.toFile());
                else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) bytes += entry.toFile().length();
            }
        }
        return bytes;
    }
    List<File> listImportFiles() throws IOException {
        List<File> result = new ArrayList<>();
        Set<File> seen = new HashSet<>();
        for (File root : importRoots()) {
            if (!Files.isDirectory(root.toPath(), LinkOption.NOFOLLOW_LINKS)) continue;
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(root.toPath())) {
                for (Path entry : entries) {
                    interrupted(); File file = entry.toFile();
                    if (BookRepository.regularChild(root, file) && seen.add(file.getCanonicalFile())) result.add(file);
                }
            }
        }
        result.sort((left, right) -> {
            int order = left.getName().compareToIgnoreCase(right.getName());
            return order != 0 ? order : left.getAbsolutePath().compareTo(right.getAbsolutePath());
        });
        return result;
    }
    private List<File> importRoots() throws IOException {
        List<File> roots = new ArrayList<>();
        File[] candidates = context.getExternalFilesDirs("import");
        if (candidates == null) return roots;
        for (File root : candidates) {
            if (root == null || Files.isSymbolicLink(root.toPath())) continue;
            File canonical = root.getCanonicalFile();
            if (!roots.contains(canonical)) roots.add(canonical);
        }
        return roots;
    }
    private File ownedImport(File file, List<File> roots) throws IOException {
        if (file == null || !Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Import selection is not a regular file");
        File canonical = file.getCanonicalFile();
        if (!roots.contains(canonical.getParentFile())) throw new IOException("Import cleanup is outside the application import directory");
        return canonical;
    }
    long deleteImportFiles(List<File> selected) throws IOException {
        if (selected == null) throw new IOException("No import selection");
        List<File> roots = importRoots();
        Set<File> files = new LinkedHashSet<>();
        // Validate the whole selection before making any destructive change.
        for (File file : selected) files.add(ownedImport(file, roots));
        long bytes = 0;
        for (File file : files) {
            interrupted(); ownedImport(file, roots);
            long length = file.length();
            if (!file.delete()) throw new IOException("Cannot remove selected import file");
            bytes += length;
        }
        for (File root : roots) if (root.isDirectory()) DurableFiles.syncDirectory(root);
        return bytes;
    }
    long clearCaches(String activeBookId) throws IOException {
        long bytes = 0;
        synchronized (repository) {
            for (BookRepository.Book book : repository.list()) {
                interrupted();
                if (book.id.equals(activeBookId)) continue;
                // A damaged/missing original must never lose its last usable text cache.
                repository.original(book.id);
                File folder = repository.directory(book.id);
                for (String name : BookRepository.CACHE_NAMES) {
                    File file = new File(folder, name);
                    if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) continue;
                    if (!BookRepository.regularChild(folder, file)) throw new IOException("Unsafe private cache path");
                    long length = file.length();
                    if (!file.delete()) throw new IOException("Cannot clear text cache");
                    bytes += length;
                }
                DurableFiles.syncDirectory(folder);
            }
        }
        return bytes;
    }
    synchronized void prepareReserve() throws IOException {
        if (!files.isDirectory() && !files.mkdirs()) throw new IOException("Private storage unavailable");
        if (Files.isSymbolicLink(reserve.toPath())) throw new IOException("Unsafe progress reserve");
        if (reserve.isFile() && reserve.length() == RESERVE_BYTES) return;
        File staging = new File(files, ".progress-reserve.tmp");
        if (Files.isSymbolicLink(staging.toPath())) throw new IOException("Unsafe progress reserve staging");
        try {
            // Writing every block allocates real storage; setLength alone may create a sparse file.
            try (FileOutputStream out = new FileOutputStream(staging)) {
                byte[] block = new byte[16384];
                for (int written = 0; written < RESERVE_BYTES; written += block.length) { interrupted(); out.write(block); }
                out.getFD().sync();
            }
            if (!staging.renameTo(reserve)) throw new IOException("Cannot publish progress reserve");
            DurableFiles.syncDirectory(files);
        } finally { if (staging.exists()) staging.delete(); }
    }
    synchronized boolean releaseReserve() {
        if (!Files.isRegularFile(reserve.toPath(), LinkOption.NOFOLLOW_LINKS)) return false;
        if (!reserve.delete()) return false;
        try { DurableFiles.syncDirectory(files); } catch (IOException ignored) { /* Freed blocks are already available to SQLite. */ }
        return true;
    }
    private static void interrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Storage action cancelled");
    }
}

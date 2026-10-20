package com.sh02l.reader.epub;

import com.sh02l.reader.core.*;

import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** EPUB positions retain resource identity and content offset across repagination. */
public final class EpubDocumentSession implements DocumentSession {
    final EpubArchive archive;
    private final Map<String, EpubArchive.TextIndex> indexes =
            new LinkedHashMap<String, EpubArchive.TextIndex>(4, .75f, true) {
                protected boolean removeEldestEntry(
                        Map.Entry<String, EpubArchive.TextIndex> entry) {
                    return size() > 3;
                }
            };

    public EpubDocumentSession(File file) throws IOException {
        archive = new EpubArchive(file);
    }

    public String format() {
        return "epub";
    }

    public Set<DocumentCapability> capabilities() {
        return EnumSet.of(
                DocumentCapability.CONTENTS,
                DocumentCapability.SEARCH,
                DocumentCapability.REFLOW,
                DocumentCapability.LINKS);
    }

    public static DocumentLocation location(String path, int offset, String fragment) {
        return new DocumentLocation(
                "epub",
                1,
                path
                        + '\u001f'
                        + Math.max(0, offset)
                        + '\u001f'
                        + (fragment == null ? "" : fragment));
    }

    static String[] parts(DocumentLocation location) {
        if (!"epub".equals(location.format) || location.version != 1)
            throw new IllegalArgumentException("Unsupported EPUB location");
        String[] parts = location.payload.split("\u001f", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Invalid EPUB location");
        Integer.parseInt(parts[1]);
        return parts;
    }

    public List<ContentEntry> contents() {
        List<ContentEntry> result = new ArrayList<>();
        for (EpubArchive.Part part : archive.navigation)
            result.add(new ContentEntry(part.title, location(part.href, 0, part.fragment)));
        return result;
    }

    public List<ContentEntry> links(DocumentLocation at) throws IOException {
        List<ContentEntry> result = new ArrayList<>();
        for (EpubArchive.Part link : archive.links(parts(at)[0]))
            result.add(new ContentEntry(link.title, location(link.href, 0, link.fragment)));
        return result;
    }

    public DocumentLocation locationAt(double progression) {
        double value = Math.max(0, Math.min(.999999, progression)) * archive.spine.size();
        int chapter = (int) value;
        return location(archive.spine.get(chapter), 0, "@" + (value - chapter));
    }

    public double progressionAt(DocumentLocation location) {
        String[] parts = parts(location);
        int index = Math.max(0, archive.spine.indexOf(parts[0]));
        EpubArchive.TextIndex text = index(parts[0]);
        double fraction =
                parts[2].startsWith("@")
                        ? Double.parseDouble(parts[2].substring(1))
                        : text == null || text.length == 0
                                ? 0
                                : (double) offset(parts) / text.length;
        return Math.max(0, Math.min(1, (index + fraction) / archive.spine.size()));
    }

    void prepareContent(String path, byte[] bytes) throws IOException {
        EpubArchive.TextIndex parsed = archive.index(bytes);
        synchronized (indexes) {
            indexes.put(path, parsed);
        }
    }

    private EpubArchive.TextIndex index(String path) {
        synchronized (indexes) {
            return indexes.get(path);
        }
    }

    private int offset(String[] parts) {
        EpubArchive.TextIndex text = index(parts[0]);
        if (text != null && !parts[2].isEmpty() && !parts[2].startsWith("@"))
            return text.anchors.getOrDefault(parts[2], Integer.parseInt(parts[1]));
        return Integer.parseInt(parts[1]);
    }

    @Override
    public int compare(DocumentLocation first, DocumentLocation second) {
        String[] a = parts(first), b = parts(second);
        int chapter = Integer.compare(archive.spine.indexOf(a[0]), archive.spine.indexOf(b[0]));
        return chapter != 0 ? chapter : Integer.compare(offset(a), offset(b));
    }

    public SearchBatch search(
            String query, DocumentLocation from, int limit, BooleanSupplier cancelled)
            throws IOException {
        if (query == null || query.isEmpty() || query.length() > 256 || limit < 1 || limit > 100)
            throw new IllegalArgumentException("search");
        String[] start = from == null ? null : parts(from);
        int first = start == null ? 0 : Math.max(0, archive.spine.indexOf(start[0]));
        List<ContentEntry> hits = new ArrayList<>();
        for (int i = first; i < archive.spine.size(); i++) {
            check(cancelled);
            String href = archive.spine.get(i), text = archive.text(href);
            int cursor = i == first && start != null ? Math.max(0, Integer.parseInt(start[1])) : 0;
            while (cursor <= text.length()) {
                check(cancelled);
                int found = text.indexOf(query, cursor);
                if (found < 0) break;
                DocumentLocation position = location(href, found, "");
                if (hits.size() == limit) return new SearchBatch(hits, position, true);
                hits.add(
                        new ContentEntry(
                                text.substring(
                                                Math.max(0, found - 18),
                                                Math.min(
                                                        text.length(), found + query.length() + 42))
                                        .replace('\n', ' '),
                                position));
                cursor = found + query.length();
            }
        }
        return new SearchBatch(
                hits,
                location(archive.spine.get(archive.spine.size() - 1), Integer.MAX_VALUE, ""),
                false);
    }

    private static void check(BooleanSupplier cancelled) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || cancelled != null && cancelled.getAsBoolean())
            throw new InterruptedIOException("EPUB search cancelled");
    }

    public void close() {
        archive.close();
    }
}

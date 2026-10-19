package com.sh02l.reader.core.text;

import java.io.*;
import java.util.*;

/** Bounded, case-sensitive literal search of the normalized UTF-16LE cache. */
public final class TextSearcher {
    public static final int MAX_QUERY_LENGTH = 256, MAX_BATCH_SIZE = 100;
    private static final int SNIPPET_LENGTH = 60;

    public static final class Match {
        public final long offset;
        public final String snippet;
        private Match(long offset, String snippet) { this.offset = offset; this.snippet = snippet; }
    }
    public static final class Batch {
        public final List<Match> matches;
        public final long nextOffset;
        public final boolean hasMore;
        private Batch(List<Match> matches, long nextOffset, boolean hasMore) {
            this.matches = Collections.unmodifiableList(new ArrayList<>(matches));
            this.nextOffset = nextOffset; this.hasMore = hasMore;
        }
    }
    private TextSearcher() { }

    /**
     * Matches do not overlap. Pass nextOffset to continue: it is immediately after the last
     * delivered match when hasMore is true, or length after the search is exhausted.
     * Query/range/limit errors throw IllegalArgumentException; inconsistent cache lengths throw IOException.
     * At most 8192 text units, 256 query units and limit snippets are retained in memory.
     */
    public static Batch find(File normalized, long length, String query, long fromOffset, int limit,
                             TextImporter.Observer observer) throws IOException {
        validate(normalized, length, query, fromOffset, limit);
        check(observer);
        List<Match> matches = new ArrayList<>(limit);
        try (RandomAccessFile input = new RandomAccessFile(normalized, "r")) {
            if (input.length() != length * 2) throw new IOException("Invalid normalized text length");
            progress(observer, fromOffset);
            if (query.length() > length - fromOffset) return new Batch(matches, length, false);
            int[] failure = failure(query);
            byte[] bytes = new byte[Utf16File.BLOCK * 2];
            int matched = 0;
            long cursor = fromOffset, nextOffset = fromOffset;
            while (cursor < length) {
                check(observer);
                int count = (int) Math.min(Utf16File.BLOCK, length - cursor);
                input.seek(cursor * 2); input.readFully(bytes, 0, count * 2);
                for (int i = 0; i < count; i++) {
                    char value = (char) ((bytes[i * 2] & 255) | ((bytes[i * 2 + 1] & 255) << 8));
                    while (matched > 0 && value != query.charAt(matched)) matched = failure[matched - 1];
                    if (value == query.charAt(matched)) matched++;
                    if (matched == query.length()) {
                        check(observer);
                        long end = cursor + i + 1;
                        // One-match lookahead makes hasMore exact without consuming that result.
                        if (matches.size() == limit) return new Batch(matches, nextOffset, true);
                        long offset = end - query.length();
                        matches.add(new Match(offset, snippet(input, length, offset)));
                        nextOffset = end;
                        matched = 0;
                    }
                }
                cursor += count;
                progress(observer, cursor);
            }
            return new Batch(matches, length, false);
        }
    }
    private static void validate(File file, long length, String query, long start, int limit) {
        if (file == null || length < 0 || length > Long.MAX_VALUE / 2 || start < 0 || start > length
                || query == null || query.isEmpty() || query.length() > MAX_QUERY_LENGTH
                || limit < 1 || limit > MAX_BATCH_SIZE) throw new IllegalArgumentException("search range or query");
        for (int i = 0; i < query.length(); i++) {
            char value = query.charAt(i);
            if (Character.isHighSurrogate(value)) {
                if (++i >= query.length() || !Character.isLowSurrogate(query.charAt(i)))
                    throw new IllegalArgumentException("Malformed UTF-16 query");
            } else if (Character.isLowSurrogate(value)) throw new IllegalArgumentException("Malformed UTF-16 query");
        }
    }
    private static int[] failure(String query) {
        int[] table = new int[query.length()];
        for (int i = 1, matched = 0; i < query.length(); i++) {
            while (matched > 0 && query.charAt(i) != query.charAt(matched)) matched = table[matched - 1];
            if (query.charAt(i) == query.charAt(matched)) matched++;
            table[i] = matched;
        }
        return table;
    }
    private static String snippet(RandomAccessFile input, long length, long offset) throws IOException {
        long start = Math.max(0, offset - 20);
        int count = (int) Math.min(SNIPPET_LENGTH, length - start);
        byte[] bytes = new byte[count * 2];
        input.seek(start * 2); input.readFully(bytes);
        char[] chars = new char[count];
        for (int i = 0; i < count; i++) chars[i] = (char) ((bytes[i * 2] & 255) | ((bytes[i * 2 + 1] & 255) << 8));
        int first = count > 0 && Character.isLowSurrogate(chars[0]) ? 1 : 0;
        int last = count > first && Character.isHighSurrogate(chars[count - 1]) ? count - 1 : count;
        StringBuilder result = new StringBuilder(count);
        boolean space = false;
        for (int i = first; i < last; i++) {
            char value = chars[i];
            if (Character.isWhitespace(value) || Character.isSpaceChar(value)) space = result.length() > 0;
            else { if (space) result.append(' '); result.append(value); space = false; }
        }
        return result.toString();
    }
    private static void progress(TextImporter.Observer observer, long offset) throws InterruptedIOException {
        check(observer);
        if (observer != null) observer.progress("search", offset * 2);
        check(observer);
    }
    private static void check(TextImporter.Observer observer) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || (observer != null && observer.cancelled()))
            throw new InterruptedIOException("Search cancelled");
    }
}

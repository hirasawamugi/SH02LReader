package com.sh02l.reader.txt;

import com.sh02l.reader.core.*;
import com.sh02l.reader.core.text.TextImporter;
import com.sh02l.reader.core.text.TextSearcher;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Single-worker TXT session. Positions and length count normalized UTF-16 units. */
public final class TxtDocumentSession implements DocumentSession {
    private final File normalized;
    private final long length;
    private final TxtPaginator paginator;
    private final List<ContentEntry> chapters = new ArrayList<>();

    public TxtDocumentSession(File normalized, long length) {
        this.normalized = normalized;
        this.length = length;
        paginator = new TxtPaginator(normalized, length);
    }

    public TxtDocumentSession(File normalized, TextImporter.Result index) {
        this(normalized, index.length);
        for (TextImporter.Chapter entry : index.chapters)
            chapters.add(new ContentEntry(entry.title, DocumentLocation.txt(entry.offset)));
    }

    @Override
    public String format() {
        return "txt";
    }

    public long length() {
        return length;
    }

    @Override
    public Set<DocumentCapability> capabilities() {
        return EnumSet.of(
                DocumentCapability.CONTENTS,
                DocumentCapability.SEARCH,
                DocumentCapability.REFLOW,
                DocumentCapability.ENCODING);
    }

    @Override
    public List<ContentEntry> contents() {
        return Collections.unmodifiableList(chapters);
    }

    @Override
    public DocumentLocation locationAt(double progression) {
        return DocumentLocation.txt(
                Math.max(
                        0,
                        Math.min(
                                Math.max(0, length - 1),
                                Math.round(Math.max(0, Math.min(1, progression)) * length))));
    }

    @Override
    public double progressionAt(DocumentLocation location) {
        return length == 0 ? 0 : Math.max(0, Math.min(1, (double) location.textOffset() / length));
    }

    @Override
    public SearchBatch search(
            String query, DocumentLocation from, int limit, BooleanSupplier cancelled)
            throws IOException {
        TextSearcher.Batch batch =
                TextSearcher.find(
                        normalized,
                        length,
                        query,
                        from == null ? 0 : from.textOffset(),
                        limit,
                        new TextImporter.Observer() {
                            public boolean cancelled() {
                                return cancelled != null && cancelled.getAsBoolean();
                            }

                            public void progress(String phase, long bytes) {}
                        });
        List<ContentEntry> entries = new ArrayList<>();
        for (TextSearcher.Match match : batch.matches)
            entries.add(new ContentEntry(match.snippet, DocumentLocation.txt(match.offset)));
        return new SearchBatch(entries, DocumentLocation.txt(batch.nextOffset), batch.hasMore);
    }

    @Override
    public void close() {}

    public File normalizedFile() {
        return normalized;
    }

    public TxtPaginator.Page seek(long offset, TxtPaginator.Spec spec) throws IOException {
        return paginator.seek(offset, spec);
    }

    public TxtPaginator.Page move(TxtPaginator.Page page, int direction) throws IOException {
        return paginator.move(page, direction);
    }
}

package com.sh02l.reader.core;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * A format engine owns page layout and movement; the application owns navigation and persistence.
 */
public interface DocumentSession extends AutoCloseable {
    String format();

    Set<DocumentCapability> capabilities();

    List<ContentEntry> contents();

    SearchBatch search(String query, DocumentLocation from, int limit, BooleanSupplier cancelled)
            throws IOException;

    DocumentLocation locationAt(double progression);

    double progressionAt(DocumentLocation location);

    default List<ContentEntry> links(DocumentLocation location) throws IOException {
        return java.util.Collections.emptyList();
    }

    @Override
    void close();
}

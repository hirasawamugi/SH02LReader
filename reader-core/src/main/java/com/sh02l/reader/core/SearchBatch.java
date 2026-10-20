package com.sh02l.reader.core;

import java.util.*;

public final class SearchBatch {
    public final List<ContentEntry> matches;
    public final DocumentLocation next;
    public final boolean hasMore;

    public SearchBatch(List<ContentEntry> matches, DocumentLocation next, boolean more) {
        this.matches = Collections.unmodifiableList(new ArrayList<>(matches));
        this.next = next;
        hasMore = more;
    }
}

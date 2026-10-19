package com.sh02l.reader.txt;

import com.sh02l.reader.core.DocumentSession;
import java.io.File;
import java.io.IOException;

/** Single-worker TXT session. Positions and length count normalized UTF-16 units. */
public final class TxtDocumentSession implements DocumentSession<TxtPaginator.Page, TxtPaginator.Spec> {
    private final File normalized;
    private final long length;
    private final TxtPaginator paginator;

    public TxtDocumentSession(File normalized, long length) {
        this.normalized = normalized; this.length = length;
        paginator = new TxtPaginator(normalized, length);
    }
    @Override public String format() { return "txt"; }
    @Override public long length() { return length; }
    public File normalizedFile() { return normalized; }
    @Override public TxtPaginator.Page seek(long offset, TxtPaginator.Spec spec) throws IOException {
        return paginator.seek(offset, spec);
    }
    @Override public TxtPaginator.Page move(TxtPaginator.Page page, int direction) throws IOException {
        return paginator.move(page, direction);
    }
}

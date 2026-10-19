package com.sh02l.reader.core;

import java.io.IOException;

/** A format engine owns page layout and movement; the application owns navigation and persistence. */
public interface DocumentSession<P, S> {
    String format();
    long length();
    P seek(long offset, S spec) throws IOException;
    P move(P page, int direction) throws IOException;
}

package com.sh02l.reader.core;

import java.util.Objects;

/** Versioned, format-owned position. Page numbers and display percentages are not locations. */
public final class DocumentLocation {
    public final String format, payload;
    public final int version;

    public DocumentLocation(String format, int version, String payload) {
        if (format == null
                || !format.matches("[a-z][a-z0-9-]{0,31}")
                || version < 1
                || payload == null
                || payload.length() > 8192) throw new IllegalArgumentException("location");
        if ("txt".equals(format))
            try {
                if (version != 1 || Long.parseLong(payload) < 0)
                    throw new IllegalArgumentException("text position");
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("text position", e);
            }
        this.format = format;
        this.version = version;
        this.payload = payload;
    }

    public static DocumentLocation txt(long offset) {
        return new DocumentLocation("txt", 1, Long.toString(Math.max(0, offset)));
    }

    public long textOffset() {
        if (!"txt".equals(format)) throw new IllegalStateException("Not a TXT position");
        return Long.parseLong(payload);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DocumentLocation)) return false;
        DocumentLocation p = (DocumentLocation) other;
        return version == p.version && format.equals(p.format) && payload.equals(p.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, version, payload);
    }

    @Override
    public String toString() {
        return format + "-v" + version + ":" + payload;
    }
}

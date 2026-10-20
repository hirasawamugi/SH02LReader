package com.sh02l.reader.core;

/** A save request couples its canonical location with the derived display progression. */
public final class ReadingPosition {
    public final DocumentLocation location;
    public final double progression;

    public ReadingPosition(DocumentLocation location, double progression) {
        this.location = location;
        this.progression = Double.isFinite(progression) ? Math.max(0, Math.min(1, progression)) : 0;
    }
}

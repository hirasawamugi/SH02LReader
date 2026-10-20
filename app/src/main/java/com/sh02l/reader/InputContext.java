package com.sh02l.reader;

import com.sh02l.reader.core.DocumentCapability;

import java.util.Collections;
import java.util.Set;

/** Snapshot shared by routing, menu composition and visible hints. */
final class InputContext {
    final NavigationState.Kind kind, base;
    final boolean general, bookSelected, cover, canReturn;
    final String format;
    final Set<DocumentCapability> capabilities;

    InputContext(
            NavigationState.Kind kind,
            NavigationState.Kind base,
            boolean general,
            boolean bookSelected,
            String format,
            Set<DocumentCapability> capabilities,
            boolean cover,
            boolean canReturn) {
        this.kind = kind;
        this.base = base;
        this.general = general;
        this.bookSelected = bookSelected;
        this.format = format;
        this.capabilities = capabilities == null ? Collections.emptySet() : capabilities;
        this.cover = cover;
        this.canReturn = canReturn;
    }
}

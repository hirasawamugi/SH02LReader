package com.sh02l.reader.core;

/** One descriptor for menus and key hints; application controllers own execution. */
public final class UiAction {
    public final String id, title;
    public UiAction(String id, String title) { this.id = id; this.title = title; }
}

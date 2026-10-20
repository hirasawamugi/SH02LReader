package com.sh02l.reader.core;

public enum Action {
    NONE(false),
    UP(true),
    DOWN(true),
    LEFT(true),
    RIGHT(true),
    ACTIVATE(false),
    PREVIOUS_PAGE(true),
    NEXT_PAGE(true),
    PREVIOUS_CHAPTER(false),
    NEXT_CHAPTER(false),
    MENU(false),
    GENERAL_MENU(false),
    BACK(false),
    HOME(false),
    IMPORT(false),
    DIAGNOSTICS(false),
    HELP(false),
    CONTENTS(false),
    SETTINGS(false),
    BOOKMARKS(false),
    SMALLER(true),
    LARGER(true),
    THEME(false),
    DIMMER(true),
    BRIGHTER(true),
    INFO(false);

    public final boolean repeatable;

    Action(boolean repeatable) {
        this.repeatable = repeatable;
    }
}

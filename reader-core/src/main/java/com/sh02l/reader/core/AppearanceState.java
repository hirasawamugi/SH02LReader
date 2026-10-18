package com.sh02l.reader.core;

/** Immutable independent appearance choices. */
public final class AppearanceState {
    public static final int WARM=0, LIGHT=1, DARK=2, TEXT=0, ICON=1, COVER=2;
    public final int uiTheme, readerTheme, bookStyle;
    public AppearanceState(int uiTheme, int readerTheme, int bookStyle) {
        this.uiTheme=valid(uiTheme,WARM); this.readerTheme=valid(readerTheme,WARM); this.bookStyle=valid(bookStyle,ICON);
    }
    private static int valid(int value,int fallback) { return value>=0 && value<=2 ? value : fallback; }
    public AppearanceState withUiTheme(int value) { return new AppearanceState(value,readerTheme,bookStyle); }
    public AppearanceState withReaderTheme(int value) { return new AppearanceState(uiTheme,value,bookStyle); }
    public AppearanceState withBookStyle(int value) { return new AppearanceState(uiTheme,readerTheme,value); }
}

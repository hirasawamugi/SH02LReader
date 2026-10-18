package com.sh02l.reader.core;

/** Opaque ARGB roles shared by every native page. */
public final class Palette {
    public final int background,surface,foreground,secondary,accent,selected,divider;
    private Palette(int background,int surface,int foreground,int secondary,int accent,int selected,int divider) {
        this.background=background; this.surface=surface; this.foreground=foreground; this.secondary=secondary;
        this.accent=accent; this.selected=selected; this.divider=divider;
    }
    public static Palette forTheme(int theme) {
        if(theme==AppearanceState.DARK)return new Palette(0xff252421,0xff302e29,0xffeae6dd,0xffb7b1a6,0xff93b4ce,0xff35414a,0xff4a4740);
        if(theme==AppearanceState.LIGHT)return new Palette(0xfffafaf7,0xfffffefa,0xff2d3033,0xff696a66,0xff3c6688,0xffe4ebf0,0xffdcded8);
        return new Palette(0xfff4f1ea,0xfffffcf6,0xff2d3033,0xff696a66,0xff3c6688,0xffe4ebf0,0xffd8d4c9);
    }
}

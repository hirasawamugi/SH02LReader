package com.sh02l.reader.txt;

import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import com.sh02l.reader.core.text.Utf16File;
import java.io.*;
import java.util.*;

/** Single-worker paginator. Fixed bounded blocks make next/previous reproducible without whole-book layout. */
public final class TxtPaginator {
    public static final class Spec {
        public final int width, height, color;
        public final float textSize, lineExtra;
        public final Typeface typeface;
        public Spec(int width, int height, float textSize, float lineExtra, int color) {
            this(width, height, textSize, lineExtra, color, Typeface.DEFAULT);
        }
        public Spec(int width, int height, float textSize, float lineExtra, int color, Typeface typeface) {
            if (width < 1 || height < 1 || textSize <= 0) throw new IllegalArgumentException("layout");
            this.width = width; this.height = height; this.textSize = textSize; this.lineExtra = lineExtra; this.color = color;
            this.typeface = typeface == null ? Typeface.DEFAULT : typeface;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Spec)) return false; Spec value = (Spec) other;
            return width == value.width && height == value.height && textSize == value.textSize
                    && lineExtra == value.lineExtra && color == value.color && typeface.equals(value.typeface);
        }
        @Override public int hashCode() { return Objects.hash(width, height, textSize, lineExtra, color, typeface); }
    }
    public static final class Page {
        public final long start, end, block;
        public final int index, firstLine, lastLine, top, bottom;
        public final StaticLayout layout;
        public final Spec spec;
        Page(long block, int index, long blockStart, StaticLayout layout, Spec spec, int firstLine, int lastLine) {
            this.block = block; this.index = index; this.layout = layout; this.spec = spec;
            this.firstLine = firstLine; this.lastLine = lastLine;
            this.start = blockStart + layout.getLineStart(firstLine);
            this.end = blockStart + layout.getLineEnd(lastLine);
            this.top = layout.getLineTop(firstLine); this.bottom = layout.getLineBottom(lastLine);
        }
        public String text() {
            return layout.getText().subSequence(layout.getLineStart(firstLine), layout.getLineEnd(lastLine)).toString();
        }
    }
    private final Utf16File text;
    private Spec spec;
    private final LinkedHashMap<Long, List<Page>> cache = new LinkedHashMap<Long, List<Page>>(4, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, List<Page>> entry) { return size() > 3; }
    };
    public TxtPaginator(File normalized, long length) { text = new Utf16File(normalized, length); }
    public Page seek(long offset, Spec wanted) throws IOException {
        check();
        if (!wanted.equals(spec)) { spec = wanted; cache.clear(); }
        List<Page> pages = block(text.blockAt(offset));
        for (Page page : pages) if (offset < page.end || page.end == page.start) return page;
        return pages.get(pages.size() - 1);
    }
    public Page move(Page page, int direction) throws IOException {
        check();
        if (!page.spec.equals(spec)) return seek(page.start, page.spec);
        List<Page> pages = block(page.block);
        if (direction > 0) {
            if (page.index + 1 < pages.size()) return pages.get(page.index + 1);
            if (page.end >= text.length()) return page;
            return block(page.block + 1).get(0);
        }
        if (page.index > 0) return pages.get(page.index - 1);
        if (page.block == 0) return page;
        pages = block(page.block - 1); return pages.get(pages.size() - 1);
    }
    private List<Page> block(long number) throws IOException {
        List<Page> hit = cache.get(number); if (hit != null) return hit;
        check();
        long start = text.boundary(number), end = text.boundary(number + 1);
        String content = text.read(start, (int) (end - start));
        TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG | TextPaint.SUBPIXEL_TEXT_FLAG);
        paint.setTextSize(spec.textSize); paint.setColor(spec.color); paint.setTypeface(spec.typeface);
        StaticLayout layout = StaticLayout.Builder.obtain(content, 0, content.length(), paint, spec.width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setLineSpacing(spec.lineExtra, 1f).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build();
        check();
        List<Page> pages = new ArrayList<>();
        int first = 0, lineCount = layout.getLineCount();
        while (first < lineCount) {
            int last = first;
            while (last + 1 < lineCount && layout.getLineBottom(last + 1) - layout.getLineTop(first) <= spec.height) last++;
            // StaticLayout adds a zero-length visual line after a trailing LF. It belongs to the preceding range.
            if (layout.getLineStart(first) == content.length() && !pages.isEmpty()) break;
            pages.add(new Page(number, pages.size(), start, layout, spec, first, last)); first = last + 1;
        }
        if (pages.isEmpty()) pages.add(new Page(number, 0, start, layout, spec, 0, 0));
        cache.put(number, pages); return pages;
    }
    private static void check() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Layout cancelled");
    }
}

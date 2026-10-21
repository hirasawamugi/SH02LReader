package com.sh02l.reader.txt;

import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import com.sh02l.reader.core.text.Utf16File;

import java.io.*;
import java.util.*;

/** Single-worker continuous pagination. Read windows never become visible page boundaries. */
public final class TxtPaginator {
    public static final int MAX_LAYOUT_UNITS = Utf16File.BLOCK * 2;
    private static final int MAX_PAGE_RANGES = 1024;

    public static final class Spec {
        public final int width, height, color;
        public final float textSize, lineExtra;
        public final Typeface typeface;

        public Spec(int width, int height, float textSize, float lineExtra, int color) {
            this(width, height, textSize, lineExtra, color, Typeface.DEFAULT);
        }

        public Spec(
                int width,
                int height,
                float textSize,
                float lineExtra,
                int color,
                Typeface typeface) {
            if (width < 1 || height < 1 || textSize <= 0)
                throw new IllegalArgumentException("layout");
            this.width = width;
            this.height = height;
            this.textSize = textSize;
            this.lineExtra = lineExtra;
            this.color = color;
            this.typeface = typeface == null ? Typeface.DEFAULT : typeface;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Spec)) return false;
            Spec value = (Spec) other;
            return width == value.width
                    && height == value.height
                    && textSize == value.textSize
                    && lineExtra == value.lineExtra
                    && color == value.color
                    && typeface.equals(value.typeface);
        }

        @Override
        public int hashCode() {
            return Objects.hash(width, height, textSize, lineExtra, color, typeface);
        }
    }

    public static final class Page {
        public final long start, end, block;
        public final int index, firstLine, lastLine, top, bottom;
        public final StaticLayout layout;
        public final Spec spec;

        Page(
                long block,
                int index,
                long blockStart,
                StaticLayout layout,
                Spec spec,
                int firstLine,
                int lastLine) {
            this.block = block;
            this.index = index;
            this.layout = layout;
            this.spec = spec;
            this.firstLine = firstLine;
            this.lastLine = lastLine;
            this.start = blockStart + layout.getLineStart(firstLine);
            this.end = blockStart + layout.getLineEnd(lastLine);
            this.top = layout.getLineTop(firstLine);
            this.bottom = layout.getLineBottom(lastLine);
        }

        public String text() {
            return layout.getText()
                    .subSequence(layout.getLineStart(firstLine), layout.getLineEnd(lastLine))
                    .toString();
        }
    }

    private final Utf16File text;
    private Spec spec;
    private final LinkedHashMap<String, List<Page>> cache =
            new LinkedHashMap<String, List<Page>>(4, .75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Page>> entry) {
                    return size() > 3;
                }
            };
    // Offsets only: evicting a layout must not keep all earlier text/layouts alive.
    private final LinkedHashMap<Long, Long> ranges =
            new LinkedHashMap<Long, Long>(64, .75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Long> entry) {
                    return size() > MAX_PAGE_RANGES;
                }
            };

    public TxtPaginator(File normalized, long length) {
        text = new Utf16File(normalized, length);
    }

    public Page seek(long offset, Spec wanted) throws IOException {
        configure(wanted);
        long target = Math.max(0, Math.min(offset, Math.max(0, text.length() - 1)));
        Page hit = cached(target, false);
        if (hit != null) return hit;
        long start = text.boundary(Math.max(0, text.blockAt(target) - 1));
        Page page = startingAt(start);
        while (target >= page.end && page.end < text.length()) {
            check();
            page = startingAt(page.end);
        }
        return page;
    }

    public Page seekStart(long offset, Spec wanted) throws IOException {
        configure(wanted);
        return startingAt(Math.max(0, Math.min(offset, Math.max(0, text.length() - 1))));
    }

    private void configure(Spec wanted) throws InterruptedIOException {
        check();
        if (!wanted.equals(spec)) {
            spec = wanted;
            cache.clear();
            ranges.clear();
        }
    }

    public Page move(Page page, int direction) throws IOException {
        check();
        if (!page.spec.equals(spec)) return seek(page.start, page.spec);
        if (direction > 0) {
            if (page.end >= text.length()) return page;
            return startingAt(page.end);
        }
        if (page.start == 0 || direction == 0) return page;
        Long previous = null;
        for (Map.Entry<Long, Long> entry : ranges.entrySet())
            if (entry.getValue() == page.start && (previous == null || entry.getKey() > previous))
                previous = entry.getKey();
        if (previous != null) return exactRange(previous, page.start);
        long start = Math.max(0, page.start - Utf16File.BLOCK);
        // Start on a safe global boundary, while retaining a bounded amount of preceding text.
        start = text.boundary(Math.max(0, text.blockAt(start)));
        if (page.start - start > MAX_LAYOUT_UNITS) start = text.boundary(text.blockAt(start) + 1);
        String content = read(start, (int) (page.start - start));
        StaticLayout layout = layout(content);
        int last = lastLine(layout, content.length()), first = last;
        while (first > 0
                && layout.getLineBottom(last) - layout.getLineTop(first - 1) <= spec.height)
            first--;
        Page result = new Page(start, 0, start, layout, spec, first, last);
        remember(Collections.singletonList(result), start + ":" + page.start + ":back");
        return result;
    }

    private Page cached(long offset, boolean exact) {
        Page found = null;
        String key = null;
        for (Map.Entry<String, List<Page>> entry : cache.entrySet())
            for (Page page : entry.getValue())
                if ((exact
                                ? page.start == offset
                                : page.start <= offset
                                        && (offset < page.end || page.end == page.start))
                        && (found == null || page.start >= found.start)) {
                    found = page;
                    key = entry.getKey();
                }
        if (key != null) cache.get(key);
        return found;
    }

    private Page exactRange(long start, long end) throws IOException {
        Page hit = cached(start, true);
        if (hit != null && hit.end == end) return hit;
        String content = read(start, (int) (end - start));
        StaticLayout layout = layout(content);
        Page page = new Page(start, 0, start, layout, spec, 0, lastLine(layout, content.length()));
        remember(Collections.singletonList(page), start + ":" + end + ":range");
        return page;
    }

    private Page startingAt(long start) throws IOException {
        Page hit = cached(start, true);
        if (hit != null) return hit;
        Long knownEnd = ranges.get(start);
        if (knownEnd != null) return exactRange(start, knownEnd);
        int count = (int) Math.min(Utf16File.BLOCK, text.length() - start);
        for (; ; ) {
            String content = read(start, count);
            StaticLayout layout = layout(content);
            List<Page> pages = new ArrayList<>();
            int first = 0, lastContentLine = lastLine(layout, content.length());
            while (first <= lastContentLine) {
                int last = first;
                while (last < lastContentLine
                        && layout.getLineBottom(last + 1) - layout.getLineTop(first) <= spec.height)
                    last++;
                // The last line may be cut by this read window. Carry the whole unfinished page.
                if (last == lastContentLine && start + count < text.length()) break;
                pages.add(new Page(start, pages.size(), start, layout, spec, first, last));
                first = last + 1;
            }
            if (!pages.isEmpty()) {
                remember(pages, start + ":" + (start + count));
                return pages.get(0);
            }
            int larger = (int) Math.min(MAX_LAYOUT_UNITS, text.length() - start);
            if (count >= larger) throw new IOException("Reading area exceeds bounded text layout");
            count = larger;
        }
    }

    private void remember(List<Page> pages, String key) {
        cache.put(key, pages);
        for (Page page : pages) if (page.end > page.start) ranges.put(page.start, page.end);
    }

    private String read(long start, int count) throws IOException {
        if (count < 0 || count > MAX_LAYOUT_UNITS) throw new IOException("Unbounded text read");
        StringBuilder content = new StringBuilder(count);
        while (content.length() < count) {
            check();
            content.append(
                    text.read(
                            start + content.length(),
                            Math.min(Utf16File.BLOCK, count - content.length())));
        }
        return content.toString();
    }

    private static int lastLine(StaticLayout layout, int length) {
        int last = layout.getLineCount() - 1;
        while (last > 0 && layout.getLineStart(last) == length) last--;
        return last;
    }

    private StaticLayout layout(String content) throws IOException {
        check();
        TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG | TextPaint.SUBPIXEL_TEXT_FLAG);
        paint.setTextSize(spec.textSize);
        paint.setColor(spec.color);
        paint.setTypeface(spec.typeface);
        StaticLayout layout =
                StaticLayout.Builder.obtain(content, 0, content.length(), paint, spec.width)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setIncludePad(false)
                        .setLineSpacing(spec.lineExtra, 1f)
                        .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                        .build();
        check();
        return layout;
    }

    private static void check() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("Layout cancelled");
    }
}

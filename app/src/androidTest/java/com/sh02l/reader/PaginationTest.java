package com.sh02l.reader;

import android.test.InstrumentationTestCase;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;

import com.sh02l.reader.core.text.TextImporter;
import com.sh02l.reader.txt.TxtDocumentSession;
import com.sh02l.reader.txt.TxtPaginator;

import java.io.*;
import java.util.*;

@SuppressWarnings("deprecation")
public final class PaginationTest extends InstrumentationTestCase {
    public void testBlockBoundaryDoesNotCreateShortPage() throws Exception {
        StringBuilder source = new StringBuilder();
        while (source.length() < 26000) source.append("方源只能自己想办法。中文连续分页，English 😀e\u0301。\n");
        File directory =
                new File(getInstrumentation().getTargetContext().getCacheDir(), "m3-boundary");
        TextImporter.Result index =
                TextImporter.prepare(
                        new ByteArrayInputStream(source.toString().getBytes("UTF-8")),
                        directory,
                        null,
                        null);
        String expected = source.toString();
        TxtPaginator.Spec spec = new TxtPaginator.Spec(420, 610, 32, 6, 0xff000000);
        TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG | TextPaint.SUBPIXEL_TEXT_FLAG);
        paint.setTextSize(spec.textSize);
        paint.setTypeface(spec.typeface);
        StaticLayout reference =
                StaticLayout.Builder.obtain(expected, 0, expected.length(), paint, spec.width)
                        .setIncludePad(false)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(spec.lineExtra, 1f)
                        .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                        .build();
        TxtPaginator paginator = new TxtPaginator(new File(directory, "text.u16"), index.length);
        TxtPaginator.Page page = paginator.seek(0, spec);
        int first = 0;
        StringBuilder actual = new StringBuilder();
        while (first < reference.getLineCount()
                && reference.getLineStart(first) < expected.length()) {
            int last = first;
            while (last + 1 < reference.getLineCount()
                    && reference.getLineBottom(last + 1) - reference.getLineTop(first)
                            <= spec.height) last++;
            assertEquals(
                    "A read window must not create an extra line/page break",
                    reference.getLineEnd(last),
                    page.end);
            actual.append(page.text());
            assertTrue(page.layout.getText().length() <= 16384);
            if (page.end == index.length) break;
            TxtPaginator.Page next = paginator.move(page, 1);
            assertEquals(page.end, next.start);
            assertEquals(page.start, paginator.move(next, -1).start);
            page = next;
            first = last + 1;
        }
        assertEquals(expected, actual.toString());
    }

    public void testChapterSeekStartsAtHeadingAndTurnsRemainContinuous() throws Exception {
        StringBuilder source = new StringBuilder("第一章 开始\n");
        while (source.length() < 8400) source.append("连续正文没有人为分页。");
        source.append('\n');
        long heading = source.length();
        source.append("第二章 对齐标题\n");
        for (int i = 0; i < 500; i++) source.append("章节正文保持连续，不能重复也不能丢失。\n");
        File directory =
                new File(getInstrumentation().getTargetContext().getCacheDir(), "m3-heading");
        TextImporter.Result index =
                TextImporter.prepare(
                        new ByteArrayInputStream(source.toString().getBytes("UTF-8")),
                        directory,
                        null,
                        null);
        TxtDocumentSession document =
                new TxtDocumentSession(new File(directory, "text.u16"), index);
        TxtPaginator.Spec spec = new TxtPaginator.Spec(420, 610, 32, 6, 0xff000000);
        TxtPaginator.Page page = document.seek(heading, spec);
        assertEquals(heading, page.start);
        assertTrue(page.text().startsWith("第二章 对齐标题\n"));
        TxtPaginator.Page previous = document.move(page, -1);
        assertEquals(heading, previous.end);
        assertEquals(heading, document.move(previous, 1).start);
        TxtPaginator.Page next = document.move(page, 1);
        assertEquals(page.end, next.start);
        assertEquals(page.start, document.move(next, -1).start);
        TxtPaginator.Page reflow =
                document.seek(heading, new TxtPaginator.Spec(420, 610, 40, 6, 0xff000000));
        assertEquals(heading, reflow.start);
        assertTrue(reflow.text().startsWith("第二章 对齐标题\n"));
        TxtDocumentSession reopened =
                new TxtDocumentSession(new File(directory, "text.u16"), index);
        assertEquals(heading, reopened.seek(heading, spec).start);
    }

    public void testOneAndTenMiBGb18030FilesKeepExtendedChineseAndCanReachTheEnd()
            throws Exception {
        BookRepository repository = BookRepository.obtain(getInstrumentation().getTargetContext());
        for (int mib : new int[] {1, 10}) {
            File source =
                    new File(
                            getInstrumentation().getTargetContext().getCacheDir(),
                            mib + "MiB-GB18030.txt");
            byte[] block = new byte[1024];
            Arrays.fill(block, (byte) 'x');
            String marker = "中文𠀀GB18030，扩展汉字与标点。";
            byte[] prefix = marker.getBytes("GB18030");
            System.arraycopy(prefix, 0, block, 0, prefix.length);
            block[block.length - 1] = '\n';
            try (FileOutputStream output = new FileOutputStream(source)) {
                for (int i = 0; i < mib * 1024; i++) output.write(block);
            }
            BookRepository.Book book =
                    repository.importBook(new FileInputStream(source), source.getName(), null);
            assertEquals("GB18030", book.charset);
            assertEquals(mib * 1024L * 1024L, book.sourceBytes);
            TxtPaginator paginator =
                    new TxtPaginator(
                            new File(repository.directory(book.id), "text.u16"), book.length);
            TxtPaginator.Spec spec = new TxtPaginator.Spec(500, 760, 30, 6, 0xff000000);
            assertTrue(paginator.seek(0, spec).text().startsWith(marker));
            assertEquals(book.length, paginator.seek(book.length - 1, spec).end);
            android.os.Debug.MemoryInfo memory = new android.os.Debug.MemoryInfo();
            android.os.Debug.getMemoryInfo(memory);
            Log.i(
                    "SH02LTests",
                    mib + "MiB GB18030 chars=" + book.length + " pssKiB=" + memory.getTotalPss());
            assertTrue(source.delete());
        }
    }

    public void testEveryLogicalCharacterAppearsOnceAndBackwardPagesMatch() throws Exception {
        StringBuilder source = new StringBuilder("第一章 分页验证\r\n");
        while (source.length() < 26000)
            source.append("中文：标点，English words 😀e\u0301 𠀀。\n\n下一行\r\n");
        String expected = source.toString().replace("\r\n", "\n");
        File directory =
                new File(
                        getInstrumentation().getTargetContext().getCacheDir(),
                        "pagination-" + System.nanoTime());
        TextImporter.Result index =
                TextImporter.prepare(
                        new ByteArrayInputStream(source.toString().getBytes("UTF-8")),
                        directory,
                        null,
                        null);
        for (int font : new int[] {20, 32, 48}) {
            TxtPaginator paginator =
                    new TxtPaginator(new File(directory, "text.u16"), index.length);
            TxtPaginator.Spec spec = new TxtPaginator.Spec(420, 610, font, 6, 0xff000000);
            TxtPaginator.Page page = paginator.seek(0, spec);
            long offset = 0;
            StringBuilder actual = new StringBuilder();
            int pages = 0;
            while (true) {
                assertEquals(offset, page.start);
                assertTrue(page.end > page.start);
                actual.append(page.text());
                offset = page.end;
                pages++;
                TxtPaginator.Page next = paginator.move(page, 1);
                if (next.start == page.start) break;
                TxtPaginator.Page back = paginator.move(next, -1);
                assertEquals(page.start, back.start);
                assertEquals(page.text(), back.text());
                page = next;
                assertTrue("Unbounded page loop", pages < 2000);
            }
            assertEquals(index.length, offset);
            assertEquals(expected, actual.toString());
            Log.i("SH02LTests", "coverage font=" + font + " pages=" + pages + " chars=" + offset);
        }
        for (File file : Objects.requireNonNull(directory.listFiles())) assertTrue(file.delete());
        assertTrue(directory.delete());
    }

    public void testFiftyMiBWithoutNewlinesUsesBoundedLayoutMemoryAndKeepsOriginal()
            throws Exception {
        File source =
                new File(getInstrumentation().getTargetContext().getCacheDir(), "50MiB-无换行.txt");
        byte[] block = new byte[16384];
        Arrays.fill(block, (byte) 'a');
        byte[] chinese = "中文😀𠀀，长行与跨块字符。".getBytes("UTF-8");
        System.arraycopy(chinese, 0, block, 0, chinese.length);
        long started = android.os.SystemClock.elapsedRealtime();
        try (FileOutputStream output = new FileOutputStream(source)) {
            for (int i = 0; i < 3200; i++) output.write(block);
        }
        BookRepository repository = BookRepository.obtain(getInstrumentation().getTargetContext());
        BookRepository.Book book =
                repository.importBook(new FileInputStream(source), source.getName(), null);
        assertEquals(50L * 1024 * 1024, book.sourceBytes);
        File directory = repository.directory(book.id);
        TextImporter.Result index = TextImporter.readIndex(directory);
        assertEquals(source.length(), new File(directory, "original.bin").length());
        assertEquals(1, index.chapters.size());
        TxtPaginator paginator = new TxtPaginator(new File(directory, "text.u16"), index.length);
        TxtPaginator.Spec spec = new TxtPaginator.Spec(500, 760, 30, 6, 0xff000000);
        for (long offset : new long[] {0, index.length / 2, index.length - 100}) {
            TxtPaginator.Page page = paginator.seek(offset, spec);
            assertTrue(page.start <= offset && page.end > offset);
            assertTrue(page.layout.getText().length() <= 8704);
        }
        android.os.Debug.MemoryInfo memory = new android.os.Debug.MemoryInfo();
        android.os.Debug.getMemoryInfo(memory);
        Log.i(
                "SH02LTests",
                "50MiB source="
                        + book.sourceBytes
                        + " chars="
                        + index.length
                        + " pssKiB="
                        + memory.getTotalPss()
                        + " elapsedMs="
                        + (android.os.SystemClock.elapsedRealtime() - started));
        assertTrue(source.delete());
    }
}

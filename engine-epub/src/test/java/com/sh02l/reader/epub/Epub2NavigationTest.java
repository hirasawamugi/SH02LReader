package com.sh02l.reader.epub;

import static org.junit.Assert.*;

import com.sh02l.reader.core.SearchBatch;

import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

public class Epub2NavigationTest {
    @Test
    public void ncxCoverAndSearchUseTheOriginalResources() throws Exception {
        File file = File.createTempFile("epub2-navigation-", ".epub");
        file.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            entry(
                    zip,
                    "META-INF/container.xml",
                    "<container><rootfiles><rootfile"
                        + " full-path='OPS/book.opf'/></rootfiles></container>");
            entry(
                    zip,
                    "OPS/book.opf",
                    "<package version='2.0'><metadata"
                        + " xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:title>EPUB2"
                        + " 验证</dc:title><meta name='cover'"
                        + " content='art'/></metadata><manifest><item id='chapter'"
                        + " href='chapter.xhtml' media-type='application/xhtml+xml'/><item id='toc'"
                        + " href='toc.ncx' media-type='application/x-dtbncx+xml'/><item id='art'"
                        + " href='art.png' media-type='image/png'/></manifest><spine"
                        + " toc='toc'><itemref idref='chapter'/></spine></package>");
            entry(
                    zip,
                    "OPS/toc.ncx",
                    "<ncx><navMap><navPoint><navLabel><text>旧式目录</text></navLabel><content"
                        + " src='chapter.xhtml#target'/></navPoint></navMap></ncx>");
            entry(
                    zip,
                    "OPS/chapter.xhtml",
                    "<!DOCTYPE html SYSTEM 'http://example.invalid/never-fetch.dtd'><html><body><p"
                        + " id='target'>实体隔离后的正文搜索。正文搜索。</p></body></html>");
        }
        try (EpubArchive archive = new EpubArchive(file)) {
            assertEquals("OPS/art.png", archive.cover);
            assertEquals("旧式目录", archive.navigation.get(0).title);
            assertEquals("target", archive.navigation.get(0).fragment);
            assertTrue(archive.text(archive.spine.get(0)).contains("正文搜索"));
        }
        EpubDocumentSession document = new EpubDocumentSession(file);
        try {
            SearchBatch first = document.search("正文搜索", null, 1, () -> false);
            assertEquals(1, first.matches.size());
            assertTrue(first.hasMore);
            SearchBatch second = document.search("正文搜索", first.next, 1, () -> false);
            assertEquals(1, second.matches.size());
            assertFalse(second.hasMore);
            assertNotEquals(first.matches.get(0).location, second.matches.get(0).location);
        } finally {
            document.close();
        }
    }

    private void entry(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

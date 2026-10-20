package com.sh02l.reader.epub;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

public class EpubArchiveTest {
    private File book(boolean hostile) throws Exception {
        return book(hostile, false);
    }

    private File book(boolean hostile, boolean entity) throws Exception {
        File file = File.createTempFile("epub-archive-", ".epub");
        file.deleteOnExit();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            add(zip, "mimetype", "application/epub+zip");
            add(
                    zip,
                    "META-INF/container.xml",
                    (entity
                                    ? "<!DOCTYPE container [<!ENTITY stolen SYSTEM"
                                          + " 'file:///private/secret'>]>"
                                    : "")
                            + "<container"
                            + " xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile"
                            + " full-path='OPS/book.opf'/></rootfiles></container>");
            add(
                    zip,
                    "OPS/book.opf",
                    "<package xmlns='http://www.idpf.org/2007/opf' version='3.0'><metadata"
                        + " xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:title>验证读本</dc:title></metadata><manifest><item"
                        + " id='one' href='text/one.xhtml'"
                        + " media-type='application/xhtml+xml'/><item id='two' href='two.xhtml'"
                        + " media-type='application/xhtml+xml'/></manifest><spine><itemref"
                        + " idref='one'/><itemref idref='two'/></spine></package>");
            add(
                    zip,
                    "OPS/text/one.xhtml",
                    "<html xmlns='http://www.w3.org/1999/xhtml'><body><p id='a'>中文 Alpha</p><a"
                        + " href='../two.xhtml#b'>第二章</a></body></html>");
            add(
                    zip,
                    "OPS/two.xhtml",
                    "<html xmlns='http://www.w3.org/1999/xhtml'><body><p"
                        + " id='b'>Beta</p></body></html>");
            if (hostile) add(zip, "../escape.xhtml", "outside");
        }
        return file;
    }

    private void add(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    @Test
    public void spineAndRelativeLinksRemainInsidePublication() throws Exception {
        try (EpubArchive archive = new EpubArchive(book(false))) {
            assertEquals("验证读本", archive.title);
            assertEquals("OPS/text/one.xhtml", archive.spine.get(0));
            assertEquals(
                    "OPS/two.xhtml", archive.resolve("OPS/text/one.xhtml", "../two.xhtml#b").path);
            assertEquals("b", archive.resolve("OPS/text/one.xhtml", "../two.xhtml#b").fragment);
            assertTrue(archive.text(archive.spine.get(0)).contains("中文 Alpha"));
            try {
                archive.resolve(archive.spine.get(0), "../../../outside");
                fail("Escaped publication root");
            } catch (IOException expected) {
            }
            try {
                archive.resolve(archive.spine.get(0), "https://example.com/book");
                fail("Accepted remote resource");
            } catch (IOException expected) {
            }
        }
    }

    @Test
    public void hostileZipNamesAreRejectedBeforeReading() throws Exception {
        try (EpubArchive archive = new EpubArchive(book(true))) {
            fail("Accepted traversal ZIP");
        } catch (IOException expected) {
        }
    }

    @Test
    public void publicationEntitiesAreRejectedWithoutResolvingFiles() throws Exception {
        try (EpubArchive archive = new EpubArchive(book(false, true))) {
            fail("Accepted entity declaration");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("entity"));
        }
    }
}

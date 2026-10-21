package com.sh02l.reader.epub;

import static org.junit.Assert.*;

import org.junit.Test;
import org.xml.sax.InputSource;

import java.io.*;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

public class EpubHtmlTest {
    @Test
    public void readerStyleIsInTheDocumentBeforeTheFirstBodyFrame() throws Exception {
        String html =
                "<html><head><style>body{color:red}</style></head><body>image page</body></html>";
        String css = "body{color:#123456!important;font-size:18px!important}";
        String display =
                new String(
                        EpubHtml.forDisplay(html.getBytes(StandardCharsets.UTF_8), css),
                        StandardCharsets.UTF_8);
        assertTrue(display.indexOf("__reader_initial") > display.indexOf("body{color:red}"));
        assertTrue(display.indexOf(css) < display.indexOf("<body>"));
        assertEquals(1, display.split("name=\"viewport\"", -1).length - 1);
    }

    @Test
    public void namedReferencesAndUtf16KeepTheirExactTextOffsets() throws Exception {
        String original =
                "<?xml version='1.0' encoding='UTF-16'?><html><head><meta name='viewport'"
                    + " content='width=980'/></head><body>before&nbsp;middle&mdash;&NotEqualTilde;needle</body></html>";
        byte[] bytes = original.getBytes(StandardCharsets.UTF_16);
        String normalized = EpubHtml.forXml(bytes);
        String text =
                DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(new InputSource(new StringReader(normalized)))
                        .getDocumentElement()
                        .getTextContent();
        assertEquals("before\u00a0middle\u2014\u2242\u0338needle", text);
        assertEquals(16, text.indexOf("needle"));
        String display = new String(EpubHtml.forDisplay(bytes), StandardCharsets.UTF_8);
        assertTrue(display.contains("initial-scale=1"));
        assertFalse(display.contains("width=980"));
        assertEquals(1, display.split("name=\"viewport\"", -1).length - 1);
    }
}

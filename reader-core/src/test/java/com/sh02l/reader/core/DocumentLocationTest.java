package com.sh02l.reader.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class DocumentLocationTest {
    @Test
    public void textAndEpubLocationsKeepTheirOwnMeaning() {
        assertEquals(42L, DocumentLocation.txt(42).textOffset());
        DocumentLocation epub = new DocumentLocation("epub", 1, "chapter.xhtml\u001f42\u001f");
        assertEquals(epub, new DocumentLocation("epub", 1, epub.payload));
        try {
            epub.textOffset();
            fail("EPUB interpreted as a TXT offset");
        } catch (IllegalStateException expected) {
        }
        try {
            new DocumentLocation("txt", 1, "-1");
            fail("Negative text position accepted");
        } catch (IllegalArgumentException expected) {
        }
    }
}

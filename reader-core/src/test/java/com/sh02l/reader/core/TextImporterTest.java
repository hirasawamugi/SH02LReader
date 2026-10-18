package com.sh02l.reader.core;

import com.sh02l.reader.core.text.TextImporter;
import com.sh02l.reader.core.text.Utf16File;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Arrays;
import static org.junit.Assert.*;

public class TextImporterTest {
    @Test public void everyOutputStageConsultsTheSpaceGuardBeforeWriting()throws Exception {
        for(String stopped:new String[]{"copy","decode","commit"}){
            File directory=temporary.newFolder();final String[] phase={"copy"};
            TextImporter.Observer observer=new TextImporter.Observer(){public boolean cancelled(){return false;}public void progress(String value,long bytes){phase[0]=value;}public void beforeWrite(long bytes)throws IOException{if(stopped.equals(phase[0]))throw new IOException("storage exhausted at "+stopped);}};
            try{TextImporter.prepare(new ByteArrayInputStream("plain text\n".getBytes("UTF-8")),directory,null,observer);fail("Write ignored space guard at "+stopped);}catch(IOException expected){}
            try{TextImporter.readIndex(directory);fail("Failed import published a readable index");}catch(IOException expected){}
        }
    }
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private File importBytes(byte[] bytes) throws Exception {
        File directory = temporary.newFolder();
        TextImporter.prepare(new ByteArrayInputStream(bytes), directory, null, null); return directory;
    }
    @Test public void allRequiredEncodingsPreserveTextAndOriginalBytes() throws Exception {
        String text = "第一章 你好\r\n中文😀，GB18030扩展𠀀。\r空行\n\n终";
        for (String charset : new String[]{"UTF-8", "UTF-16LE", "UTF-16BE", "GB18030"}) {
            byte[] body = text.getBytes(Charset.forName(charset));
            byte[] bytes;
            if (charset.startsWith("UTF-16")) {
                bytes = new byte[body.length + 2]; bytes[0] = (byte) (charset.endsWith("LE") ? 0xff : 0xfe);
                bytes[1] = (byte) (charset.endsWith("LE") ? 0xfe : 0xff); System.arraycopy(body, 0, bytes, 2, body.length);
            } else bytes = body;
            File directory = importBytes(bytes); TextImporter.Result index = TextImporter.readIndex(directory);
            assertEquals(charset, index.charset);
            String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
            assertEquals(normalized, new Utf16File(new File(directory, "text.u16"), index.length).read(0, 8192));
            assertArrayEquals(bytes, java.nio.file.Files.readAllBytes(new File(directory, "original.bin").toPath()));
            assertEquals("第一章 你好", index.chapters.get(0).title);
        }
    }
    @Test public void bomIsRemovedAndCrLfAcrossReadsIsNormalized() throws Exception {
        char[] prefix = new char[8190]; Arrays.fill(prefix, '文');
        String original = "\ufeff" + new String(prefix) + "\r\n第十二章 结束\r\n终";
        File directory = importBytes(original.getBytes("UTF-8")); TextImporter.Result index = TextImporter.readIndex(directory);
        Utf16File file = new Utf16File(new File(directory, "text.u16"), index.length);
        assertEquals(new String(prefix) + "\n第十二章 结束\n终", file.read(0, 8400));
        assertEquals(8191, index.chapters.get(1).offset);
    }
    @Test public void invalidUtf8LateInFileUsesGbkForTheWholeFile() throws Exception {
        byte[] bytes = new byte[40001]; Arrays.fill(bytes, (byte) 'a'); bytes[39999] = (byte) 0xd6; bytes[40000] = (byte) 0xd0;
        TextImporter.Result index = TextImporter.readIndex(importBytes(bytes)); assertEquals("GB18030", index.charset);
    }
    @Test public void emptyTextHasAUsableBodyIndex() throws Exception {
        TextImporter.Result index = TextImporter.readIndex(importBytes(new byte[0]));
        assertEquals(0, index.length); assertEquals(1, index.chapters.size()); assertEquals(0, index.chapters.get(0).offset);
    }
    @Test public void binaryOrDamagedBomTextFailsWithoutAValidIndex() throws Exception {
        for (byte[] bytes : new byte[][]{{0, 0, 0}, {(byte) 0xff, (byte) 0xfe, 1}}) {
            File directory = temporary.newFolder();
            try { TextImporter.prepare(new ByteArrayInputStream(bytes), directory, null, null); fail("expected rejection"); }
            catch (IOException expected) { assertFalse(new File(directory, "index.bin").exists()); }
        }
    }
    @Test public void cancellationDoesNotPublishAnIndex() throws Exception {
        File directory = temporary.newFolder();
        try {
            TextImporter.prepare(new ByteArrayInputStream(new byte[40000]), directory, null, new TextImporter.Observer() {
                public void progress(String phase, long bytes) { } public boolean cancelled() { return true; }
            }); fail("expected cancellation");
        } catch (InterruptedIOException expected) { assertFalse(new File(directory, "index.bin").exists()); }
    }
    @Test public void failedStagingCreationClosesTheSource() throws Exception {
        final boolean[] closed = {false};
        InputStream source = new ByteArrayInputStream(new byte[10]) {
            @Override public void close() { closed[0] = true; }
        };
        try { TextImporter.prepare(source, temporary.newFile(), null, null); fail("expected storage failure"); }
        catch (IOException expected) { assertTrue(closed[0]); }
    }
    @Test public void surrogateAndCombiningBoundariesAreStableForBackwardReading() throws Exception {
        char[] prefix = new char[8191]; Arrays.fill(prefix, 'x');
        String text = new String(prefix) + "😀e\u0301" + new String(prefix) + "结束";
        File directory = importBytes(text.getBytes("UTF-8")); TextImporter.Result index = TextImporter.readIndex(directory);
        Utf16File file = new Utf16File(new File(directory, "text.u16"), index.length);
        long next = file.boundary(1); assertEquals(8191, next); assertEquals(1, file.blockAt(next));
        StringBuilder rebuilt = new StringBuilder();
        for (long block = 0; file.boundary(block) < index.length; block++) {
            long start = file.boundary(block), end = file.boundary(block + 1);
            rebuilt.append(file.read(start, (int) (end - start)));
        }
        assertEquals(text, rebuilt.toString());
    }
    @Test public void hugeLineDoesNotBecomeAHugeChapterTitle() throws Exception {
        char[] text = new char[3 * 1024 * 1024]; Arrays.fill(text, '文');
        File directory = importBytes(new String(text).getBytes("UTF-8")); TextImporter.Result index = TextImporter.readIndex(directory);
        assertEquals(text.length, index.length); assertEquals(1, index.chapters.size());
        assertEquals(8192, new Utf16File(new File(directory, "text.u16"), index.length).read(0, 8192).length());
    }
}

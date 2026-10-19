package com.sh02l.reader.core;

import com.sh02l.reader.core.text.TextImporter;
import com.sh02l.reader.core.text.TextSearcher.Batch;
import com.sh02l.reader.core.text.TextSearcher.Match;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;
import static com.sh02l.reader.core.text.TextSearcher.find;

public class TextSearcherTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void findsAcrossReadWindowsAndNormalizesSnippetWhitespace() throws Exception {
        String text = repeat('x', 8190) + "needle\n\t  next needle tail";
        Batch batch = find(normalized(text), text.length(), "needle", 0, 100, null);
        List<Match> matches = batch.matches;
        assertEquals(2, matches.size());
        assertEquals(8190, matches.get(0).offset);
        assertEquals(8205, matches.get(1).offset);
        String snippet = matches.get(0).snippet;
        assertTrue(snippet.contains("needle next needle tail"));
        assertTrue(snippet.length() <= 60);
        assertFalse(snippet.contains("\n"));
        assertFalse(snippet.contains("\t"));
        assertFalse(batch.hasMore);
    }

    @Test public void continuationDoesNotDropOrRepeatNonoverlappingMatches() throws Exception {
        String text = "aaaaa aaa AAA aaa";
        File file = normalized(text);
        List<Long> offsets = new ArrayList<>();
        long cursor = 0;
        for (int i = 0; i < 4; i++) {
            Batch batch = find(file, text.length(), "aa", cursor, 1, null);
            assertEquals(1, batch.matches.size());
            offsets.add(batch.matches.get(0).offset);
            long next = batch.nextOffset;
            assertTrue(next > cursor);
            assertEquals(i < 3, batch.hasMore);
            cursor = next;
        }
        assertEquals(Arrays.asList(0L, 2L, 6L, 14L), offsets);
        assertEquals(text.length(), cursor);

        Batch capped = find(normalized(repeat('x', 250)), 250, "x", 0, 100, null);
        assertEquals(100, capped.matches.size());
        assertEquals(100, capped.nextOffset);
        assertTrue(capped.hasMore);
    }

    @Test public void cancellationStopsAnInProgressScanAndEvenAnEmptyScan() throws Exception {
        File file = normalized(repeat('x', 40000));
        final boolean[] cancel = {false};
        TextImporter.Observer observer = new TextImporter.Observer() {
            public void progress(String phase, long bytes) { if (bytes >= 16384) cancel[0] = true; }
            public boolean cancelled() { return cancel[0]; }
        };
        assertThrows(InterruptedIOException.class, () -> find(file, 40000, "absent", 0, 100, observer));
        assertTrue(cancel[0]);
        assertThrows(InterruptedIOException.class, () -> find(normalized(""), 0, "x", 0, 1, observer));
    }

    @Test public void handlesEmptySmallLiteralAndUtf16Queries() throws Exception {
        Batch empty = find(normalized(""), 0, "x", 0, 100, null);
        assertTrue(empty.matches.isEmpty());
        assertEquals(0, empty.nextOffset);
        assertFalse(empty.hasMore);

        String text = "Needle needle n.edle 😀";
        File file = normalized(text);
        assertEquals(7, find(file, text.length(), "needle", 0, 100, null).matches.get(0).offset);
        assertEquals(15, find(file, text.length(), ".", 0, 100, null).matches.get(0).offset);
        assertEquals(21, find(file, text.length(), "😀", 0, 100, null).matches.get(0).offset);
        assertTrue(find(file, text.length(), "longer than this entire book", 0, 100, null).matches.isEmpty());
        assertTrue(find(file, text.length(), "needle", text.length(), 100, null).matches.isEmpty());
    }

    @Test public void rejectsInvalidQueriesRangesAndDamagedCacheLengths() throws Exception {
        File file = normalized("text");
        for (String query : new String[]{null, "", repeat('x', 257), "\ud83d"})
            assertThrows(IllegalArgumentException.class, () -> find(file, 4, query, 0, 1, null));
        assertThrows(IllegalArgumentException.class, () -> find(file, 4, "x", -1, 1, null));
        assertThrows(IllegalArgumentException.class, () -> find(file, 4, "x", 5, 1, null));
        assertThrows(IllegalArgumentException.class, () -> find(file, -1, "x", 0, 1, null));
        assertThrows(IllegalArgumentException.class, () -> find(file, Long.MAX_VALUE, "x", 0, 1, null));
        assertThrows(IllegalArgumentException.class, () -> find(file, 4, "x", 0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> find(file, 4, "x", 0, 101, null));
        assertThrows(IOException.class, () -> find(file, 3, "x", 0, 1, null));
        File odd = temporary.newFile();
        Files.write(odd.toPath(), new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> find(odd, 1, "x", 0, 1, null));
    }

    private File normalized(String text) throws IOException {
        File file = temporary.newFile();
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_16LE));
        return file;
    }
    private static String repeat(char value, int count) {
        char[] chars = new char[count]; Arrays.fill(chars, value); return new String(chars);
    }
}

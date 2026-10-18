package com.sh02l.reader.core.text;

import java.io.*;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/** Streaming import. Offsets always count UTF-16 units in normalized (LF) text, version 1. */
public final class TextImporter {
    public interface Observer { void progress(String phase, long bytes); boolean cancelled(); }
    public static final class Chapter {
        public final String title;
        public final long offset;
        public Chapter(String title, long offset) { this.title = title; this.offset = offset; }
    }
    public static final class Result {
        public final String id, charset;
        public final long sourceBytes, length;
        public final List<Chapter> chapters;
        Result(String id, String charset, long sourceBytes, long length, List<Chapter> chapters) {
            this.id = id; this.charset = charset; this.sourceBytes = sourceBytes; this.length = length;
            this.chapters = Collections.unmodifiableList(chapters);
        }
    }
    private static final int MAGIC = 0x53483254, VERSION = 1;
    private static final int MAX_CHAPTERS = 10000;
    private static final Pattern HEADING = Pattern.compile(
            "^(?:第[零〇一二三四五六七八九十百千万两0-9０-９]{1,16}[章节卷回部篇集].{0,100}|(?:chapter|卷)\\s*[0-9一二三四五六七八九十]+.{0,100}|序章|序言|楔子|尾声|后记)$",
            Pattern.CASE_INSENSITIVE);
    private TextImporter() { }
    public static void check(Observer observer) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || (observer != null && observer.cancelled()))
            throw new InterruptedIOException("Import cancelled");
    }
    private static void progress(Observer observer, String phase, long count) throws IOException {
        check(observer); if (observer != null) observer.progress(phase, count);
    }
    /** Caller owns a fresh staging directory and publishes it only after this returns. */
    public static Result prepare(InputStream source, File staging, String override, Observer observer) throws IOException {
        if (!staging.isDirectory() && !staging.mkdirs()) {
            source.close(); throw new IOException("Cannot create staging directory");
        }
        File original = new File(staging, "original.bin");
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        long bytes = 0;
        try (InputStream input = source; FileOutputStream output = new FileOutputStream(original)) {
            byte[] buffer = new byte[16384]; int count;
            while ((count = input.read(buffer)) != -1) {
                progress(observer, "copy", bytes);
                output.write(buffer, 0, count); digest.update(buffer, 0, count); bytes += count;
            }
            output.getFD().sync();
        }
        StringBuilder hash = new StringBuilder(64);
        for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 255));
        String charset = override == null ? detect(original, observer) : Charset.forName(override).name();
        CharsetDecoder decoder = Charset.forName(charset).newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        List<Chapter> chapters = new ArrayList<>();
        long length = 0, lineStart = 0;
        StringBuilder line = new StringBuilder(128);
        boolean longLine = false, lastCr = false, first = true;
        try (Reader input = new InputStreamReader(new FileInputStream(original), decoder);
             FileOutputStream file = new FileOutputStream(new File(staging, "text.u16"))) {
            BufferedOutputStream output = new BufferedOutputStream(file, 16384);
            char[] buffer = new char[8192]; byte[] encoded = new byte[16384]; int count;
            while ((count = input.read(buffer)) != -1) {
                progress(observer, "decode", length * 2);
                int encodedCount = 0;
                for (int i = 0; i < count; i++) {
                    char value = buffer[i];
                    if (first) { first = false; if (value == '\ufeff') continue; }
                    if (value == '\n' && lastCr) { lastCr = false; continue; }
                    lastCr = value == '\r';
                    if (lastCr) value = '\n';
                    if (value == 0) throw new IOException("Binary NUL in text");
                    encoded[encodedCount++] = (byte) value;
                    encoded[encodedCount++] = (byte) (value >>> 8); length++;
                    if (value == '\n') {
                        if (!longLine) chapter(chapters, line.toString(), lineStart);
                        line.setLength(0); longLine = false; lineStart = length;
                    } else if (!longLine) {
                        if (line.length() < 512) line.append(value);
                        else { line.setLength(0); longLine = true; }
                    }
                }
                output.write(encoded, 0, encodedCount);
            }
            if (!longLine && line.length() > 0) chapter(chapters, line.toString(), lineStart);
            output.flush(); file.getFD().sync();
        }
        if (chapters.isEmpty() || chapters.get(0).offset > 0) chapters.add(0, new Chapter("正文", 0));
        Result result = new Result(hash.toString(), charset, bytes, length, chapters);
        progress(observer, "commit", bytes);
        try (FileOutputStream file = new FileOutputStream(new File(staging, "index.bin"))) {
            DataOutputStream output = new DataOutputStream(new BufferedOutputStream(file));
            output.writeInt(MAGIC); output.writeInt(VERSION); output.writeUTF(result.id); output.writeUTF(charset);
            output.writeLong(bytes); output.writeLong(length); output.writeInt(chapters.size());
            for (Chapter chapter : chapters) { output.writeUTF(chapter.title); output.writeLong(chapter.offset); }
            output.flush(); file.getFD().sync();
        }
        check(observer);
        return result;
    }
    private static void chapter(List<Chapter> chapters, String value, long offset) {
        String title = value.trim();
        if (title.length() <= 120 && chapters.size() < MAX_CHAPTERS && HEADING.matcher(title).matches())
            chapters.add(new Chapter(title, offset));
    }
    public static Result readIndex(File directory) throws IOException {
        File index = new File(directory, "index.bin");
        if (index.length() > 5 * 1024 * 1024) throw new IOException("Invalid index size");
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(index)))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) throw new IOException("Unsupported index");
            String id = input.readUTF(), charset = input.readUTF();
            long bytes = input.readLong(), length = input.readLong(); int count = input.readInt();
            if (!id.matches("[0-9a-f]{64}") || !Charset.isSupported(charset) || bytes < 0 || length < 0
                    || length > Long.MAX_VALUE / 2 || count < 1 || count > MAX_CHAPTERS + 1
                    || new File(directory, "text.u16").length() != length * 2) throw new IOException("Invalid index");
            List<Chapter> chapters = new ArrayList<>(); long previous = -1;
            for (int i = 0; i < count; i++) {
                String title = input.readUTF(); long offset = input.readLong();
                if (title.length() > 120 || offset < 0 || offset > length || offset <= previous) throw new IOException("Invalid chapter");
                chapters.add(new Chapter(title, offset)); previous = offset;
            }
            if (chapters.get(0).offset != 0 || input.read() != -1) throw new IOException("Invalid index end");
            return new Result(id, charset, bytes, length, chapters);
        } catch (IllegalArgumentException error) { throw new IOException("Invalid index charset", error); }
    }
    private static String detect(File file, Observer observer) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            int first = input.read(), second = input.read(), third = input.read();
            if (first == 0xff && second == 0xfe) return "UTF-16LE";
            if (first == 0xfe && second == 0xff) return "UTF-16BE";
            if (first == 0xef && second == 0xbb && third == 0xbf) return "UTF-8";
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (Reader input = new InputStreamReader(new FileInputStream(file), decoder)) {
            char[] buffer = new char[8192]; int count; long total = 0;
            while ((count = input.read(buffer)) != -1) { total += count; progress(observer, "encoding", total); }
            return "UTF-8";
        } catch (CharacterCodingException notUtf8) { return "GB18030"; }
    }
}

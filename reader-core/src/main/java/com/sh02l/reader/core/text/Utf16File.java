package com.sh02l.reader.core.text;

import java.io.*;

/** Bounded random reads in the normalized UTF-16LE cache; never reads an entire book. */
public final class Utf16File {
    public static final int BLOCK = 8192;
    private final File file;
    private final long length;
    public Utf16File(File file, long length) { this.file = file; this.length = length; }
    public long length() { return length; }
    public String read(long start, int maximum) throws IOException {
        if (start < 0 || start > length || maximum < 0 || maximum > BLOCK + 512) throw new IllegalArgumentException("range");
        int count = (int) Math.min(maximum, length - start);
        byte[] bytes = new byte[count * 2]; char[] chars = new char[count];
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) { input.seek(start * 2); input.readFully(bytes); }
        for (int i = 0; i < count; i++) chars[i] = (char) ((bytes[i * 2] & 255) | ((bytes[i * 2 + 1] & 255) << 8));
        return new String(chars);
    }
    /** A deterministic boundary near an 8192-unit grid; keep common combining/emoji clusters intact. */
    public long boundary(long block) throws IOException {
        if (block < 0 || block > Long.MAX_VALUE / BLOCK) throw new IllegalArgumentException("block");
        long nominal = Math.min(length, block * BLOCK);
        if (nominal == 0 || nominal == length) return nominal;
        long start = Math.max(0, nominal - 256);
        String sample = read(start, (int) Math.min(512, length - start));
        int position = (int) (nominal - start);
        for (int steps = 0; position > 1 && steps < 120; steps++) {
            if (position < sample.length() && Character.isLowSurrogate(sample.charAt(position))
                    && Character.isHighSurrogate(sample.charAt(position - 1))) { position--; continue; }
            int next = sample.codePointAt(position), prior = sample.codePointBefore(position);
            int type = Character.getType(next);
            if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK || next == 0x200d || prior == 0x200d
                    || (next >= 0x1f3fb && next <= 0x1f3ff)) position -= Character.charCount(prior);
            else break;
        }
        return start + position;
    }
    public long blockAt(long offset) throws IOException {
        long bounded = Math.max(0, Math.min(offset, Math.max(0, length - 1)));
        long block = bounded / BLOCK;
        if (block + 1 <= length / BLOCK && bounded >= boundary(block + 1)) block++;
        return block;
    }
}

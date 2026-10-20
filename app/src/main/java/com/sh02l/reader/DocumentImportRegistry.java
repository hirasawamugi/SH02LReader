package com.sh02l.reader;

import com.sh02l.reader.core.*;
import com.sh02l.reader.core.text.TextImporter;
import com.sh02l.reader.epub.*;

import java.io.*;
import java.util.Locale;

/** Format preparation produces an immutable private original and format-owned metadata. */
final class DocumentImportRegistry {
    static final class Prepared {
        final String id, title, format, charset;
        final long length, sourceBytes;
        final DocumentLocation location;
        final byte[] cover;

        Prepared(
                String id,
                String title,
                String format,
                String charset,
                long length,
                long sourceBytes,
                DocumentLocation location,
                byte[] cover) {
            this.id = id;
            this.title = title;
            this.format = format;
            this.charset = charset;
            this.length = length;
            this.sourceBytes = sourceBytes;
            this.location = location;
            this.cover = cover;
        }
    }

    static boolean bookName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".txt") || n.endsWith(".epub");
    }

    static Prepared prepare(
            InputStream source, String name, File staging, TextImporter.Observer observer)
            throws IOException {
        BufferedInputStream input = new BufferedInputStream(source);
        input.mark(4);
        byte[] magic = new byte[4];
        int count = input.read(magic);
        input.reset();
        if (count != 4 || magic[0] != 'P' || magic[1] != 'K' || magic[2] != 3 || magic[3] != 4) {
            TextImporter.Result text = TextImporter.prepare(input, staging, null, observer);
            TxtCacheFingerprint.write(staging, observer);
            return new Prepared(
                    text.id,
                    title(name),
                    "txt",
                    text.charset,
                    text.length,
                    text.sourceBytes,
                    DocumentLocation.txt(0),
                    null);
        }
        if (!staging.mkdirs()) throw new IOException("Cannot create EPUB staging");
        File original = new File(staging, "original.bin");
        long total = 0;
        try (InputStream in = input;
                FileOutputStream out = new FileOutputStream(original)) {
            byte[] bytes = new byte[16384];
            int n;
            while ((n = in.read(bytes)) != -1) {
                TextImporter.check(observer);
                total += n;
                if (total > 256L * 1024 * 1024)
                    throw new IOException("EPUB source exceeds 256 MiB");
                if (observer != null) observer.beforeWrite(n);
                out.write(bytes, 0, n);
                if (observer != null) observer.progress("保存 EPUB 原件", total);
            }
            out.getFD().sync();
        }
        String id = CoverStore.hash(original, observer);
        try (EpubArchive archive = new EpubArchive(original)) {
            byte[] cover = null;
            if (archive.cover != null)
                try {
                    cover = archive.read(archive.cover, 4 * 1024 * 1024);
                } catch (IOException ignored) {
                }
            return new Prepared(
                    id,
                    archive.title,
                    "epub",
                    "EPUB",
                    1000000,
                    total,
                    EpubDocumentSession.location(archive.spine.get(0), 0, ""),
                    cover);
        }
    }

    private static String title(String name) {
        String value =
                name == null
                        ? ""
                        : name.replaceAll("[\\p{Cntrl}]", " ")
                                .replaceFirst("(?i)\\.(txt|epub)$", "")
                                .trim();
        return value.isEmpty() ? "未命名图书" : value.substring(0, Math.min(160, value.length()));
    }
}

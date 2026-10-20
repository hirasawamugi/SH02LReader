package com.sh02l.reader;

import com.sh02l.reader.core.text.TextImporter;

import java.io.*;
import java.nio.file.Files;

/** Fingerprints are published only after complete normalized bytes have been synced. */
final class TxtCacheFingerprint {
    static void write(File folder, TextImporter.Observer observer) throws IOException {
        String hash = CoverStore.hash(new File(folder, "text.u16"), observer);
        TextImporter.check(observer);
        if (observer != null) observer.beforeWrite(64);
        File temporary = new File(folder, ".cache.sha256.tmp");
        if (Files.isSymbolicLink(temporary.toPath()))
            throw new IOException("Unsafe cache fingerprint staging");
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                out.write(hash.getBytes("US-ASCII"));
                out.getFD().sync();
            }
            if (!temporary.renameTo(new File(folder, "cache.sha256")))
                throw new IOException("Cannot publish cache fingerprint");
            DurableFiles.syncDirectory(folder);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }
}

package com.sh02l.reader;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AtomicFile;
import com.sh02l.reader.core.importing.ImportCoordinator;
import com.sh02l.reader.core.text.TextImporter;
import java.io.*;
import java.util.UUID;

/** One imported font with an atomic replacement; old fonts do not accumulate. */
final class FontStore {
    private final File directory, original;
    FontStore(Context context) { directory = new File(context.getFilesDir(), "font"); original = new File(directory, "custom.ttf"); }
    Typeface load() {
        try { new AtomicFile(original).openRead().close(); return Typeface.createFromFile(original); }
        catch (IOException | RuntimeException error) { return Typeface.DEFAULT; }
    }
    boolean available() { return original.isFile(); }
    long bytes() { return original.length(); }
    void remove() throws IOException {
        new AtomicFile(original).delete();
        if (original.exists()) throw new IOException("无法移除自定义字体");
    }
    void cleanupStaging() throws IOException {
        File[] files = directory.listFiles(file -> file.getName().matches("\\.font-[0-9a-f-]{36}"));
        if (files != null) for (File file : files) if (file.isFile() && !file.delete()) throw new IOException("无法清理字体暂存");
    }
    ImportCoordinator.CommittedAsset importFont(InputStream source, TextImporter.Observer observer) throws IOException {
        File staging = new File(directory, ".font-" + UUID.randomUUID());
        try (InputStream in = source) {
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建字体目录");
            long total = 0;
            try (FileOutputStream out = new FileOutputStream(staging)) {
                byte[] buffer = new byte[16384]; int count;
                while ((count = in.read(buffer)) != -1) {
                    TextImporter.check(observer); total += count;
                    if (total > 8L * 1024 * 1024) throw new IOException("字体文件超过 8 MiB");
                    if (observer != null) observer.beforeWrite(count); out.write(buffer, 0, count);
                }
                out.getFD().sync();
            }
            try { Typeface.createFromFile(staging); } catch (RuntimeException error) { throw new IOException("字体无法读取", error); }
            AtomicFile target = new AtomicFile(original); FileOutputStream output = null;
            try {
                TextImporter.check(observer); output = target.startWrite();
                try (InputStream input = new FileInputStream(staging)) {
                    byte[] buffer = new byte[16384]; int count;
                    while ((count = input.read(buffer)) != -1) { TextImporter.check(observer); if (observer != null) observer.beforeWrite(count); output.write(buffer, 0, count); }
                }
                output.getFD().sync(); target.finishWrite(output);
            } catch (IOException | RuntimeException error) { if (output != null) target.failWrite(output); throw error; }
            String hash = ImportCoordinator.sha256(original);
            return new ImportCoordinator.CommittedAsset(hash, original, hash);
        } finally { if (staging.exists() && !staging.delete()) staging.deleteOnExit(); }
    }
}

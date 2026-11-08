package com.sh02l.reader;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.view.*;
import android.widget.*;

import com.sh02l.reader.NavigationState.*;
import com.sh02l.reader.core.Palette;
import com.sh02l.reader.core.text.TextImporter;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** Owns appearance drafts, bounded image work and preview; never changes reading preferences. */
final class AppearanceFeatures implements AutoCloseable {
    static final int PICK_BACKGROUND = 41;
    private final FeatureHost host;
    private final BackgroundStore store;
    private final LatestWorker worker = new LatestWorker();
    private final Consumer<ThemeSpec> applied;
    private ThemeSpec active, draft;
    private Bitmap activeBitmap, previewBitmap;
    private File source;
    private Screen editor;
    private volatile long epoch;
    private boolean busy, committing, closed;
    int revision;

    AppearanceFeatures(
            FeatureHost host, int legacyTheme, int legacyStyle, Consumer<ThemeSpec> applied) {
        this.host = host;
        this.applied = applied;
        store = new BackgroundStore(host.activity());
        active = store.load(legacyTheme, legacyStyle);
    }

    ThemeSpec active() {
        return active;
    }

    void start() {
        final long token = ++epoch;
        final ThemeSpec saved = active.copy();
        worker.submit(
                () -> {
                    Bitmap bitmap = null;
                    try {
                        store.cleanup(saved.asset);
                        if (!saved.asset.isEmpty())
                            bitmap = decode(store.file(saved.asset), saved, false);
                    } catch (Exception ignored) {
                        /* Missing custom assets fall back to the configured plain palette. */
                    }
                    final Bitmap result = bitmap;
                    host.activity()
                            .runOnUiThread(
                                    () -> {
                                        if (closed || epoch != token) {
                                            discard(result);
                                            return;
                                        }
                                        activeBitmap = result;
                                        revision++;
                                        host.render();
                                    });
                });
    }

    Palette palette() {
        return active.palette();
    }

    Drawable background() {
        return new Backdrop(active, activeBitmap);
    }

    boolean blocksBack(Screen screen) {
        return screen == editor && committing;
    }

    void removed(Screen screen) {
        if (screen == editor) cancel();
    }

    void open() {
        if (draft != null) return;
        draft = active.copy();
        source = null;
        previewBitmap = null;
        editor = new Screen(Kind.THEME_EDITOR, "界面外观 · F3 保存 / F4 放弃");
        editor.primary = this::save;
        fill();
        host.push(editor);
        host.render();
    }

    private void fill() {
        if (editor == null || draft == null) return;
        editor.rows.clear();
        editor.rows.add(
                Row.adjust(
                        "theme-preset",
                        "配色：" + ThemeSpec.NAMES[draft.preset],
                        n -> {
                            if (busy) return;
                            draft.preset((draft.preset + n + 5) % 5);
                            fill();
                        }));
        editor.rows.add(Row.action("theme-colors", "自定义界面颜色", this::colors));
        editor.rows.add(
                Row.adjust(
                        "theme-style",
                        "书架样式：" + new String[] {"纯文字", "小书标", "独立封面"}[draft.style],
                        n -> {
                            if (busy) return;
                            draft.style = (draft.style + n + 3) % 3;
                            fill();
                        }));
        editor.rows.add(Row.action("theme-image", "选择静态背景图片", this::choose));
        editor.rows.add(
                Row.adjust(
                        "theme-fit",
                        "缩放：" + (draft.fit == 0 ? "裁剪铺满" : "完整显示"),
                        n -> changeCrop(true, n)));
        editor.rows.add(
                Row.adjust(
                        "theme-position",
                        "对齐：" + new String[] {"居中", "靠上", "靠下", "靠左", "靠右"}[draft.position],
                        n -> changeCrop(false, n)));
        editor.rows.add(
                Row.adjust(
                        "theme-mask",
                        "背景遮罩：" + new String[] {"无", "浅", "中", "深"}[draft.mask],
                        n -> {
                            if (busy) return;
                            draft.mask = (draft.mask + n + 4) % 4;
                            fill();
                        }));
        editor.rows.add(Row.action("theme-preview", busy ? "正在处理图片…" : "预览书架", this::preview));
        editor.rows.add(
                Row.action(
                        "theme-remove",
                        "关闭背景",
                        () -> {
                            if (busy) return;
                            source = null;
                            previewBitmap = null;
                            draft.asset = "";
                            fill();
                        }));
        editor.rows.add(
                Row.action(
                        "theme-default",
                        "恢复默认外观（保存后生效）",
                        () -> {
                            if (busy) return;
                            draft = new ThemeSpec(0, 0);
                            source = null;
                            previewBitmap = null;
                            fill();
                        }));
    }

    private void changeCrop(boolean fit, int direction) {
        if (busy || draft == null) return;
        if (source == null && !draft.asset.isEmpty()) {
            host.notice("调整已保存背景的裁剪，请重新选择原图片");
            return;
        }
        if (fit) draft.fit = (draft.fit + 1) % 2;
        else draft.position = (draft.position + direction + 5) % 5;
        previewBitmap = null;
        fill();
    }

    private void colors() {
        if (busy) return;
        Screen screen = new Screen(Kind.THEME_COLORS, "自定义颜色 · 六位 RGB");
        fillColors(screen);
        host.push(screen);
        host.render();
    }

    private void fillColors(Screen screen) {
        screen.rows.clear();
        for (int i = 0; i < 7; i++) {
            final int role = i;
            screen.rows.add(
                    Row.action(
                            "color-" + i,
                            ThemeSpec.ROLES[i] + "：" + hex(draft.colors[i]),
                            () ->
                                    host.input(
                                            ThemeSpec.ROLES[role] + " · RRGGBB",
                                            hex(draft.colors[role]),
                                            false,
                                            text -> {
                                                String value = text.trim().replaceFirst("^#", "");
                                                if (!value.matches("[0-9a-fA-F]{6}"))
                                                    return "请输入六位十六进制颜色";
                                                draft.colors[role] =
                                                        0xff000000
                                                                | (int) Long.parseLong(value, 16);
                                                fillColors(screen);
                                                return null;
                                            })));
        }
        screen.rows.add(new Row("back", "返回外观草稿"));
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "%06X", color & 0xffffff);
    }

    private void choose() {
        if (busy) return;
        Screen screen = new Screen(Kind.THEME_FILES, "选择背景来源");
        screen.rows.add(
                Row.action(
                        "image-system",
                        "系统文件选择器",
                        () -> {
                            try {
                                host.activity()
                                        .startActivityForResult(
                                                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                                        .setType("image/*"),
                                                PICK_BACKGROUND);
                            } catch (ActivityNotFoundException error) {
                                host.notice("没有系统文件选择器，请使用导入文件夹");
                            }
                        }));
        for (File folder : host.activity().getExternalFilesDirs("import"))
            if (folder != null)
                screen.rows.add(
                        Row.action(
                                "image-root",
                                "导入文件夹 · " + folder.getAbsolutePath(),
                                () -> directory(screen, folder)));
        File shared = Environment.getExternalStorageDirectory();
        screen.rows.add(Row.action("image-shared", "手机存储", () -> directory(screen, shared)));
        File[] external = host.activity().getExternalFilesDirs(null);
        for (File folder : external)
            if (folder != null) {
                String path = folder.getAbsolutePath();
                int at = path.indexOf("/Android/");
                if (at > 0) {
                    File volume = new File(path.substring(0, at));
                    if (!volume.equals(shared))
                        screen.rows.add(
                                Row.action(
                                        "image-volume",
                                        "存储卡 · " + volume,
                                        () -> directory(screen, volume)));
                }
            }
        screen.rows.add(new Row("back", "返回外观草稿"));
        host.push(screen);
        host.render();
    }

    private void directory(Screen screen, File directory) {
        host.task(
                "读取图片目录…",
                () -> {
                    File[] entries = directory.listFiles();
                    if (entries == null) throw new IOException("目录无法读取，可使用系统文件选择器或应用导入文件夹");
                    List<File> files = new ArrayList<>();
                    for (File f : entries)
                        if (f.isDirectory() || f.getName().matches("(?i).*\\.(png|jpe?g|webp)$"))
                            files.add(f);
                    files.sort(
                            (a, b) ->
                                    a.isDirectory() != b.isDirectory()
                                            ? (a.isDirectory() ? -1 : 1)
                                            : a.getName().compareToIgnoreCase(b.getName()));
                    return files;
                },
                files -> {
                    screen.rows.clear();
                    screen.selected = 0;
                    screen.hasSelectionTop = false;
                    screen.rows.add(new Row("back", "返回外观草稿"));
                    File parent = directory.getParentFile();
                    if (parent != null)
                        screen.rows.add(
                                Row.action("image-up", "↑ 上一级", () -> directory(screen, parent)));
                    // Keep one screen and at most 100 entries per page, including exceptionally
                    // large folders.
                    fillDirectoryPage(screen, directory, files, 0);
                });
    }

    private void fillDirectoryPage(Screen screen, File folder, List<File> files, int offset) {
        while (screen.rows.size() > 2) screen.rows.remove(screen.rows.size() - 1);
        for (int i = offset; i < Math.min(files.size(), offset + 100); i++) {
            File f = files.get(i);
            screen.rows.add(
                    Row.action(
                            "image-file-" + i,
                            (f.isDirectory() ? "▸ " : "") + f.getName(),
                            () -> {
                                if (f.isDirectory()) directory(screen, f);
                                else {
                                    host.back();
                                    importImage(() -> new FileInputStream(f));
                                }
                            }));
        }
        if (offset > 0)
            screen.rows.add(
                    Row.action(
                            "image-prev",
                            "← 上一批",
                            () -> {
                                screen.selected = 0;
                                fillDirectoryPage(screen, folder, files, Math.max(0, offset - 100));
                                host.render();
                            }));
        if (offset + 100 < files.size())
            screen.rows.add(
                    Row.action(
                            "image-next",
                            "下一批 →",
                            () -> {
                                screen.selected = 0;
                                fillDirectoryPage(screen, folder, files, offset + 100);
                                host.render();
                            }));
        host.render();
    }

    void picked(Uri uri) {
        if (draft == null || uri == null) return;
        host.back();
        importImage(() -> host.activity().getContentResolver().openInputStream(uri));
    }

    private interface Source {
        InputStream open() throws IOException;
    }

    private void importImage(Source input) {
        if (busy || draft == null) return;
        busy = true;
        fill();
        host.notice("正在准备静态背景…");
        long token = ++epoch;
        ThemeSpec spec = draft.copy();
        worker.submit(
                () -> {
                    File copied = null;
                    Bitmap bitmap = null;
                    try {
                        copied = store.copySource(input.open());
                        bitmap = decode(copied, spec, true);
                        TextImporter.check(null);
                        final File file = copied;
                        final Bitmap result = bitmap;
                        host.activity()
                                .runOnUiThread(
                                        () -> {
                                            if (closed || epoch != token) {
                                                discard(result);
                                                file.delete();
                                                return;
                                            }
                                            if (source != null) source.delete();
                                            source = file;
                                            previewBitmap = result;
                                            draft.asset = "";
                                            busy = false;
                                            fill();
                                            host.notice("背景已放入草稿，预览后按 F3 保存");
                                        });
                    } catch (Exception error) {
                        discard(bitmap);
                        if (copied != null) copied.delete();
                        failed(token, error);
                    }
                });
    }

    private Bitmap decode(File file, ThemeSpec spec, boolean crop) throws IOException {
        android.util.DisplayMetrics m = host.activity().getResources().getDisplayMetrics();
        double factor = Math.min(1, Math.sqrt(518400.0 / ((long) m.widthPixels * m.heightPixels)));
        int w = Math.max(1, (int) (m.widthPixels * factor)),
                h = Math.max(1, (int) (m.heightPixels * factor));
        return CoverDecoder.decode(file, w, h, crop && spec.fit == 1, crop ? spec.position : 0);
    }

    private void prepared(Runnable done) {
        if (busy || draft == null) return;
        if (source == null || previewBitmap != null) {
            done.run();
            return;
        }
        busy = true;
        fill();
        host.notice("正在调整背景…");
        long token = ++epoch;
        File file = source;
        ThemeSpec spec = draft.copy();
        worker.submit(
                () -> {
                    try {
                        Bitmap result = decode(file, spec, true);
                        host.activity()
                                .runOnUiThread(
                                        () -> {
                                            if (closed || token != epoch) {
                                                discard(result);
                                                return;
                                            }
                                            previewBitmap = result;
                                            busy = false;
                                            fill();
                                            done.run();
                                            host.render();
                                        });
                    } catch (Exception error) {
                        failed(token, error);
                    }
                });
    }

    private void preview() {
        prepared(
                () -> {
                    host.push(new Screen(Kind.THEME_PREVIEW, "外观预览"));
                    host.render();
                });
    }

    View previewView() {
        FrameLayout frame = new FrameLayout(host.activity());
        frame.setBackground(
                new Backdrop(
                        draft,
                        source != null
                                ? previewBitmap
                                : draft.asset.isEmpty() ? null : activeBitmap));
        LinearLayout content = new LinearLayout(host.activity());
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(18 * host.activity().getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        Palette p = draft.palette();
        UiStyle style = new UiStyle(host.activity(), p);
        content.addView(style.title("我的书架 · 外观预览"));
        BookRowView first = new BookRowView(host.activity());
        first.bind(
                new LibraryAdapter.Item(null, false, 0, "preview", "长篇小说 · 继续阅读"),
                draft.style,
                p,
                true);
        content.addView(first);
        BookRowView second = new BookRowView(host.activity());
        second.bind(
                new LibraryAdapter.Item(null, false, 0, "preview", "下一段故事"), draft.style, p, false);
        content.addView(second);
        TextView note = new TextView(host.activity());
        note.setTextSize(13);
        note.setTextColor(p.foreground);
        note.setBackgroundColor(p.surface);
        note.setText(
                "F4 返回草稿 · 在外观页按 F3 保存\n"
                        + (contrast(p.foreground, p.selected) < 3
                                        || contrast(p.foreground, p.background) < 3
                                ? "提示：文字与背景接近，请提高对比度或加深遮罩。"
                                : "图片只用于书架，正文保留独立配色。"));
        content.addView(note);
        frame.addView(content, new FrameLayout.LayoutParams(-1, -1));
        return frame;
    }

    private static double luminance(int c) {
        double sum = 0;
        double[] weights = {.2126, .7152, .0722};
        int[] parts = {Color.red(c), Color.green(c), Color.blue(c)};
        for (int i = 0; i < 3; i++) {
            double v = parts[i] / 255.0;
            sum += weights[i] * (v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4));
        }
        return sum;
    }

    private static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }

    private void save() {
        prepared(
                () -> {
                    committing = busy = true;
                    fill();
                    host.notice("正在保存外观…");
                    long token = ++epoch;
                    ThemeSpec value = draft.copy();
                    Bitmap image = source == null ? null : previewBitmap;
                    worker.submit(
                            () -> {
                                try {
                                    ThemeSpec saved = store.save(value, image);
                                    try {
                                        store.cleanup(saved.asset);
                                    } catch (IOException ignored) {
                                        /* Startup retries orphan cleanup. */
                                    }
                                    host.activity()
                                            .runOnUiThread(
                                                    () -> {
                                                        if (closed || token != epoch) return;
                                                        active = saved;
                                                        activeBitmap =
                                                                image != null
                                                                        ? image
                                                                        : saved.asset.isEmpty()
                                                                                ? null
                                                                                : activeBitmap;
                                                        revision++;
                                                        committing = busy = false;
                                                        applied.accept(saved);
                                                        host.back();
                                                        host.notice("外观已保存");
                                                    });
                                } catch (Exception error) {
                                    failed(token, error);
                                }
                            });
                });
    }

    private void failed(long token, Exception error) {
        host.activity()
                .runOnUiThread(
                        () -> {
                            if (closed || token != epoch) return;
                            busy = committing = false;
                            fill();
                            host.notice(
                                    error instanceof IOException
                                            ? error.getMessage()
                                            : "图片处理失败，请选择静态 JPG / PNG / WebP");
                        });
    }

    private void cancel() {
        epoch++;
        worker.cancel();
        draft = null;
        editor = null;
        previewBitmap = null;
        source = null;
        busy = committing = false;
        if (activeBitmap == null && !active.asset.isEmpty()) {
            start();
            return;
        }
        String keep = active.asset;
        worker.submit(
                () -> {
                    try {
                        store.cleanup(keep);
                    } catch (IOException ignored) {
                    }
                });
    }

    private static void discard(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    @Override
    public void close() {
        closed = true;
        epoch++;
        worker.close();
        activeBitmap = previewBitmap = null;
    }

    private static final class Backdrop extends Drawable {
        private final ThemeSpec spec;
        private final Bitmap bitmap;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

        Backdrop(ThemeSpec spec, Bitmap bitmap) {
            this.spec = spec.copy();
            this.bitmap = bitmap;
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawColor(spec.colors[0]);
            if (bitmap != null && !bitmap.isRecycled()) {
                canvas.drawBitmap(bitmap, null, getBounds(), paint);
                int alpha = new int[] {0, 48, 100, 160}[spec.mask];
                canvas.drawColor((alpha << 24) | (spec.colors[0] & 0xffffff));
            }
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(ColorFilter filter) {}

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}

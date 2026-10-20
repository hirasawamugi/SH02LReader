package com.sh02l.reader;

import com.sh02l.reader.NavigationState.Kind;
import com.sh02l.reader.NavigationState.Row;
import com.sh02l.reader.NavigationState.Screen;
import com.sh02l.reader.core.ContentEntry;
import com.sh02l.reader.core.text.TextImporter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** User-selected library mutations and storage management; never deletes a source elsewhere. */
final class LibraryFeatures {
    private final FeatureHost host;

    LibraryFeatures(FeatureHost host) {
        this.host = host;
    }

    void addBookActions(Screen screen, BookRepository.Book book) {
        final String id = book.id;
        screen.rows.add(
                Row.action(
                        "rename-book",
                        "重命名图书",
                        () ->
                                host.input(
                                        "重命名图书",
                                        book.title,
                                        false,
                                        text -> {
                                            String value = text.trim();
                                            if (value.isEmpty() || value.length() > 160)
                                                return "请输入 1–160 个字符";
                                            host.task(
                                                    "正在保存书名…",
                                                    () -> {
                                                        host.repository().renameBook(id, value);
                                                        return id;
                                                    },
                                                    host::refreshShelf);
                                            return null;
                                        })));
        screen.rows.add(
                Row.action(
                        "remove-cover",
                        "移除自定义封面",
                        () ->
                                confirm(
                                        "移除封面",
                                        "保留图书，恢复默认封面",
                                        () -> {
                                            host.task(
                                                    "正在移除封面…",
                                                    () -> {
                                                        new CoverStore(host.activity()).remove(id);
                                                        return id;
                                                    },
                                                    host::refreshShelf);
                                        })));
        screen.rows.add(
                Row.action(
                        "delete-book",
                        "删除图书及私有数据",
                        () ->
                                host.task(
                                        "统计该书占用…",
                                        () -> host.storage().bookBytes(id),
                                        bytes -> {
                                            confirm(
                                                    "删除《" + book.title + "》",
                                                    "释放约 " + size(bytes) + "；保留外部来源文件",
                                                    () ->
                                                            deleteBooks(
                                                                    java.util.Collections
                                                                            .singletonList(id)));
                                            host.render();
                                        })));
    }

    void bookmark(BookRepository.Book book, ContentEntry mark) {
        Screen screen = new Screen(Kind.MARK_ACTION, "书签操作", book.id);
        screen.rows.add(new Row("back", "返回书签"));
        screen.selected = 1;
        screen.rows.add(
                Row.action(
                        "jump",
                        "跳转到此书签",
                        () -> {
                            host.closeOverlays();
                            host.seekTo(mark.location);
                        }));
        screen.rows.add(
                Row.action(
                        "rename",
                        "重命名书签",
                        () ->
                                host.input(
                                        "书签名称",
                                        mark.title,
                                        false,
                                        text -> {
                                            String value = text.trim();
                                            if (value.isEmpty() || value.length() > 160)
                                                return "请输入 1–160 个字符";
                                            host.task(
                                                    "保存书签…",
                                                    () -> {
                                                        host.repository()
                                                                .renameBookmark(
                                                                        book.id,
                                                                        mark.location,
                                                                        value);
                                                        return true;
                                                    },
                                                    ignored -> {
                                                        host.closeOverlays();
                                                        host.notice("书签名称已保存");
                                                    });
                                            return null;
                                        })));
        screen.rows.add(
                Row.action(
                        "delete",
                        "删除此书签",
                        () ->
                                confirm(
                                        "删除书签",
                                        mark.title,
                                        () -> {
                                            host.task(
                                                    "删除书签…",
                                                    () -> {
                                                        host.repository()
                                                                .removeBookmark(
                                                                        book.id, mark.location);
                                                        return true;
                                                    },
                                                    ignored -> {
                                                        host.closeOverlays();
                                                        host.notice("书签已删除");
                                                    });
                                        })));
        host.push(screen);
        host.render();
    }

    void openStorage() {
        Screen screen = new Screen(Kind.STORAGE, "存储管理");
        screen.rows.add(new Row("noop", "正在统计私有书库…"));
        screen.rows.add(new Row("back", "返回"));
        host.push(screen);
        host.task(
                "统计存储占用…",
                () -> host.storage().scan(),
                snapshot -> {
                    screen.rows.clear();
                    screen.rows.add(new Row("noop", "图书原件：" + size(snapshot.originals)));
                    screen.rows.add(new Row("noop", "正文缓存：" + size(snapshot.caches)));
                    screen.rows.add(new Row("noop", "封面：" + size(snapshot.covers)));
                    screen.rows.add(
                            new Row(
                                    "noop",
                                    "自定义字体：" + size(new FontStore(host.activity()).bytes())));
                    screen.rows.add(new Row("noop", "导入文件夹：" + size(snapshot.imports)));
                    screen.rows.add(new Row("noop", "可用空间：" + size(snapshot.free)));
                    screen.rows.add(
                            Row.action(
                                    "manage-books",
                                    "按占用管理 / 批量删除图书",
                                    () -> storageBooks(snapshot)));
                    screen.rows.add(
                            Row.action(
                                    "clear-cache",
                                    "清理可重建正文缓存",
                                    () ->
                                            confirm(
                                                    "清理正文缓存",
                                                    "保留原件、进度、书签和当前阅读书",
                                                    () -> {
                                                        final String active =
                                                                host.reading() == null
                                                                        ? ""
                                                                        : host.reading().book.id;
                                                        host.task(
                                                                "核验原件并清理缓存…",
                                                                () ->
                                                                        host.storage()
                                                                                .clearCaches(
                                                                                        active),
                                                                freed ->
                                                                        storageUpdated(
                                                                                "已释放 "
                                                                                        + size(
                                                                                                freed)));
                                                    })));
                    screen.rows.add(Row.action("manage-import", "选择并清理导入文件夹", this::importFiles));
                    screen.rows.add(new Row("back", "返回"));
                    host.render();
                });
    }

    private void storageBooks(StorageManager.Snapshot snapshot) {
        Screen screen = new Screen(Kind.STORAGE_BOOKS, "选择图书 · 确认勾选");
        fillBooks(screen, snapshot);
        host.push(screen);
        host.render();
    }

    private void fillBooks(Screen screen, StorageManager.Snapshot snapshot) {
        screen.rows.clear();
        for (StorageManager.BookUsage usage : snapshot.books) {
            String id = usage.book.id;
            screen.rows.add(
                    Row.action(
                            "storage-book-" + id,
                            (screen.checked.contains(id) ? "☑ " : "☐ ")
                                    + usage.book.title
                                    + " · "
                                    + size(usage.totalBytes()),
                            () -> {
                                if (!screen.checked.remove(id)) screen.checked.add(id);
                                fillBooks(screen, snapshot);
                                host.render();
                            }));
        }
        if (snapshot.books.isEmpty()) screen.rows.add(new Row("noop", "书库为空"));
        screen.rows.add(
                Row.action(
                        "delete-selected",
                        "删除选中图书（" + screen.checked.size() + "）",
                        () -> {
                            if (screen.checked.isEmpty()) {
                                host.notice("请先选择图书");
                                return;
                            }
                            List<String> ids = new ArrayList<>(screen.checked);
                            confirm(
                                    "删除 " + ids.size() + " 本图书",
                                    "删除私有原件、缓存、封面及书签；保留来源",
                                    () -> deleteBooks(ids));
                        }));
        screen.rows.add(new Row("back", "返回"));
    }

    private void deleteBooks(List<String> ids) {
        for (String id : ids) {
            host.quiesceBook(id);
            host.forgetBook(id);
        }
        host.task(
                "删除所选私有图书…",
                () -> {
                    for (String id : ids) {
                        TextImporter.check(null);
                        host.repository().deleteBook(id);
                    }
                    return ids.size();
                },
                count -> {
                    host.retryProgress();
                    host.refreshShelf("");
                    host.notice("已删除 " + count + " 本图书，来源文件保留");
                });
    }

    private void importFiles() {
        Screen screen = new Screen(Kind.IMPORT_CLEANUP, "清理导入文件 · 确认勾选");
        screen.rows.add(new Row("noop", "读取导入文件夹…"));
        host.push(screen);
        host.task(
                "读取应用导入文件夹…",
                () -> host.storage().listImportFiles(),
                files -> fillImports(screen, files));
    }

    private void fillImports(Screen screen, List<File> files) {
        screen.rows.clear();
        for (File file : files) {
            String id = file.getAbsolutePath();
            screen.rows.add(
                    Row.action(
                            "import-" + id,
                            (screen.checked.contains(id) ? "☑ " : "☐ ")
                                    + file.getName()
                                    + " · "
                                    + size(file.length()),
                            () -> {
                                if (!screen.checked.remove(id)) screen.checked.add(id);
                                fillImports(screen, files);
                            }));
        }
        if (files.isEmpty()) screen.rows.add(new Row("noop", "导入文件夹为空"));
        screen.rows.add(
                Row.action(
                        "clear-selected",
                        "删除所选导入文件（" + screen.checked.size() + "）",
                        () -> {
                            List<File> selected = new ArrayList<>();
                            for (File file : files)
                                if (screen.checked.contains(file.getAbsolutePath()))
                                    selected.add(file);
                            if (selected.isEmpty()) {
                                host.notice("请先选择文件");
                                return;
                            }
                            confirm(
                                    "删除 " + selected.size() + " 个导入文件",
                                    "仅清理所选 import 文件；私有书库保持可读",
                                    () -> {
                                        host.task(
                                                "清理所选导入文件…",
                                                () -> host.storage().deleteImportFiles(selected),
                                                freed -> storageUpdated("导入目录已释放 " + size(freed)));
                                    });
                        }));
        screen.rows.add(new Row("back", "返回"));
        host.render();
    }

    private void storageUpdated(String message) {
        host.retryProgress();
        host.closeOverlays();
        openStorage();
        host.notice(message);
    }

    private void confirm(String title, String detail, Runnable action) {
        Screen screen = new Screen(Kind.CONFIRM, title);
        screen.rows.add(new Row("back", "取消"));
        screen.rows.add(new Row("noop", detail));
        screen.rows.add(Row.action("confirm-operation", "确认执行", action));
        host.push(screen);
        host.render();
    }

    static String size(long bytes) {
        return String.format(Locale.ROOT, "%.2f MiB", bytes / 1048576.0);
    }
}

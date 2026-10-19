package com.sh02l.reader;

import com.sh02l.reader.NavigationState.Kind;
import com.sh02l.reader.NavigationState.Row;
import com.sh02l.reader.NavigationState.Screen;
import com.sh02l.reader.core.text.TextImporter;
import com.sh02l.reader.core.text.TextSearcher;
import java.util.Locale;

/** Search, position input, encoding repair and reading layout controls. */
final class ReaderFeatures {
    private final FeatureHost host;
    private String lastQuery = "";
    ReaderFeatures(FeatureHost host) { this.host = host; }
    void addMenu(Screen screen) {
        screen.rows.add(Row.action("search", "搜索正文", this::searchInput));
        screen.rows.add(Row.action("jump", "跳转阅读进度", this::jump));
        screen.rows.add(Row.action("encoding", "纠正文本编码", this::encoding));
    }
    void searchInput() {
        if (host.reading() == null) return;
        host.input("搜索正文（区分大小写）", lastQuery, false, text -> {
            if (text.isEmpty() || text.length() > 256) return "请输入 1–256 个字符";
            lastQuery = text; host.closeOverlays(); search(host.reading(), text, 0); return null;
        });
    }
    private void search(ReaderSession target, String query, long from) {
        if (target == null) return;
        Screen screen = new Screen(Kind.SEARCH_RESULTS, "搜索：" + query, target.book.id);
        screen.rows.add(new Row("noop", "正在搜索… F2 取消")); host.push(screen);
        host.task("搜索正文…", () -> TextSearcher.find(target.normalized, target.index.length, query, from, 100, null), batch -> {
            screen.rows.clear();
            for (TextSearcher.Match match : batch.matches) screen.rows.add(Row.action("match-" + match.offset,
                    String.format(Locale.ROOT, "%.1f%% · %s", target.index.length == 0 ? 0 : 100.0 * match.offset / target.index.length, match.snippet), () -> {
                if (host.reading() != target) return; host.closeOverlays(); host.seekTo(match.offset);
            }));
            if (batch.matches.isEmpty()) screen.rows.add(new Row("noop", "没有找到匹配内容"));
            if (batch.hasMore) screen.rows.add(Row.action("more-results", "继续查找下一批", () -> { host.back(); search(target, query, batch.nextOffset); }));
            screen.rows.add(Row.action("new-search", "重新输入搜索内容", this::searchInput));
            screen.rows.add(new Row("back", "返回正文"));
            host.notice("本批 " + batch.matches.size() + " 条" + (batch.hasMore ? "，可继续查找" : "，已搜索至末尾"));
        });
    }
    private void jump() {
        ReaderSession target = host.reading(); if (target == null) return;
        String initial = String.format(Locale.ROOT, "%.1f", target.index.length == 0 ? 0 : 100.0 * host.currentOffset() / target.index.length);
        host.input("跳转进度（0–100%）", initial, true, text -> {
            try {
                double value = Double.parseDouble(text.trim());
                if (Double.isNaN(value) || Double.isInfinite(value) || value < 0 || value > 100) return "请输入 0–100 的百分比";
                if (host.reading() != target) return "图书已切换，请重新打开";
                host.closeOverlays(); host.seekTo(Math.round(target.index.length * value / 100)); return null;
            } catch (NumberFormatException error) { return "请输入数字，例如 25 或 25.5"; }
        });
    }
    private void encoding() {
        ReaderSession target = host.reading(); if (target == null) return;
        Screen screen = new Screen(Kind.ENCODING, "当前编码：" + target.book.charset, target.book.id);
        String[] names = {"自动识别", "UTF-8", "UTF-16LE", "UTF-16BE", "GB18030"};
        for (int i = 0; i < names.length; i++) {
            final String charset = i == 0 ? null : names[i];
            screen.rows.add(Row.action("charset-" + i, names[i], () -> {
                Screen confirm = new Screen(Kind.CONFIRM, "重新解码正文");
                confirm.rows.add(new Row("noop", "保留原件；进度和书签按阅读比例近似恢复"));
                confirm.rows.add(Row.action("rebuild", "确认使用 " + (charset == null ? "自动识别" : charset), () -> {
                    host.quiesceBook(target.book.id);
                    host.task("重新解码并建立索引…", () -> {
                        host.awaitProgress(); return host.repository().rebuild(target.book.id, charset, null);
                    }, book -> { host.closeOverlays(); host.open(book); host.notice("编码已更新，位置可能略有变化"); });
                }));
                confirm.rows.add(new Row("back", "取消")); host.push(confirm); host.render();
            }));
        }
        screen.rows.add(new Row("back", "返回")); host.push(screen); host.render();
    }
    void addSettings(Screen screen) {
        ReadingSettings settings = host.readingSettings();
        screen.rows.add(Row.adjust("line-extra", "额外行距：" + settings.lineExtra + " dp", direction -> {
            settings.lineExtra = ReadingSettings.bound(settings.lineExtra + direction * 2, 0, 20); changed(screen);
        }));
        screen.rows.add(Row.adjust("margin-h", "左右边距：" + settings.horizontal + " dp", direction -> {
            settings.horizontal = ReadingSettings.bound(settings.horizontal + direction * 2, 0, 40); changed(screen);
        }));
        screen.rows.add(Row.adjust("margin-v", "上下边距：" + settings.vertical + " dp", direction -> {
            settings.vertical = ReadingSettings.bound(settings.vertical + direction * 2, 0, 40); changed(screen);
        }));
        screen.rows.add(Row.action("font-family", "字体：" + settings.fontName(), this::fonts));
    }
    private void changed(Screen screen) {
        host.readingSettings().save(); host.layoutChanged();
        // The host rebuilds setting labels from the single persisted model.
        host.render();
    }
    private void fonts() {
        Screen screen = new Screen(Kind.FONTS, "正文字体");
        String[] names = {"系统字体", "衬线字体", "无衬线字体", "等宽字体", "已导入自定义字体"};
        for (int i = 0; i < names.length; i++) {
            final int font = i;
            screen.rows.add(Row.action("family-" + i, names[i], () -> {
                if (font == 4 && !new FontStore(host.activity()).available()) { host.notice("请先导入 TTF / OTF 字体"); return; }
                host.readingSettings().font = font; host.readingSettings().save(); host.layoutChanged();
                host.back(); host.notice("已选择 " + names[font]);
            }));
        }
        screen.rows.add(Row.action("import-font", "导入 TTF / OTF 字体（≤8 MiB）", host::importFont));
        screen.rows.add(Row.action("remove-font", "移除已导入字体", () -> host.task("移除自定义字体…", () -> {
            new FontStore(host.activity()).remove(); return true;
        }, ignored -> {
            ReadingSettings settings = host.readingSettings(); settings.invalidateFont();
            if (settings.font == 4) settings.font = 0; settings.save(); host.layoutChanged();
            host.back(); host.notice("自定义字体已移除");
        })));
        screen.rows.add(new Row("back", "返回")); host.push(screen); host.render();
    }
}

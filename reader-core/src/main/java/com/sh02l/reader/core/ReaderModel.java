package com.sh02l.reader.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/** Android-independent navigation and the explicitly labelled M0 reading sample. */
public final class ReaderModel implements KeyRouter.Target {
    public enum Kind {
        HOME(false), READER(false), DIAGNOSTICS(false), SETTINGS(false), HELP(false),
        MENU(true), CONTENTS(true), BOOKMARKS(true), EXIT_CONFIRM(true);
        public final boolean overlay;
        Kind(boolean overlay) { this.overlay = overlay; }
    }
    public enum Effect { NONE, SAVE_LOG, CLEAR_LOG, EXIT }
    public static final class Frame {
        public final Kind kind;
        private int selected;
        Frame(Kind kind) { this.kind = kind; }
        public int selected() { return selected; }
    }
    public static final class Item {
        public final String id, label;
        Item(String id, String label) { this.id = id; this.label = label; }
    }

    private final List<Frame> frames = new ArrayList<>();
    private final TreeSet<Integer> bookmarks = new TreeSet<>();
    private long context = 1;
    private Effect effect = Effect.NONE;
    private int page, fontSize = 20, theme, brightness = 8;
    private boolean infoVisible = true;
    private String notice = "方向键移动，确认键打开";

    public ReaderModel() { frames.add(new Frame(Kind.HOME)); }
    public Frame top() { return frames.get(frames.size() - 1); }
    public List<Frame> frames() { return Collections.unmodifiableList(frames); }
    public int page() { return page; }
    public int fontSize() { return fontSize; }
    public int theme() { return theme; }
    public int brightness() { return brightness; }
    public boolean infoVisible() { return infoVisible; }
    public String notice() { return notice; }
    public void setNotice(String notice) { this.notice = notice; }
    @Override public long contextVersion() { return context; }

    public Frame contentFrame() {
        for (int i = frames.size() - 1; i >= 0; i--) if (!frames.get(i).kind.overlay) return frames.get(i);
        return frames.get(0);
    }

    @Override public Action resolve(Key key) {
        Kind kind = top().kind;
        if (key == Key.F2) return kind == Kind.HOME || kind == Kind.READER ? Action.NONE : Action.BACK;
        if (key == Key.F4) return kind == Kind.HOME ? Action.NONE : Action.BACK;
        if (key == Key.F1 || key == Key.F3) {
            if (kind.overlay) return Action.NONE;
            if (kind == Kind.HOME && key == Key.F3) return Action.DIAGNOSTICS;
            return Action.MENU;
        }
        if (key == Key.ZERO) return Action.HELP;
        if (kind == Kind.READER) {
            switch (key) {
                case UP: case LEFT: return Action.PREVIOUS_PAGE;
                case DOWN: case RIGHT: case CENTER: return Action.NEXT_PAGE;
                case ONE: return Action.PREVIOUS_CHAPTER;
                case TWO: return Action.CONTENTS;
                case THREE: return Action.NEXT_CHAPTER;
                case FOUR: return Action.SMALLER;
                case FIVE: return Action.THEME;
                case SIX: return Action.LARGER;
                case SEVEN: return Action.DIMMER;
                case EIGHT: return Action.INFO;
                case NINE: return Action.BRIGHTER;
                case STAR: return Action.SETTINGS;
                case POUND: return Action.BOOKMARKS;
                default: return Action.NONE;
            }
        }
        switch (key) {
            case UP: return Action.UP;
            case DOWN: return Action.DOWN;
            case LEFT: return Action.LEFT;
            case RIGHT: return Action.RIGHT;
            case CENTER: return Action.ACTIVATE;
            default: return Action.NONE;
        }
    }

    @Override public void perform(Action action) {
        switch (action) {
            case UP: select(top().selected - 1); break;
            case DOWN: select(top().selected + 1); break;
            case LEFT: if (top().kind == Kind.SETTINGS) adjust(-1); break;
            case RIGHT: if (top().kind == Kind.SETTINGS) adjust(1); break;
            case ACTIVATE: activate(); break;
            case PREVIOUS_PAGE: goPage(page - 1); break;
            case NEXT_PAGE: goPage(page + 1); break;
            case PREVIOUS_CHAPTER: goPage(Math.max(0, page / 3 - 1) * 3); break;
            case NEXT_CHAPTER: goPage(Math.min(2, page / 3 + 1) * 3); break;
            case MENU: push(Kind.MENU); break;
            case BACK: back(); break;
            case HOME: while (frames.size() > 1) back(); break;
            case DIAGNOSTICS: push(Kind.DIAGNOSTICS); break;
            case HELP: push(Kind.HELP); break;
            case CONTENTS: push(Kind.CONTENTS); break;
            case SETTINGS: push(Kind.SETTINGS); break;
            case BOOKMARKS: push(Kind.BOOKMARKS); break;
            case SMALLER: fontSize = clamp(fontSize - 1, 14, 32); break;
            case LARGER: fontSize = clamp(fontSize + 1, 14, 32); break;
            case THEME: theme = (theme + 1) % 3; break;
            case DIMMER: brightness = clamp(brightness - 1, 1, 10); break;
            case BRIGHTER: brightness = clamp(brightness + 1, 1, 10); break;
            case INFO: infoVisible = !infoVisible; break;
            default: break;
        }
    }

    public void select(int position) {
        top().selected = clamp(position, 0, Math.max(0, items(top()).size() - 1));
    }

    public void back() {
        if (frames.size() > 1) { frames.remove(frames.size() - 1); context++; }
    }

    private void push(Kind kind) {
        if (top().kind == kind || frames.size() >= 8) return;
        frames.add(new Frame(kind)); context++;
        if (kind == Kind.CONTENTS) select(page / 3);
    }

    private void activate() {
        List<Item> items = items(top());
        if (items.isEmpty()) return;
        String id = items.get(top().selected).id;
        switch (id) {
            case "sample": push(Kind.READER); break;
            case "diagnostics": push(Kind.DIAGNOSTICS); break;
            case "settings": push(Kind.SETTINGS); break;
            case "help": push(Kind.HELP); break;
            case "contents": push(Kind.CONTENTS); break;
            case "bookmarks": push(Kind.BOOKMARKS); break;
            case "save-log": effect = Effect.SAVE_LOG; break;
            case "clear-log": effect = Effect.CLEAR_LOG; break;
            case "back": back(); break;
            case "exit": push(Kind.EXIT_CONFIRM); break;
            case "exit-now": effect = Effect.EXIT; break;
            case "font": case "theme": case "brightness": case "info": adjust(1); break;
            case "bookmark-add":
                bookmarks.add(page); notice = "已添加样例书签：第 " + (page + 1) + " 页"; back(); break;
            default:
                if (id.startsWith("chapter-")) { goPage(Integer.parseInt(id.substring(8)) * 3); closeReaderOverlays(); }
                else if (id.startsWith("bookmark-")) { goPage(Integer.parseInt(id.substring(9))); closeReaderOverlays(); }
                break;
        }
    }

    private void closeReaderOverlays() {
        while (top().kind.overlay) back();
    }

    private void adjust(int direction) {
        String id = items(top()).get(top().selected).id;
        switch (id) {
            case "font": fontSize = clamp(fontSize + direction, 14, 32); break;
            case "theme": theme = (theme + direction + 3) % 3; break;
            case "brightness": brightness = clamp(brightness + direction, 1, 10); break;
            case "info": infoVisible = !infoVisible; break;
            default: break;
        }
    }

    private void goPage(int destination) {
        int bounded = clamp(destination, 0, 8);
        if (bounded == page) notice = destination < 0 ? "已经是第一页" : destination > 8 ? "已经是最后一页" : "";
        else { page = bounded; notice = ""; }
    }

    public List<Item> items(Frame frame) {
        List<Item> list = new ArrayList<>();
        switch (frame.kind) {
            case HOME:
                add(list, "sample", "打开阅读样例"); add(list, "diagnostics", "按键诊断");
                add(list, "settings", "阅读设置"); add(list, "help", "按键说明"); break;
            case MENU:
                if (readerInStack()) { add(list, "contents", "样例目录"); add(list, "bookmarks", "样例书签"); }
                add(list, "settings", "阅读设置"); add(list, "diagnostics", "按键诊断");
                add(list, "help", "按键说明"); add(list, "exit", "退出应用"); add(list, "back", "关闭菜单"); break;
            case SETTINGS:
                add(list, "font", "字号：" + fontSize);
                add(list, "theme", "主题：" + themeName());
                add(list, "brightness", "应用亮度：" + brightness * 10 + "%");
                add(list, "info", "阅读信息栏：" + (infoVisible ? "显示" : "隐藏"));
                add(list, "back", "返回"); break;
            case DIAGNOSTICS:
                add(list, "save-log", "保存诊断记录"); add(list, "clear-log", "清空记录"); add(list, "back", "返回"); break;
            case HELP: add(list, "back", "返回"); break;
            case CONTENTS:
                add(list, "chapter-0", "第一节 · 实体按键"); add(list, "chapter-1", "第二节 · 菜单与焦点");
                add(list, "chapter-2", "第三节 · 阅读设置"); break;
            case BOOKMARKS:
                add(list, "bookmark-add", "添加当前页书签");
                for (int bookmark : bookmarks) add(list, "bookmark-" + bookmark, "样例第 " + (bookmark + 1) + " 页");
                add(list, "back", "返回"); break;
            case EXIT_CONFIRM:
                add(list, "back", "继续使用"); add(list, "exit-now", "退出应用"); break;
            default: break;
        }
        return list;
    }
    private static void add(List<Item> items, String id, String label) { items.add(new Item(id, label)); }
    private boolean readerInStack() {
        for (Frame frame : frames) if (frame.kind == Kind.READER) return true;
        return false;
    }
    public Effect takeEffect() { Effect value = effect; effect = Effect.NONE; return value; }
    public String themeName() { return new String[]{"暖色", "浅色", "深色"}[theme]; }
    public String title(Frame frame) {
        switch (frame.kind) {
            case HOME: return "开始阅读";
            case READER: return "阅读样例 · 第 " + (page / 3 + 1) + " 节";
            case MENU: return "菜单";
            case DIAGNOSTICS: return "按键诊断";
            case SETTINGS: return "阅读设置";
            case HELP: return "按键说明";
            case CONTENTS: return "样例目录";
            case BOOKMARKS: return "样例书签";
            case EXIT_CONFIRM: return "退出应用？";
            default: return "SH02L 阅读";
        }
    }

    public String sampleText() {
        String[] paragraphs = {
            "这是 SH-02L 的按键阅读样例。\n\n用上、左翻到上一页；用下、右或确认翻到下一页。这里共有三节、九页样例。\n\n每次短按只应执行一次。可以连续按键，也可以长按方向键检查重复翻页。",
            "这一页用来检查连续操作。\n\n按下与抬起属于同一次按键。松开按键后，页面应立即停止变化。\n\n合盖或切到其他应用，再恢复阅读，也不应继续执行此前的长按。",
            "第一节结束。\n\n按 1、3 切换样例章节；按 2 打开目录。\n\n试着打开菜单后立即松开按键：菜单应保持打开，抬起事件不应执行新的操作。",
            "按 F1 或 F3 打开菜单。\n\n菜单内用上下键选择，确认键执行，F2 返回。\n\n蓝色高亮应始终对应真正能够确认的选项。",
            "菜单和正文共享按键规则。\n\n反复打开菜单、移动焦点、关闭菜单，再打开，应该每次都能立即操作。\n\n关闭菜单只关闭当前层，并恢复先前的位置。",
            "F2 在正文中保持当前阅读；F4 从正文返回首页。\n\n井号键打开样例书签，星号键打开阅读设置。\n\n所有这些操作都能用短按完成。",
            "按 4、6 调整字号，按 5 切换主题。\n\n用 7、9 调整本应用亮度，按 8 显示或隐藏阅读信息栏。\n\n也可以在设置页用左右键调整当前项目。",
            "这里提供暖色、浅色和深色主题。\n\n使用系统字体与直接切换的页面，让小屏设备保持清楚、稳定的阅读体验。\n\n字号和亮度都有边界，连续调整也不应越界。",
            "你已经到达样例的最后一页。\n\n按 0 查看按键说明，或从菜单打开按键诊断。\n\n这是用于验证按键、导航和设置的版本。正式 TXT 文件导入将在后续阅读版本提供。"
        };
        return paragraphs[page];
    }

    public String save() {
        StringBuilder state = new StringBuilder("1|").append(page).append('|').append(fontSize).append('|')
                .append(theme).append('|').append(brightness).append('|').append(infoVisible ? 1 : 0).append('|');
        for (Frame frame : frames) state.append(frame.kind.name()).append(',').append(frame.selected).append(';');
        state.append('|');
        for (int bookmark : bookmarks) state.append(bookmark).append(',');
        return state.toString();
    }

    public static ReaderModel restore(String state) {
        ReaderModel model = new ReaderModel();
        if (state == null || state.length() > 2048) return model;
        try {
            String[] parts = state.split("\\|", -1);
            if (parts.length != 8 || !"1".equals(parts[0])) return model;
            model.page = bounded(parts[1], 0, 8); model.fontSize = bounded(parts[2], 14, 32);
            model.theme = bounded(parts[3], 0, 2); model.brightness = bounded(parts[4], 1, 10);
            model.infoVisible = bounded(parts[5], 0, 1) == 1;
            String[] storedFrames = parts[6].split(";");
            if (storedFrames.length < 1 || storedFrames.length > 8) return new ReaderModel();
            model.frames.clear();
            for (String storedFrame : storedFrames) {
                String[] fields = storedFrame.split(",", -1);
                if (fields.length != 2) throw new IllegalArgumentException("frame");
                Frame frame = new Frame(Kind.valueOf(fields[0]));
                if (model.frames.isEmpty() && frame.kind != Kind.HOME) throw new IllegalArgumentException("root");
                model.frames.add(frame);
                model.select(Integer.parseInt(fields[1]));
            }
            if (!parts[7].isEmpty()) for (String bookmark : parts[7].split(",")) model.bookmarks.add(bounded(bookmark, 0, 8));
            model.notice = "已恢复样例与设置";
            return model;
        } catch (IllegalArgumentException error) { return new ReaderModel(); }
    }
    private static int bounded(String value, int min, int max) {
        int number = Integer.parseInt(value);
        if (number < min || number > max) throw new IllegalArgumentException("range");
        return number;
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}

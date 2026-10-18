package com.sh02l.reader;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.sh02l.reader.core.*;
import com.sh02l.reader.core.text.TextImporter;
import com.sh02l.reader.txt.TxtPaginator;
import java.io.*;
import java.util.*;

/** One window, one key router, and bounded asynchronous TXT sessions. */
public final class MainActivity extends Activity implements KeyRouter.Target {
    private enum Kind {
        LIBRARY, SOURCES, FILES, IMPORT, READER, SETTINGS, HELP, DIAGNOSTICS,
        MENU, CONTENTS, BOOKMARKS, EXIT;
        boolean overlay() { return this==SETTINGS || this==HELP || this==MENU || this==CONTENTS || this==BOOKMARKS || this==EXIT; }
    }
    private static final class Row {
        final String id, label; final Object value;
        Row(String id, String label) { this(id, label, null); }
        Row(String id, String label, Object value) { this.id = id; this.label = label; this.value = value; }
    }
    private static final class Screen {
        final Kind kind; final List<Row> rows = new ArrayList<>();
        int selected, group; String title; File directory;
        Screen(Kind kind, String title) { this.kind = kind; this.title = title; }
    }
    private static final class Session {
        final BookRepository.Book book; final TextImporter.Result index; final TxtPaginator paginator;
        Session(BookRepository.Book book, TextImporter.Result index, File directory) {
            this.book = book; this.index = index;
            this.paginator = new TxtPaginator(new File(directory, "text.u16"), index.length);
        }
    }
    private final List<Screen> screens = new ArrayList<>();
    private final LatestWorker worker = new LatestWorker();
    private final DiagnosticBuffer diagnostics = new DiagnosticBuffer(256);
    private KeyRouter router;
    private BookRepository repository;
    private ProgressWriter progressWriter;
    private DiagnosticStore diagnosticStore;
    private SharedPreferences preferences;
    private AppearanceStore appearanceStore;
    private int uiTheme, bookStyle;
    private FrameLayout root, overlayHost;
    private Screen builtBase, builtOverlay;
    private ListView baseList, overlayList;
    private TextView noticeView, infoView, diagnosticView;
    private PageView pageView;
    private long context = 1, generation, committedOffset = -1;
    private String committedBook = "", notice = "F3 导入 TXT，确认打开图书";
    private String libraryReturnId = "";
    private String builtStyle = "";
    private int builtOverlayTheme=-1;
    private boolean resumed;
    private int fontSize, theme, brightness, foreground, secondary, background, surface;
    private boolean infoVisible, layoutBusy, loadingLibrary, destroyed;
    private Session session;
    private TxtPaginator.Page page;
    private TxtPaginator.Spec requestedSpec;
    private long anchor;
    private int pendingTurns;
    private Uri pendingUri;
    private final Map<String, Long> livePositions = new HashMap<>();

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        preferences = getSharedPreferences("reader", MODE_PRIVATE);
        // Carry the real device's M0 appearance forward, but never treat sample page numbers as book locators.
        ReaderModel prototype = ReaderModel.restore(getSharedPreferences("prototype", MODE_PRIVATE).getString("state", null));
        fontSize = bound(preferences.getInt("font", prototype.fontSize()), 14, 32);
        appearanceStore = new AppearanceStore(this);
        AppearanceState appearance = appearanceStore.load(AppearanceStore.integer(preferences,"theme",prototype.theme()));
        theme = appearance.readerTheme; uiTheme = appearance.uiTheme; bookStyle = appearance.bookStyle;
        brightness = bound(preferences.getInt("brightness", prototype.brightness()), 1, 10);
        infoVisible = preferences.getBoolean("info", prototype.infoVisible());
        repository = BookRepository.obtain(this);
        progressWriter = new ProgressWriter(repository, (id, offset, error) -> runOnUiThread(() -> {
            if (destroyed) return;
            if (error == null) { committedBook = id; committedOffset = offset; }
            else { notice = "阅读位置保存失败，请检查可用空间"; render(); }
        }));
        File logDirectory = getExternalFilesDir("diagnostics");
        diagnosticStore = new DiagnosticStore(logDirectory != null ? logDirectory : new File(getFilesDir(), "diagnostics"));
        router = new KeyRouter(this);
        root = new FrameLayout(this); root.setTag("app-root"); setContentView(root);
        screens.add(new Screen(Kind.LIBRARY, "书架"));
        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) pendingUri = getIntent().getData();
        diagnostics.append("session-created sdk=" + Build.VERSION.SDK_INT + " model=" + Build.MODEL);
        render(); loadLibrary(true);
    }
    private Screen top() { return screens.get(screens.size() - 1); }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) importUri(intent.getData());
    }
    private Screen base() {
        for (int i = screens.size() - 1; i >= 0; i--) if (!screens.get(i).kind.overlay()) return screens.get(i);
        return screens.get(0);
    }
    @Override public long contextVersion() { return context; }
    @Override public Action resolve(Key key) {
        Kind kind = top().kind;
        if (key == Key.F2) return kind == Kind.LIBRARY || kind == Kind.READER ? Action.NONE : Action.BACK;
        if (key == Key.F4) return kind == Kind.LIBRARY ? Action.NONE : Action.BACK;
        if (key == Key.F1 || key == Key.F3) {
            if (kind.overlay()) return Action.NONE;
            if (kind == Kind.LIBRARY && key == Key.F3) return Action.IMPORT;
            return Action.MENU;
        }
        if (key == Key.ZERO) return Action.HELP;
        if (kind == Kind.READER) {
            switch (key) {
                case UP: case LEFT: return Action.PREVIOUS_PAGE;
                case DOWN: case RIGHT: return Action.NEXT_PAGE;
                case CENTER: return Action.ACTIVATE;
                case ONE: return Action.PREVIOUS_CHAPTER;
                case TWO: return Action.CONTENTS;
                case THREE: return Action.NEXT_CHAPTER;
                case FOUR: return Action.SMALLER;
                case SIX: return Action.LARGER;
                case FIVE: return Action.THEME;
                case SEVEN: return Action.DIMMER;
                case NINE: return Action.BRIGHTER;
                case EIGHT: return Action.INFO;
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
            case ACTIVATE: if (top().kind == Kind.READER) turn(1); else activate(); break;
            case BACK: back(); break;
            case HOME: goLibrary(); break;
            case IMPORT: sources(); break;
            case MENU: menu(); break;
            case SETTINGS: settings(); break;
            case DIAGNOSTICS: push(new Screen(Kind.DIAGNOSTICS, "按键诊断")); break;
            case HELP: help(); break;
            case CONTENTS: contents(); break;
            case BOOKMARKS: bookmarks(); break;
            case PREVIOUS_PAGE: turn(-1); break;
            case NEXT_PAGE: turn(1); break;
            case PREVIOUS_CHAPTER: chapter(-1); break;
            case NEXT_CHAPTER: chapter(1); break;
            case SMALLER: fontSize = bound(fontSize - 1, 14, 32); appearanceChanged(); break;
            case LARGER: fontSize = bound(fontSize + 1, 14, 32); appearanceChanged(); break;
            case THEME: theme = (theme + 1) % 3; appearanceChanged(); break;
            case DIMMER: brightness = bound(brightness - 1, 1, 10); saveSettings(); break;
            case BRIGHTER: brightness = bound(brightness + 1, 1, 10); saveSettings(); break;
            case INFO: infoVisible = !infoVisible; appearanceChanged(); break;
            default: break;
        }
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (router == null) return super.dispatchKeyEvent(event);
        KeyInput input = new KeyInput(event.getAction(), event.getKeyCode(), event.getScanCode(), event.getRepeatCount(),
                event.getDeviceId(), event.getDownTime(), event.getEventTime(), event.isCanceled());
        KeyRouter.Result result = router.dispatch(input); diagnostics.record(input, result);
        Log.d("SH02LKeys", diagnostics.tail(1).trim());
        if (result.consumed) { if (!isFinishing()) render(); return true; }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed() { if (top().kind == Kind.LIBRARY) menu(); else back(); render(); }
    private void select(int selected) { top().selected = bound(selected, 0, Math.max(0, top().rows.size() - 1)); }
    private void push(Screen screen) {
        if ((top().kind == screen.kind && screen.kind != Kind.SETTINGS) || screens.size() >= 12) return;
        screens.add(screen); context++;
    }
    private void back() {
        if (screens.size() == 1) return;
        Screen removed = screens.remove(screens.size() - 1); context++;
        if (removed.kind == Kind.IMPORT || removed.kind == Kind.FILES || removed.kind == Kind.SOURCES) cancelWork();
        if (removed.kind == Kind.READER) {
            if (session != null) libraryReturnId = session.book.id;
            savePosition(); cancelWork(); session = null; page = null; requestedSpec = null; pendingTurns = 0;
            preferences.edit().putBoolean("reopen", false).apply(); loadLibrary(false);
        } else if (top().kind == Kind.LIBRARY) loadLibrary(false);
    }
    private void closeOverlays() { while (top().kind.overlay()) back(); }
    private void goLibrary() {
        if (session != null) libraryReturnId = session.book.id;
        savePosition(); cancelWork();
        while (screens.size() > 1) screens.remove(screens.size() - 1);
        context++; session = null; page = null; requestedSpec = null;
        preferences.edit().putBoolean("reopen", false).apply(); loadLibrary(false);
    }
    private void menu() {
        Screen screen = new Screen(Kind.MENU, "菜单");
        if (base().kind == Kind.READER && session != null) { screen.rows.add(new Row("contents", "目录")); screen.rows.add(new Row("bookmarks", "书签")); }
        else if (base().kind == Kind.LIBRARY) screen.rows.add(new Row("import", "导入 TXT"));
        if (base().kind == Kind.IMPORT) screen.rows.add(new Row("cancel-import", "取消导入"));
        screen.rows.add(new Row("settings", "阅读设置")); screen.rows.add(new Row("diagnostics", "按键诊断"));
        screen.rows.add(new Row("help", "按键说明"));
        if (session != null) screen.rows.add(new Row("library", "返回书架"));
        screen.rows.add(new Row("exit", "退出应用")); screen.rows.add(new Row("back", "关闭菜单")); push(screen);
    }
    private void settings() {
        settingsGroup(0);
    }
    private void settingsGroup(int group) {
        Screen screen=new Screen(Kind.SETTINGS,group==1?"界面外观":group==2?"正文阅读":"设置");screen.group=group;
        refreshSettings(screen);push(screen);
    }
    private void refreshSettings(Screen screen) {
        if (screen.kind != Kind.SETTINGS) return;
        screen.rows.clear();
        String[] colors={"米白","柔白","暖炭灰"};
        if(screen.group==0){screen.rows.add(new Row("appearance","界面外观"));screen.rows.add(new Row("reading-settings","正文阅读"));screen.rows.add(new Row("help","按键说明"));}
        else if(screen.group==1){screen.rows.add(new Row("ui-theme","界面主题："+colors[uiTheme]));screen.rows.add(new Row("shelf-style","书架样式："+new String[]{"纯文字","小书标","独立封面"}[bookStyle]));}
        else {screen.rows.add(new Row("theme","阅读配色："+colors[theme]));screen.rows.add(new Row("font","字号："+fontSize));screen.rows.add(new Row("brightness","窗口亮度："+brightness*10+"%"));screen.rows.add(new Row("info","阅读信息栏："+(infoVisible?"显示":"隐藏")));}
    }
    private void adjust(int direction) {
        if (top().rows.isEmpty()) return;
        switch (top().rows.get(top().selected).id) {
            case "ui-theme": uiTheme=(uiTheme+direction+3)%3;saveSettings();refreshSettings(top());return;
            case "shelf-style": bookStyle=(bookStyle+direction+3)%3;saveSettings();refreshSettings(top());return;
            case "font": fontSize = bound(fontSize + direction, 14, 32); break;
            case "theme": theme = (theme + direction + 3) % 3; break;
            case "brightness": brightness = bound(brightness + direction, 1, 10); break;
            case "info": infoVisible = !infoVisible; break;
            default: return;
        }
        appearanceChanged(); refreshSettings(top());
    }
    private void appearanceChanged() {
        saveSettings();
        if (session != null) cancelWork();
        requestedSpec = null; pendingTurns = 0;
    }
    private void saveSettings() {
        appearanceStore.save(new AppearanceState(uiTheme,theme,bookStyle));
        preferences.edit().putInt("font", fontSize).putInt("theme", theme).putInt("brightness", brightness)
                .putBoolean("info", infoVisible).apply();
    }
    private void help() {
        Screen screen = new Screen(Kind.HELP, "按键说明"); screen.rows.add(new Row("back", "返回")); push(screen);
    }
    private void activate() {
        if (top().rows.isEmpty()) return;
        Row row = top().rows.get(bound(top().selected, 0, top().rows.size() - 1));
        switch (row.id) {
            case "import": sources(); break;
            case "book": openBook((BookRepository.Book) row.value); break;
            case "directory": files((File) row.value); break;
            case "file": importFile((File) row.value); break;
            case "shared": requestSharedStorage(); break;
            case "system-picker": systemPicker(); break;
            case "settings": settings(); break;
            case "appearance": settingsGroup(1);break;
            case "reading-settings": settingsGroup(2);break;
            case "diagnostics": {
                Screen screen = new Screen(Kind.DIAGNOSTICS, "按键诊断");
                screen.rows.add(new Row("save-log", "保存诊断记录")); screen.rows.add(new Row("back", "返回")); push(screen); break;
            }
            case "help": help(); break;
            case "contents": contents(); break;
            case "bookmarks": bookmarks(); break;
            case "chapter": closeOverlays(); seek(((TextImporter.Chapter) row.value).offset); break;
            case "bookmark-add": addBookmark(); break;
            case "font": case "theme": case "brightness": case "info": case "ui-theme": case "shelf-style": adjust(1); break;
            case "library": goLibrary(); break;
            case "cancel-import": closeOverlays(); back(); break;
            case "back": back(); break;
            case "exit": {
                Screen screen = new Screen(Kind.EXIT, "退出应用？"); screen.rows.add(new Row("back", "继续使用"));
                screen.rows.add(new Row("exit-now", "退出应用")); push(screen); break;
            }
            case "exit-now": savePosition(); finish(); break;
            case "save-log":
                diagnosticStore.save(snapshot(), (file, error) -> runOnUiThread(() -> {
                    if (destroyed) return; notice = error == null ? "已保存：" + file.getName() : "保存失败，请检查空间"; render();
                })); break;
            default: break;
        }
    }
    private void loadLibrary(boolean initial) {
        loadingLibrary = true;
        final long token = ++generation;
        worker.submit(() -> {
            try {
                if (initial) repository.cleanupStaging();
                List<BookRepository.Book> books = repository.list();
                runOnUiThread(() -> {
                    if (!valid(token)) return; loadingLibrary = false;
                    Screen library = screens.get(0);
                    String selectedId = libraryReturnId.isEmpty() ? selectedId(library) : libraryReturnId;
                    libraryReturnId = "";
                    library.rows.clear();
                    String lastId = preferences.getString("lastBook", ""); BookRepository.Book last = null;
                    for (BookRepository.Book book : books) if (book.id.equals(lastId)) last = book;
                    if (last != null) library.rows.add(new Row("book", "继续阅读 · " + last.title, last));
                    library.rows.add(new Row("import", "＋ 导入 TXT"));
                    for (BookRepository.Book book : books) library.rows.add(new Row("book", book.title + "\n" + book.charset
                            + " · " + size(book.sourceBytes), book));
                    library.title = "书架 · " + books.size() + " 本"; restoreSelection(library, selectedId);
                    invalidateLists(); render();
                    if (pendingUri != null) { Uri uri = pendingUri; pendingUri = null; importUri(uri); }
                    else if (initial && top().kind == Kind.LIBRARY && preferences.getBoolean("reopen", false) && last != null) openBook(last);
                });
            } catch (Exception error) { fail(token, "书架读取失败", error); }
        });
    }
    private void sources() {
        if (base().kind == Kind.IMPORT) { notice = "请先完成或取消当前导入"; return; }
        closeOverlays(); Screen screen = new Screen(Kind.SOURCES, "导入 TXT");
        File importDirectory = getExternalFilesDir("import");
        if (importDirectory != null) { importDirectory.mkdirs(); screen.rows.add(new Row("directory", "应用导入文件夹", importDirectory)); }
        screen.rows.add(new Row("shared", "手机存储 / 内存卡"));
        screen.rows.add(new Row("system-picker", "系统文件选择")); screen.rows.add(new Row("back", "返回"));
        push(screen); notice = "选择 TXT 后复制到私有书库，保留原文件";
    }
    private void requestSharedStorage() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1); return;
        }
        sharedRoots();
    }
    private void sharedRoots() {
        Screen screen = new Screen(Kind.FILES, "手机存储 / 内存卡");
        Set<String> paths = new HashSet<>();
        File primary = Environment.getExternalStorageDirectory();
        screen.rows.add(new Row("directory", "手机存储", primary)); paths.add(primary.getAbsolutePath());
        for (File appDirectory : getExternalFilesDirs(null)) {
            if (appDirectory == null) continue;
            File directory = appDirectory;
            for (int i = 0; i < 4 && directory != null; i++) directory = directory.getParentFile();
            if (directory != null && paths.add(directory.getAbsolutePath())) screen.rows.add(new Row("directory", "内存卡 · " + directory.getName(), directory));
        }
        screen.rows.add(new Row("back", "返回")); push(screen);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 1 && !destroyed) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) sharedRoots();
            else notice = "未获得读取权限，可使用系统文件选择或应用导入文件夹";
            render();
        }
    }
    private void systemPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/*");
            startActivityForResult(intent, 2);
        } catch (ActivityNotFoundException error) { notice = "设备没有系统文件选择器，请使用手机存储入口"; }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 2 && result == RESULT_OK && data != null && data.getData() != null) importUri(data.getData());
    }
    private void files(File directory) {
        // Directories replace the file screen while parent frames retain their exact selection.
        Screen screen = new Screen(Kind.FILES, directory.getName().isEmpty() ? "文件" : directory.getName());
        screen.directory = directory;
        if (top().kind == Kind.FILES && top().directory == null) { screens.remove(screens.size() - 1); context++; }
        if (screens.size() >= 12) { notice = "目录层级过深，请用系统文件选择"; return; }
        screens.add(screen); context++; notice = "读取目录…";
        final long token = ++generation;
        worker.submit(() -> {
            try {
                File[] entries = directory.listFiles(file -> file.isDirectory() || file.getName().toLowerCase(Locale.ROOT).endsWith(".txt"));
                if (entries == null) throw new IOException("Cannot list directory");
                if (entries.length > 10000) throw new IOException("Too many files; select a narrower folder");
                Arrays.sort(entries, (left, right) -> {
                    int kind = Boolean.compare(right.isDirectory(), left.isDirectory());
                    return kind != 0 ? kind : left.getName().compareToIgnoreCase(right.getName());
                });
                List<Row> rows = new ArrayList<>(); rows.add(new Row("back", "↩ 上一层"));
                for (File file : entries) rows.add(new Row(file.isDirectory() ? "directory" : "file",
                        (file.isDirectory() ? "▸ " : "") + file.getName(), file));
                runOnUiThread(() -> {
                    if (!valid(token) || !screens.contains(screen)) return;
                    screen.rows.addAll(rows); notice = entries.length == 0 ? "此目录没有 TXT 文件" : "上下选择，确认打开";
                    invalidateLists(); render();
                });
            } catch (Exception error) { fail(token, "目录不可读取，请返回并检查权限或内存卡", error); }
        });
        render();
    }
    // Package access allows native tests to import only their own generated fixtures through the production path.
    void importFile(File file) { startImport(file.getName(), () -> new FileInputStream(file), file.length()); }
    private interface Source { InputStream open() throws IOException; }
    private void importUri(Uri uri) {
        if (!"content".equals(uri.getScheme())) { notice = "仅支持通过文件提供程序打开 TXT"; render(); return; }
        startImport("文档.txt", () -> getContentResolver().openInputStream(uri), -1, uri);
    }
    private void startImport(String name, Source source, long expected) { startImport(name, source, expected, null); }
    private void startImport(String name, Source source, long expected, Uri uri) {
        if (base().kind == Kind.IMPORT) return;
        savePosition(); cancelWork(); closeOverlays();
        Screen screen = new Screen(Kind.IMPORT, "导入 TXT"); screen.rows.add(new Row("back", "取消导入")); push(screen);
        notice = "准备导入：" + name; render();
        final long token = ++generation;
        worker.submit(() -> {
            try {
                String displayName = name; long sourceSize = expected;
                if (uri != null) try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME), s = cursor.getColumnIndex(OpenableColumns.SIZE);
                        if (n >= 0) displayName = cursor.getString(n);
                        if (s >= 0 && !cursor.isNull(s)) sourceSize = cursor.getLong(s);
                    }
                }
                if (sourceSize > 0 && (sourceSize > Long.MAX_VALUE / 4 || getFilesDir().getUsableSpace() < sourceSize * 4 + 8 * 1024 * 1024))
                    throw new IOException("Insufficient free space");
                final long[] last = {0};
                InputStream input = source.open(); if (input == null) throw new IOException("Source unavailable");
                BookRepository.Book book = repository.importBook(input, displayName, new TextImporter.Observer() {
                    public boolean cancelled() { return Thread.currentThread().isInterrupted(); }
                    public void progress(String phase, long bytes) {
                        long now = SystemClock.uptimeMillis(); if (now - last[0] < 200) return; last[0] = now;
                        String label = "copy".equals(phase) ? "复制" : "encoding".equals(phase) ? "识别编码" : "decode".equals(phase) ? "建立正文索引" : "保存";
                        runOnUiThread(() -> { if (valid(token)) { notice = label + " · " + size(bytes) + "　F2 取消"; render(); } });
                    }
                });
                runOnUiThread(() -> {
                    if (!valid(token)) return;
                    while (screens.size() > 1) screens.remove(screens.size() - 1);
                    context++; notice = "导入完成 · " + book.charset; openBook(book);
                });
            } catch (Exception error) { fail(token, "导入失败：请检查文本编码、权限和可用空间；原文件未改动", error); }
        });
    }
    private void openBook(BookRepository.Book book) {
        savePosition(); cancelWork();
        while (screens.size() > 1) screens.remove(screens.size() - 1);
        screens.add(new Screen(Kind.READER, book.title)); context++;
        session = null; page = null; pendingTurns = 0; requestedSpec = null; anchor = book.offset;
        notice = "打开图书…"; render();
        final long token = ++generation;
        worker.submit(() -> {
            try {
                BookRepository.Book latest = repository.get(book.id); if (latest == null) throw new IOException("Book not found");
                TextImporter.Result index = TextImporter.readIndex(repository.directory(book.id));
                Session opened = new Session(latest, index, repository.directory(book.id));
                runOnUiThread(() -> {
                    if (!valid(token)) return; session = opened;
                    anchor = boundOffset(livePositions.containsKey(book.id) ? livePositions.get(book.id) : latest.offset, index.length);
                    preferences.edit().putString("lastBook", book.id).putBoolean("reopen", true).apply();
                    notice = index.length == 0 ? "这是空文本，可按 F4 返回书架" : "";
                    render(); requestLayoutIfReady();
                });
            } catch (Exception error) { fail(token, "图书缓存不可读取，可返回书架；原始导入文件仍保留", error); }
        });
        render();
    }
    private void requestLayoutIfReady() {
        if (session == null || pageView == null || pageView.getWidth() < 1 || pageView.getHeight() < 1) return;
        TxtPaginator.Spec spec = new TxtPaginator.Spec(pageView.getWidth(), pageView.getHeight(),
                fontSize * getResources().getDisplayMetrics().scaledDensity, dp(4), Palette.forTheme(theme).foreground);
        if (spec.equals(requestedSpec)) { if (page != null && spec.equals(page.spec)) pageView.show(page); return; }
        int queued = pendingTurns;
        requestedSpec = spec; seek(anchor); pendingTurns = queued;
    }
    private void seek(long offset) {
        if (session == null || requestedSpec == null) return;
        final Session target = session; final TxtPaginator.Spec spec = requestedSpec;
        final long token = ++generation; anchor = boundOffset(offset, target.book.length); pendingTurns = 0; layoutBusy = true;
        final long position = anchor;
        worker.submit(() -> {
            try { TxtPaginator.Page result = target.paginator.seek(position, spec); publishPage(token, target, result, false); }
            catch (Exception error) { fail(token, "正文排版失败，请返回书架后重试", error); }
        });
    }
    private void turn(int direction) {
        if (session == null) return;
        pendingTurns = bound(pendingTurns + direction, -32, 32); pumpTurns();
    }
    private void pumpTurns() {
        if (layoutBusy || pendingTurns == 0 || session == null || page == null || !page.spec.equals(requestedSpec)) return;
        int direction = pendingTurns > 0 ? 1 : -1; pendingTurns -= direction; layoutBusy = true;
        final Session target = session; final TxtPaginator.Page old = page; final long token = ++generation;
        worker.submit(() -> {
            try { TxtPaginator.Page result = target.paginator.move(old, direction); publishPage(token, target, result, result.start == old.start); }
            catch (Exception error) { fail(token, "翻页失败，请返回书架后重试", error); }
        });
    }
    private void publishPage(long token, Session target, TxtPaginator.Page result, boolean boundary) {
        runOnUiThread(() -> {
            if (!valid(token) || session != target) return;
            page = result; anchor = result.start; layoutBusy = false;
            notice = boundary ? result.start == 0 ? "已经是第一页" : "已经是最后一页" : target.book.length == 0 ? "这是空文本" : "";
            savePosition(); render(); pumpTurns();
        });
    }
    private void contents() {
        if (session == null) return;
        Screen screen = new Screen(Kind.CONTENTS, "目录");
        for (TextImporter.Chapter chapter : session.index.chapters) {
            screen.rows.add(new Row("chapter", chapter.title, chapter)); if (chapter.offset <= anchor) screen.selected = screen.rows.size() - 1;
        }
        push(screen);
    }
    private void chapter(int direction) {
        if (session == null) return;
        List<TextImporter.Chapter> chapters = session.index.chapters; int current = 0;
        for (int i = 0; i < chapters.size(); i++) if (chapters.get(i).offset <= anchor) current = i;
        seek(chapters.get(bound(current + direction, 0, chapters.size() - 1)).offset);
    }
    private void bookmarks() {
        if (session == null) return;
        Screen screen = new Screen(Kind.BOOKMARKS, "书签"); screen.rows.add(new Row("bookmark-add", "添加当前位置书签"));
        screen.rows.add(new Row("back", "返回")); push(screen);
        final Session target = session;
        // Small metadata reads use a separate serial writer queue only through the library worker.
        if (layoutBusy) { notice = "当前页仍在加载，稍后重新打开书签"; return; }
        final long token = ++generation;
        worker.submit(() -> {
            try {
                List<TextImporter.Chapter> bookmarks = repository.bookmarks(target.book.id);
                runOnUiThread(() -> {
                    if (!valid(token) || !screens.contains(screen)) return;
                    screen.rows.remove(screen.rows.size() - 1);
                    for (TextImporter.Chapter bookmark : bookmarks) screen.rows.add(new Row("chapter", bookmark.title, bookmark));
                    screen.rows.add(new Row("back", "返回")); invalidateLists(); render();
                });
            } catch (Exception error) { fail(token, "书签读取失败", error); }
        });
    }
    private void addBookmark() {
        if (session == null || page == null) return;
        if (layoutBusy) { notice = "当前页仍在加载，请稍后添加书签"; return; }
        final String id = session.book.id; final long position = anchor;
        String snippet = page.text().replace('\n', ' ').trim();
        final String label = snippet.substring(0, Math.min(24, snippet.length())) + " · " + percent(position, session.book.length);
        closeOverlays(); final long token = ++generation;
        worker.submit(() -> {
            try {
                repository.addBookmark(id, position, label);
                runOnUiThread(() -> { if (valid(token)) { notice = "已添加书签"; render(); } });
            } catch (Exception error) { fail(token, "书签保存失败", error); }
        });
    }
    private void cancelWork() { generation++; worker.cancel(); layoutBusy = false; pendingTurns = 0; }
    private boolean valid(long token) { return !destroyed && generation == token; }
    private void fail(long token, String message, Exception error) {
        if (error instanceof InterruptedIOException || Thread.currentThread().isInterrupted()) return;
        Log.e("SH02LReader", message, error);
        runOnUiThread(() -> {
            if (!valid(token)) return; layoutBusy = false; loadingLibrary = false; notice = message;
            if (pageView != null && page == null) pageView.message("暂时无法显示正文，F4 返回"); render();
        });
    }
    private void savePosition() {
        if (session != null && page != null) {
            livePositions.put(session.book.id, anchor); progressWriter.save(session.book.id, anchor);
        }
    }

    private void render() {
        if (destroyed) return;
        Screen content = base();Palette palette=Palette.forTheme(content.kind==Kind.READER?theme:uiTheme);
        background=palette.background;foreground=palette.foreground;secondary=palette.secondary;surface=palette.surface;
        String style = content.kind==Kind.READER ? "reader:"+theme+":"+fontSize+":"+infoVisible : "ui:"+uiTheme+":"+bookStyle;
        if(builtOverlayTheme!=uiTheme){builtOverlay=null;builtOverlayTheme=uiTheme;}
        if (builtBase != content || !style.equals(builtStyle)) {
            builtBase = content; builtStyle = style; builtOverlay = null;
            root.removeAllViews(); root.setBackgroundColor(background);
            pageView = null; baseList = null; overlayList = null; diagnosticView = infoView = noticeView = null;
            root.addView(buildContent(content), new FrameLayout.LayoutParams(-1, -1));
            overlayHost = new FrameLayout(this); root.addView(overlayHost, new FrameLayout.LayoutParams(-1, -1));
        }
        if (top().kind.overlay()) {
            if (builtOverlay != top()) { overlayHost.removeAllViews(); buildOverlay(top()); builtOverlay = top(); }
        } else { overlayHost.removeAllViews(); builtOverlay = null; overlayList = null; }
        ListView active = top().kind.overlay() ? overlayList : baseList;
        if(baseList!=null)((BaseAdapter)baseList.getAdapter()).notifyDataSetChanged();
        if (baseList != null) baseList.setFocusable(!top().kind.overlay());
        if (pageView != null) pageView.setFocusable(!top().kind.overlay() && top().kind == Kind.READER);
        if (active != null) {
            ((BaseAdapter) active.getAdapter()).notifyDataSetChanged();
            if (active.getSelectedItemPosition() != top().selected) active.setSelection(top().selected);
            if (!active.hasFocus()) active.requestFocus();
        } else if (top().kind == Kind.READER && pageView != null && !pageView.hasFocus()) pageView.requestFocus();
        if (noticeView != null) noticeView.setText(notice);
        if (infoView != null) infoView.setText(session == null ? "" : percent(anchor, session.book.length)
                + " · " + session.book.charset + " · " + fontSize + "号");
        if (diagnosticView != null) diagnosticView.setText("累计事件：" + diagnostics.eventCount() + "\n" + diagnostics.tail(2));
        if (pageView != null && page != null && page.spec.equals(requestedSpec)) pageView.show(page);
        windowPolicy();
        if (top().kind == Kind.READER) requestLayoutIfReady();
    }
    private View buildContent(Screen screen) {
        LinearLayout column = vertical(); column.setPadding(dp(12), dp(10), dp(12), dp(6));
        TextView title = new UiStyle(this,Palette.forTheme(screen.kind==Kind.READER?theme:uiTheme)).title(screen.title);
        title.setTag("screen-title");if(screen.kind==Kind.READER)title.setTextSize(12);column.addView(title);
        if (screen.kind == Kind.READER) {
            pageView = new PageView(this, (width, height) -> { if (!destroyed) requestLayoutIfReady(); }, secondary);
            column.addView(pageView, new LinearLayout.LayoutParams(-1, 0, 1));
            if (infoVisible) { infoView = text("", 11, secondary); infoView.setTag("reading-info"); column.addView(infoView); }
        } else {
            if (screen.kind == Kind.HELP) column.addView(paragraph("列表：↑↓选择，确认打开。F1 菜单，F2 返回当前层，F4 返回。\n正文：↑/←上一页，↓/→/确认下一页。\n1/3 上/下一章，2 目录，4/6 字号，5 主题，7/9 亮度，8 信息栏，0 帮助，＊设置，＃书签。"));
            else if (screen.kind == Kind.SETTINGS) column.addView(paragraph("上下选择，左右调整。返回保留正文位置。"));
            else if (screen.kind == Kind.SOURCES) column.addView(paragraph("导入后不再依赖原文件或内存卡。\n应用导入文件夹：\n" + getExternalFilesDir("import")));
            else if (screen.kind == Kind.IMPORT) column.addView(paragraph("正在后台处理文本，F2 可取消。\n完成后自动打开，重复文件保留原进度。"));
            else if (screen.kind == Kind.DIAGNOSTICS) {
                column.addView(paragraph(Build.MODEL + " · Android " + Build.VERSION.RELEASE + "\n" + diagnosticStore.file()));
                diagnosticView = text("", 11, foreground); diagnosticView.setTypeface(Typeface.MONOSPACE); column.addView(diagnosticView);
            }
            if (screen.kind == Kind.LIBRARY && loadingLibrary) column.addView(paragraph("正在读取书架…"));
            baseList = list(screen); column.addView(baseList, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        noticeView = text(notice, 11, secondary); noticeView.setTag("notice"); noticeView.setMaxLines(3);
        if (screen.kind == Kind.READER) { noticeView.setLines(1); noticeView.setEllipsize(android.text.TextUtils.TruncateAt.END); }
        noticeView.setPadding(0, dp(4), 0, dp(3)); column.addView(noticeView);
        SoftKeyBar footer=new SoftKeyBar(this);Palette colors=Palette.forTheme(screen.kind==Kind.READER?theme:uiTheme);
        footer.setHints(screen.kind==Kind.READER?"F1 菜单":"↑↓ 选择",screen.kind==Kind.READER?"0 帮助":"确认 打开",screen.kind==Kind.LIBRARY?"F3 导入":screen.kind==Kind.READER?"F4 书架":"F2 返回",colors);
        column.addView(footer);return column;
    }
    private void buildOverlay(Screen screen) {
        Palette palette=Palette.forTheme(uiTheme);UiStyle style=new UiStyle(this,palette);
        FrameLayout shade = new FrameLayout(this); shade.setTag("overlay"); shade.setBackgroundColor(0x99000000); shade.setClickable(true);
        LinearLayout panel = vertical(); panel.setPadding(dp(12), dp(12), dp(12), dp(10)); panel.setBackground(round(palette.surface));
        TextView title=style.title(screen.title);panel.addView(title);
        if(screen.kind==Kind.HELP)panel.addView(text("↑↓ 选择，确认打开。F1 菜单，F2/F4 返回。正文：方向键翻页，0 帮助。",12,palette.secondary));
        overlayList = list(screen); panel.addView(overlayList, new LinearLayout.LayoutParams(-1, 0, 1));
        SoftKeyBar footer=new SoftKeyBar(this);footer.setHints("↑↓ 选择","确认 执行","F2 返回",palette);panel.addView(footer);
        int height = Math.min((root.getHeight()>0?root.getHeight():getResources().getDisplayMetrics().heightPixels) - dp(40), Math.min(8,screen.rows.size())*(style.rowHeight(64)+dp(6))+dp(100));
        FrameLayout.LayoutParams parameters = new FrameLayout.LayoutParams(-1, Math.max(dp(180), height), Gravity.CENTER);
        parameters.setMargins(dp(16), dp(20), dp(16), dp(20)); shade.addView(panel, parameters);
        overlayHost.addView(shade, new FrameLayout.LayoutParams(-1, -1));
    }
    private ListView list(Screen screen) {
        UiStyle style=new UiStyle(this,Palette.forTheme(uiTheme));
        ListView list = new ListView(this); list.setTag("list:" + screen.kind.name());
        list.setDivider(null); list.setDividerHeight(dp(6)); list.setItemsCanFocus(false);
        list.setFocusable(true); list.setFocusableInTouchMode(true); list.setDefaultFocusHighlightEnabled(false);
        list.setSelector(android.R.color.transparent); list.setCacheColorHint(Color.TRANSPARENT);
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return screen.rows.size(); }
            public Row getItem(int position) { return screen.rows.get(position); }
            public long getItemId(int position) { return position; }
            public View getView(int position, View convert, ViewGroup parent) {
                TextView row=convert instanceof UiStyle.Label?(TextView)convert:style.actionRow("",false);
                Row item=getItem(position);row.setTag("item:"+item.id);style.bind(row,item.label,position==screen.selected,top()==screen&&resumed&&hasWindowFocus());
                row.setFocusable(false); return row;
            }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (top() != screen) return; screen.selected = position; activate(); render();
        });
        list.setSelection(screen.selected); return list;
    }
    private void invalidateLists() { builtBase = null; builtOverlay = null; }
    private String selectedId(Screen screen) {
        if (screen.rows.isEmpty()) return ""; Row row = screen.rows.get(bound(screen.selected, 0, screen.rows.size() - 1));
        return row.value instanceof BookRepository.Book ? ((BookRepository.Book) row.value).id : row.id;
    }
    private void restoreSelection(Screen screen, String id) {
        screen.selected = 0;
        for (int i = 0; i < screen.rows.size(); i++) {
            Row row = screen.rows.get(i); String value = row.value instanceof BookRepository.Book ? ((BookRepository.Book) row.value).id : row.id;
            if (value.equals(id)) { screen.selected = i; break; }
        }
    }
    private void windowPolicy() {
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (getWindow().getDecorView().getSystemUiVisibility() != flags) getWindow().getDecorView().setSystemUiVisibility(flags);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        if (attributes.screenBrightness != brightness / 10f) { attributes.screenBrightness = brightness / 10f; getWindow().setAttributes(attributes); }
    }
    private String snapshot() {
        return "SH02L Reader " + BuildConfig.VERSION_NAME + "\nmodel=" + Build.MODEL + " api=" + Build.VERSION.SDK_INT
                + "\nscreen=" + top().kind + "\nbook=" + (session == null ? "" : session.book.id)
                + "\nlocator=txt-v1:" + anchor + "\ncommitted=" + committedBook + ":" + committedOffset + "\n" + diagnostics.tail(256);
    }
    private static int bound(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static long boundOffset(long value, long length) { return Math.max(0, Math.min(Math.max(0, length - 1), value)); }
    private static String size(long bytes) { return String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0); }
    private static String percent(long position, long length) { return String.format(Locale.ROOT, "%.1f%%", length == 0 ? 0 : 100.0 * position / length); }
    private LinearLayout vertical() { LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); return column; }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private TextView paragraph(String value) { TextView view = text(value, 13, secondary); view.setPadding(0, 0, 0, dp(10)); return view; }
    private GradientDrawable round(int color) { GradientDrawable result = new GradientDrawable(); result.setColor(color); result.setCornerRadius(dp(12)); return result; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    // Immutable observations used by instrumentation; no test-only behavior in production navigation.
    long readingOffset() { return page == null ? -1 : anchor; }
    long readingEnd() { return page == null ? -1 : page.end; }
    String readingText() { return page == null ? "" : page.text(); }
    boolean positionCommitted() { return session != null && committedBook.equals(session.book.id) && committedOffset == anchor; }
    boolean readingReady() { return session != null && page != null && !layoutBusy && page.spec.equals(requestedSpec); }
    String screenKind() { return top().kind.name(); }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused); if (router == null) return;
        if (!focused) {router.reset();UiStyle.pauseMarquees(root);} else { windowPolicy(); render(); }
        diagnostics.append("window-focus=" + focused);
    }
    @Override protected void onPause() {
        resumed=false;UiStyle.pauseMarquees(root);super.onPause(); router.reset(); savePosition(); saveSettings();
        diagnosticStore.save(snapshot(), null);
    }
    @Override protected void onResume() {super.onResume();resumed=true;if(root!=null)render();}
    @Override protected void onDestroy() {
        destroyed = true; generation++; worker.close(); progressWriter.close(); diagnosticStore.close();
        // The application-scoped database remains available while outstanding durable writes finish.
        super.onDestroy();
    }
}

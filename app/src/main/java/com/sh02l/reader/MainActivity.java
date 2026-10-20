package com.sh02l.reader;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.sh02l.reader.ImportExportController.ExportRequest;
import com.sh02l.reader.ImportExportController.ImportKind;
import com.sh02l.reader.NavigationState.*;
import com.sh02l.reader.android.ReadingStyle;
import com.sh02l.reader.core.*;
import com.sh02l.reader.core.importing.*;
import com.sh02l.reader.core.text.TextImporter;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** Window lifecycle and assembly; format rendering and durable state have separate owners. */
public final class MainActivity extends Activity implements KeyRouter.Target, FeatureHost {
    private static final class ImportRequest {
        final ImportKind kind;
        final String targetBookId;

        ImportRequest(ImportKind kind, String targetBookId) {
            this.kind = kind;
            this.targetBookId = targetBookId;
        }
    }

    private final NavigationState navigation = new NavigationState();
    private final List<Screen> screens = navigation.screens;
    private final LatestWorker worker = new LatestWorker();
    private final DiagnosticBuffer diagnostics = new DiagnosticBuffer(256);
    private KeyRouter router;
    private DeviceKeyMap deviceKeys;
    private ReadingController readingController;
    private BookRepository repository;
    private StorageManager storageManager;
    private ImportExportController transfers;
    private LibraryFeatures libraryFeatures;
    private ReaderFeatures readerFeatures;
    private ReadingSettings readingSettings;
    private ReaderInputView inputView;
    private Screen inputScreen;
    private String inputInitial;
    private boolean inputNumeric;
    private FeatureHost.InputSubmit inputSubmit;
    private ProgressWriter progressWriter;
    private DiagnosticStore diagnosticStore;
    private SharedPreferences preferences;
    private AppearanceStore appearanceStore;
    private int uiTheme, bookStyle;
    private FrameLayout root, overlayHost;
    private Screen builtBase, builtOverlay;
    private NavigationListView baseList, overlayList;
    private TextView noticeView, infoView, diagnosticView;
    private View readingView;
    private KeyHelpView helpView;
    private SoftKeyBar baseFooter, overlayFooter;
    private long generation;
    private DocumentLocation committedLocation;
    private String committedBook = "", notice = "F2 导入图书，确认打开；F3 通用菜单";
    private String libraryReturnId = "";
    private String pausedBookId = "";
    private String builtStyle = "";
    private int builtOverlayTheme = -1;
    private boolean resumed;
    private int fontSize, theme, brightness, foreground, secondary, background, surface;
    private boolean infoVisible, loadingLibrary, destroyed;
    private ReaderSession session;
    private Uri pendingUri;
    private ImportRequest importRequest, pickerRequest, modeRequest, storagePermissionRequest;
    private AndroidImportSource selectedImportSource;
    private File selectedImportFile;
    private ExportRequest pendingExport;
    private String importCompletionNotice = "";
    private final Map<String, ReadingPosition> livePositions = new HashMap<>();

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        preferences = getSharedPreferences("reader", MODE_PRIVATE);
        // Carry the real device's M0 appearance forward, but never treat sample page numbers as
        // book locators.
        LegacyAppearance prototype =
                LegacyAppearance.restore(
                        getSharedPreferences("prototype", MODE_PRIVATE).getString("state", null));
        fontSize = bound(preferences.getInt("font", prototype.fontSize), 14, 32);
        appearanceStore = new AppearanceStore(this);
        AppearanceState appearance =
                appearanceStore.load(
                        AppearanceStore.integer(preferences, "theme", prototype.theme));
        theme = appearance.readerTheme;
        uiTheme = appearance.uiTheme;
        bookStyle = appearance.bookStyle;
        brightness = bound(preferences.getInt("brightness", prototype.brightness), 1, 10);
        infoVisible = preferences.getBoolean("info", prototype.infoVisible);
        repository = BookRepository.obtain(this);
        readingSettings = new ReadingSettings(this, preferences);
        storageManager = new StorageManager(this, repository);
        transfers = new ImportExportController(this, repository);
        libraryFeatures = new LibraryFeatures(this);
        readerFeatures = new ReaderFeatures(this);
        progressWriter =
                new ProgressWriter(
                        repository,
                        storageManager,
                        (id, position, error) ->
                                runOnUiThread(
                                        () -> {
                                            if (destroyed) return;
                                            if (error == null) {
                                                committedBook = id;
                                                committedLocation = position.location;
                                            } else {
                                                notice = "阅读位置保存失败，请检查可用空间";
                                                render();
                                            }
                                        }));
        File logDirectory = getExternalFilesDir("diagnostics");
        diagnosticStore =
                new DiagnosticStore(
                        logDirectory != null
                                ? logDirectory
                                : new File(getFilesDir(), "diagnostics"));
        deviceKeys =
                DeviceProfiles.select(
                        Build.MANUFACTURER,
                        Build.MODEL,
                        preferences.getString("device_profile", ""));
        router = new KeyRouter(this, deviceKeys);
        readingController =
                new ReadingController(
                        this,
                        repository,
                        new ReadingController.Listener() {
                            public void opened(ReaderSession opened) {
                                session = opened;
                                builtBase = null;
                                preferences
                                        .edit()
                                        .putString("lastBook", opened.book.id)
                                        .putBoolean("reopen", true)
                                        .apply();
                                notice = "";
                                render();
                            }

                            public void changed(ReaderSession changed) {
                                if (session != changed) return;
                                notice = importCompletionNotice;
                                savePosition();
                                render();
                            }

                            public void failed(Exception error) {
                                Log.e("SH02LReader", "Reading failed", error);
                                notice = "正文暂时无法显示，F4 返回书架；原件已保留";
                                render();
                            }
                        });
        root = new FrameLayout(this);
        root.setTag("app-root");
        setContentView(root);
        screens.add(new Screen(Kind.LIBRARY, "书架"));
        if (Intent.ACTION_VIEW.equals(getIntent().getAction())) pendingUri = getIntent().getData();
        diagnostics.append(
                "session-created sdk=" + Build.VERSION.SDK_INT + " model=" + Build.MODEL);
        render();
        loadLibrary(true);
    }

    private Screen top() {
        return navigation.top();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null)
            importUri(intent.getData());
    }

    private Screen base() {
        return navigation.base();
    }

    @Override
    public long contextVersion() {
        return navigation.contextVersion();
    }

    private BookRepository.Book selectedBook() {
        Screen content = base();
        if (content.kind != Kind.LIBRARY || content.rows.isEmpty()) return null;
        Object value = content.rows.get(bound(content.selected, 0, content.rows.size() - 1)).value;
        return value instanceof BookRepository.Book ? (BookRepository.Book) value : null;
    }

    private InputContext inputContext() {
        BookRepository.Book selected = selectedBook();
        return new InputContext(
                top().kind,
                base().kind,
                top().generalMenu,
                selected != null,
                session != null ? session.book.format : selected != null ? selected.format : "txt",
                session == null ? null : session.document.capabilities(),
                top().hasCover,
                readingController != null && readingController.canReturn());
    }

    @Override
    public Action resolve(Key key) {
        return InputBindings.resolve(inputContext(), key);
    }

    @Override
    public void perform(Action action) {
        if (action != Action.NONE) importCompletionNotice = "";
        switch (action) {
            case UP:
                if (top().kind == Kind.HELP && helpView != null) helpView.move(-1);
                else select(top().selected - 1);
                break;
            case DOWN:
                if (top().kind == Kind.HELP && helpView != null) helpView.move(1);
                else select(top().selected + 1);
                break;
            case LEFT:
                if (top().kind == Kind.SETTINGS) adjust(-1);
                break;
            case RIGHT:
                if (top().kind == Kind.SETTINGS) adjust(1);
                break;
            case ACTIVATE:
                if (top().kind == Kind.INPUT && inputView != null) inputView.submit();
                else if (top().kind == Kind.READER) turn(1);
                else activate();
                break;
            case BACK:
                back();
                break;
            case HOME:
                goLibrary();
                break;
            case IMPORT:
                sources();
                break;
            case MENU:
                menu(false);
                break;
            case GENERAL_MENU:
                menu(true);
                break;
            case SETTINGS:
                settings();
                break;
            case DIAGNOSTICS:
                push(new Screen(Kind.DIAGNOSTICS, "按键诊断"));
                break;
            case HELP:
                help();
                break;
            case CONTENTS:
                contents();
                break;
            case BOOKMARKS:
                bookmarks();
                break;
            case PREVIOUS_PAGE:
                turn(-1);
                break;
            case NEXT_PAGE:
                turn(1);
                break;
            case PREVIOUS_CHAPTER:
                chapter(-1);
                break;
            case NEXT_CHAPTER:
                chapter(1);
                break;
            case SMALLER:
                fontSize = bound(fontSize - 1, 14, 32);
                appearanceChanged();
                break;
            case LARGER:
                fontSize = bound(fontSize + 1, 14, 32);
                appearanceChanged();
                break;
            case THEME:
                theme = (theme + 1) % 3;
                appearanceChanged();
                break;
            case DIMMER:
                brightness = bound(brightness - 1, 1, 10);
                saveSettings();
                break;
            case BRIGHTER:
                brightness = bound(brightness + 1, 1, 10);
                saveSettings();
                break;
            case INFO:
                infoVisible = !infoVisible;
                appearanceChanged();
                break;
            default:
                break;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (router == null) return super.dispatchKeyEvent(event);
        KeyInput input =
                new KeyInput(
                        event.getAction(),
                        event.getKeyCode(),
                        event.getScanCode(),
                        event.getRepeatCount(),
                        event.getDeviceId(),
                        event.getDownTime(),
                        event.getEventTime(),
                        event.isCanceled());
        if (top().kind == Kind.INPUT) {
            Key key = router.normalize(input);
            if (key != Key.F4 && key != Key.F3 && !router.owns(input))
                return super.dispatchKeyEvent(event);
        }
        KeyRouter.Result result = router.dispatch(input);
        diagnostics.record(input, result);
        Log.d("SH02LKeys", diagnostics.tail(1).trim());
        if (result.consumed) {
            if (!isFinishing()) render();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (top().kind == Kind.LIBRARY) menu(true);
        else back();
        render();
    }

    private void select(int selected) {
        Screen screen = top();
        int moved =
                ListSelection.move(
                        screen.selected,
                        selected - screen.selected,
                        screen.rows.size(),
                        i -> screen.rows.get(i).selectable());
        screen.selected = Math.max(0, moved);
    }

    public void push(Screen screen) {
        if ((top().kind == screen.kind && screen.kind != Kind.SETTINGS) || screens.size() >= 12)
            return;
        screens.add(screen);
        navigation.changed();
    }

    public void back() {
        if (screens.size() == 1) return;
        Screen removed = screens.remove(screens.size() - 1);
        navigation.changed();
        if (removed.busy) cancelWork();
        if (removed.busy && !pausedBookId.isEmpty()) {
            goLibrary();
            notice = "操作已取消，可重新打开图书";
            return;
        }
        if (removed.kind == Kind.INPUT) {
            if (inputView != null)
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(inputView.getWindowToken(), 0);
            inputView = null;
            inputScreen = null;
            inputSubmit = null;
        }
        if (removed.kind == Kind.SEARCH_RESULTS
                || removed.kind == Kind.STORAGE
                || removed.kind == Kind.IMPORT_CLEANUP) cancelWork();
        if (removed.kind == Kind.IMPORT_MODE) {
            selectedImportSource = null;
            selectedImportFile = null;
            modeRequest = null;
        }
        if (removed.kind == Kind.SOURCES) {
            importRequest = null;
            pickerRequest = null;
            storagePermissionRequest = null;
        }
        if (removed.kind == Kind.IMPORT
                || removed.kind == Kind.FILES
                || removed.kind == Kind.SOURCES) cancelWork();
        if (removed.kind == Kind.READER) {
            if (session != null) libraryReturnId = session.book.id;
            savePosition();
            cancelWork();
            readingController.close();
            session = null;
            preferences.edit().putBoolean("reopen", false).apply();
            loadLibrary(false);
        }
    }

    public void closeOverlays() {
        while (top().kind.overlay()) back();
    }

    private void goLibrary() {
        pausedBookId = "";
        pendingExport = null;
        importRequest = null;
        pickerRequest = null;
        modeRequest = null;
        storagePermissionRequest = null;
        selectedImportSource = null;
        selectedImportFile = null;
        if (session != null) libraryReturnId = session.book.id;
        savePosition();
        cancelWork();
        while (screens.size() > 1) screens.remove(screens.size() - 1);
        navigation.changed();
        readingController.close();
        session = null;
        preferences.edit().putBoolean("reopen", false).apply();
        loadLibrary(false);
    }

    private void menu(boolean general) {
        if (top().kind == Kind.MENU) {
            boolean same = top().generalMenu == general;
            back();
            if (same) return;
        }
        BookRepository.Book target = selectedBook();
        if (!general && base().kind == Kind.LIBRARY && target == null) {
            notice = "请选择一本图书后按 F1；F3 打开通用菜单";
            return;
        }
        if (!general && base().kind == Kind.READER && session == null) {
            notice = "图书正在打开，请稍候";
            return;
        }
        Screen screen =
                new Screen(
                        Kind.MENU,
                        general ? "通用菜单" : target != null ? target.title : "阅读菜单",
                        target == null ? "" : target.id);
        screen.generalMenu = general;
        populateMenu(screen, target);
        push(screen);
        if (!general && target != null) {
            final long token = ++generation;
            worker.submit(
                    () -> {
                        try {
                            boolean cover = new CoverStore(this).get(target.id) != null;
                            runOnUiThread(
                                    () -> {
                                        if (!valid(token)
                                                || !screens.contains(screen)
                                                || screen.hasCover == cover) return;
                                        String selected = selectedId(screen);
                                        screen.hasCover = cover;
                                        populateMenu(screen, target);
                                        restoreSelection(screen, selected);
                                        builtOverlay = null;
                                        render();
                                    });
                        } catch (IOException error) {
                            Log.w("SH02LReader", "Cover metadata unavailable", error);
                        }
                    });
        }
    }

    private void populateMenu(Screen screen, BookRepository.Book target) {
        Screen actions = new Screen(Kind.MENU, "");
        if (target != null) libraryFeatures.addBookActions(actions, target);
        if (session != null) readerFeatures.addMenu(actions);
        actions.rows.add(Row.action("storage", "", libraryFeatures::openStorage));
        actions.rows.add(
                Row.action(
                        "return-jump",
                        "",
                        () -> {
                            closeOverlays();
                            readingController.returnFromJump();
                        }));
        Map<String, Row> byId = new HashMap<>();
        for (Row row : actions.rows) byId.put(row.id, row);
        InputContext context =
                new InputContext(
                        Kind.MENU,
                        base().kind,
                        screen.generalMenu,
                        target != null,
                        target != null
                                ? target.format
                                : session != null ? session.book.format : "txt",
                        session == null ? null : session.document.capabilities(),
                        screen.hasCover,
                        readingController.canReturn());
        screen.rows.clear();
        for (UiAction item : MenuCatalog.items(context)) {
            Row action = byId.get(item.id);
            screen.rows.add(
                    action != null && action.action != null
                            ? Row.action(item.id, item.title, action.action)
                            : new Row(item.id, item.title, target == null ? null : target.id));
        }
        screen.selected = screen.rows.size() > 1 ? 1 : 0;
    }

    private void settings() {
        settingsGroup(0);
    }

    private void settingsGroup(int group) {
        Screen screen = new Screen(Kind.SETTINGS, group == 1 ? "界面外观" : group == 2 ? "正文阅读" : "设置");
        screen.group = group;
        refreshSettings(screen);
        push(screen);
    }

    private void refreshSettings(Screen screen) {
        if (screen.kind != Kind.SETTINGS) return;
        screen.rows.clear();
        String[] colors = {"米白", "白色", "暖炭灰"};
        if (screen.group == 0) {
            screen.rows.add(new Row("appearance", "界面外观"));
            screen.rows.add(new Row("reading-settings", "正文阅读"));
            screen.rows.add(new Row("help", "按键说明"));
        } else if (screen.group == 1) {
            screen.rows.add(new Row("ui-theme", "界面主题：" + colors[uiTheme]));
            screen.rows.add(
                    new Row(
                            "shelf-style",
                            "书架样式：" + new String[] {"纯文字", "小书标", "独立封面"}[bookStyle]));
        } else {
            screen.rows.add(new Row("theme", "阅读配色：" + colors[theme]));
            screen.rows.add(new Row("font", "字号：" + fontSize));
            screen.rows.add(new Row("brightness", "窗口亮度：" + brightness * 10 + "%"));
            screen.rows.add(new Row("info", "阅读信息栏：" + (infoVisible ? "显示" : "隐藏")));
            readerFeatures.addSettings(screen);
        }
    }

    private void adjust(int direction) {
        if (top().rows.isEmpty()) return;
        Row setting = top().rows.get(top().selected);
        if (setting.adjustment != null) {
            setting.adjustment.accept(direction);
            refreshSettings(top());
            return;
        }
        switch (top().rows.get(top().selected).id) {
            case "ui-theme":
                uiTheme = (uiTheme + direction + 3) % 3;
                saveSettings();
                refreshSettings(top());
                return;
            case "shelf-style":
                bookStyle = (bookStyle + direction + 3) % 3;
                saveSettings();
                refreshSettings(top());
                return;
            case "font":
                fontSize = bound(fontSize + direction, 14, 32);
                break;
            case "theme":
                theme = (theme + direction + 3) % 3;
                break;
            case "brightness":
                brightness = bound(brightness + direction, 1, 10);
                break;
            case "info":
                infoVisible = !infoVisible;
                break;
            default:
                return;
        }
        appearanceChanged();
        refreshSettings(top());
    }

    private void appearanceChanged() {
        saveSettings();
        requestLayoutIfReady();
    }

    private void saveSettings() {
        appearanceStore.save(new AppearanceState(uiTheme, theme, bookStyle));
        preferences
                .edit()
                .putInt("font", fontSize)
                .putInt("theme", theme)
                .putInt("brightness", brightness)
                .putBoolean("info", infoVisible)
                .apply();
    }

    private void help() {
        if (top().kind == Kind.HELP) {
            back();
            return;
        }
        Screen screen = new Screen(Kind.HELP, "按键说明");
        screen.helpReading = top().kind == Kind.READER;
        InputContext context = inputContext();
        screen.helpFunctions =
                new String[] {
                    InputBindings.label(resolve(Key.F1), context),
                    InputBindings.label(resolve(Key.F2), context),
                    InputBindings.label(resolve(Key.F3), context),
                    InputBindings.label(resolve(Key.F4), context)
                };
        push(screen);
    }

    private static String helpAction(Action action) {
        switch (action) {
            case MENU:
                return "书籍菜单";
            case GENERAL_MENU:
                return "通用菜单";
            case IMPORT:
                return "导入";
            case BACK:
                return "返回";
            default:
                return "无操作";
        }
    }

    private void activate() {
        if (top().rows.isEmpty()) return;
        Row row = top().rows.get(bound(top().selected, 0, top().rows.size() - 1));
        if (!row.selectable()) return;
        if (row.action != null) {
            row.action.run();
            return;
        }
        switch (row.id) {
            case "import":
                sources();
                break;
            case "book":
                openBook((BookRepository.Book) row.value);
                break;
            case "directory":
                files((File) row.value);
                break;
            case "file":
                offerSource(
                        AndroidImportSource.fromFile((File) row.value),
                        (File) row.value,
                        importRequest);
                break;
            case "cover":
                sources(new ImportRequest(ImportKind.COVER, (String) row.value));
                break;
            case "copy-import":
                {
                    AndroidImportSource source = selectedImportSource;
                    File file = selectedImportFile;
                    ImportRequest request = modeRequest;
                    if (source != null && request != null) {
                        if (file != null && request.kind == ImportKind.BOOK) importFile(file);
                        else executeImport(source, ImportCoordinator.Mode.COPY, request);
                    }
                    break;
                }
            case "move-import":
                if (selectedImportSource != null && modeRequest != null)
                    executeImport(selectedImportSource, ImportCoordinator.Mode.MOVE, modeRequest);
                break;
            case "export-text":
                exportOriginal((String) row.value, false);
                break;
            case "export-cover":
                exportOriginal((String) row.value, true);
                break;
            case "shared":
                requestSharedStorage();
                break;
            case "system-picker":
                systemPicker();
                break;
            case "settings":
                settings();
                break;
            case "appearance":
                settingsGroup(1);
                break;
            case "reading-settings":
                settingsGroup(2);
                break;
            case "diagnostics":
                {
                    Screen screen = new Screen(Kind.DIAGNOSTICS, "按键诊断");
                    screen.rows.add(new Row("save-log", "保存诊断记录"));
                    screen.rows.add(new Row("back", "返回"));
                    push(screen);
                    break;
                }
            case "help":
                help();
                break;
            case "contents":
                contents();
                break;
            case "bookmarks":
                bookmarks();
                break;
            case "chapter":
                closeOverlays();
                seekTo(((ContentEntry) row.value).location);
                break;
            case "bookmark-add":
                addBookmark();
                break;
            case "font":
            case "theme":
            case "brightness":
            case "info":
            case "ui-theme":
            case "shelf-style":
                adjust(1);
                break;
            case "library":
                goLibrary();
                break;
            case "cancel-import":
                closeOverlays();
                back();
                break;
            case "back":
                back();
                break;
            case "exit":
                {
                    Screen screen = new Screen(Kind.EXIT, "退出应用？");
                    screen.rows.add(new Row("back", "继续使用"));
                    screen.rows.add(new Row("exit-now", "退出应用"));
                    push(screen);
                    break;
                }
            case "exit-now":
                savePosition();
                finish();
                break;
            case "save-log":
                diagnosticStore.save(
                        snapshot(),
                        (file, error) ->
                                runOnUiThread(
                                        () -> {
                                            if (destroyed) return;
                                            notice =
                                                    error == null
                                                            ? "已保存：" + file.getName()
                                                            : "保存失败，请检查空间";
                                            render();
                                        }));
                break;
            default:
                break;
        }
    }

    private void loadLibrary(boolean initial) {
        loadingLibrary = true;
        final long token = ++generation;
        worker.submit(
                () -> {
                    try {
                        if (initial) {
                            repository.cleanupStaging();
                            new FontStore(this).cleanupStaging();
                            try {
                                storageManager.prepareReserve();
                            } catch (IOException error) {
                                Log.w("SH02LReader", "Emergency reserve unavailable", error);
                            }
                            try {
                                new CoverStore(this).cleanupInterruptedImports();
                            } catch (InterruptedIOException error) {
                                throw error;
                            } catch (IOException error) {
                                Log.w(
                                        "SH02LReader",
                                        "Cover cleanup will retry on next startup",
                                        error);
                            }
                        }
                        Screen shelf = screens.get(0);
                        if (!libraryReturnId.isEmpty())
                            shelf.page = repository.pageOf(libraryReturnId, 50);
                        int total = repository.count();
                        shelf.page = Math.min(shelf.page, Math.max(0, (total - 1) / 50));
                        List<BookRepository.Book> books = repository.listPage(shelf.page * 50, 50);
                        BookRepository.Book recent =
                                repository.get(preferences.getString("lastBook", ""));
                        runOnUiThread(
                                () -> {
                                    if (!valid(token)) return;
                                    loadingLibrary = false;
                                    Screen library = screens.get(0);
                                    String selectedId =
                                            libraryReturnId.isEmpty()
                                                    ? selectedId(library)
                                                    : libraryReturnId;
                                    libraryReturnId = "";
                                    library.rows.clear();
                                    BookRepository.Book last = recent;
                                    if (last != null)
                                        library.rows.add(new Row("book", last.title, last));
                                    library.rows.add(new Row("import", "＋ 导入 TXT / EPUB"));
                                    for (BookRepository.Book book : books)
                                        if (last == null || !last.id.equals(book.id))
                                            library.rows.add(new Row("book", book.title, book));
                                    if (library.page > 0)
                                        library.rows.add(
                                                Row.action(
                                                        "previous-batch",
                                                        "← 上一批图书",
                                                        () -> libraryPage(-1)));
                                    if ((library.page + 1) * 50 < total)
                                        library.rows.add(
                                                Row.action(
                                                        "next-batch",
                                                        "下一批图书 →",
                                                        () -> libraryPage(1)));
                                    library.title =
                                            "书架 · "
                                                    + total
                                                    + " 本"
                                                    + (total > 50
                                                            ? " · "
                                                                    + (library.page + 1)
                                                                    + "/"
                                                                    + ((total + 49) / 50)
                                                            : "");
                                    restoreSelection(library, selectedId);
                                    invalidateLists();
                                    render();
                                    if (pendingUri != null) {
                                        Uri uri = pendingUri;
                                        pendingUri = null;
                                        importUri(uri);
                                    } else if (initial
                                            && top().kind == Kind.LIBRARY
                                            && preferences.getBoolean("reopen", false)
                                            && last != null) openBook(last);
                                });
                    } catch (Exception error) {
                        fail(token, "书架读取失败", error);
                    }
                });
    }

    private void sources() {
        sources(new ImportRequest(ImportKind.BOOK, ""));
    }

    private void sources(ImportRequest request) {
        if (base().kind == Kind.IMPORT) {
            notice = "请先完成或取消当前导入";
            return;
        }
        pendingExport = null;
        closeOverlays();
        importRequest = request;
        Screen screen =
                new Screen(
                        Kind.SOURCES,
                        request.kind == ImportKind.COVER
                                ? "导入封面"
                                : request.kind == ImportKind.FONT ? "导入字体" : "导入图书");
        File importDirectory = getExternalFilesDir("import");
        if (importDirectory != null) {
            importDirectory.mkdirs();
            screen.rows.add(new Row("directory", "应用导入文件夹", importDirectory));
        }
        screen.rows.add(new Row("shared", "手机存储 / 内存卡"));
        screen.rows.add(new Row("system-picker", "系统文件选择"));
        screen.rows.add(new Row("back", "返回"));
        push(screen);
        notice = "选择文件后可复制或移动导入；私有原件可从图书菜单导出";
    }

    private void requestSharedStorage() {
        List<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED)
            missing.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (!missing.isEmpty()) {
            storagePermissionRequest = importRequest;
            requestPermissions(missing.toArray(new String[0]), 1);
            return;
        }
        sharedRoots();
    }

    private void sharedRoots() {
        Screen screen = new Screen(Kind.FILES, "手机存储 / 内存卡");
        Set<String> paths = new HashSet<>();
        File primary = Environment.getExternalStorageDirectory();
        screen.rows.add(new Row("directory", "手机存储", primary));
        paths.add(primary.getAbsolutePath());
        for (File appDirectory : getExternalFilesDirs(null)) {
            if (appDirectory == null) continue;
            File directory = appDirectory;
            for (int i = 0; i < 4 && directory != null; i++) directory = directory.getParentFile();
            if (directory != null && paths.add(directory.getAbsolutePath()))
                screen.rows.add(new Row("directory", "内存卡 · " + directory.getName(), directory));
        }
        screen.rows.add(new Row("back", "返回"));
        push(screen);
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 1 && !destroyed) {
            ImportRequest requested = storagePermissionRequest;
            storagePermissionRequest = null;
            if (requested == null || requested != importRequest || top().kind != Kind.SOURCES)
                return;
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                sharedRoots();
                if (Build.VERSION.SDK_INT <= 28
                        && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                != PackageManager.PERMISSION_GRANTED)
                    notice = "未获得写入权限，仍可复制导入；移动可使用系统文件选择授权。";
            } else notice = "未获得读取权限，可使用系统文件选择或应用导入文件夹";
            render();
        }
    }

    private void systemPicker() {
        try {
            pickerRequest = importRequest;
            if (pickerRequest == null) return;
            Intent intent =
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(
                                    pickerRequest.kind == ImportKind.COVER
                                            ? "image/*"
                                            : pickerRequest.kind == ImportKind.FONT ? "*/*" : "*/*")
                            .addFlags(
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, 2);
        } catch (ActivityNotFoundException error) {
            pickerRequest = null;
            notice = "设备没有系统文件选择器，请使用手机存储入口";
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 2) {
            ImportRequest selected = pickerRequest;
            pickerRequest = null;
            if (selected != null
                    && selected == importRequest
                    && result == RESULT_OK
                    && data != null
                    && data.getData() != null) {
                int grants =
                        data.getFlags()
                                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if ((data.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0)
                    try {
                        boolean read = (grants & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0,
                                write = (grants & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;
                        if (read && write)
                            getContentResolver()
                                    .takePersistableUriPermission(
                                            data.getData(),
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        else if (read)
                            getContentResolver()
                                    .takePersistableUriPermission(
                                            data.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        else if (write)
                            getContentResolver()
                                    .takePersistableUriPermission(
                                            data.getData(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } catch (SecurityException ignored) {
                    }
                offerSource(
                        AndroidImportSource.fromUri(getContentResolver(), data.getData(), grants),
                        null,
                        selected);
            } else if (selected != null && selected == importRequest) notice = "已取消文件选择";
            render();
        } else if (request == 3) {
            ExportRequest export = pendingExport;
            pendingExport = null;
            if (export == null) return;
            if (result == RESULT_OK && data != null && data.getData() != null)
                writeExport(export, data.getData(), data.getFlags());
            else {
                notice = "已取消导出";
                render();
            }
        }
    }

    private void files(File directory) {
        // Directories replace the file screen while parent frames retain their exact selection.
        Screen screen =
                new Screen(Kind.FILES, directory.getName().isEmpty() ? "文件" : directory.getName());
        screen.directory = directory;
        if (top().kind == Kind.FILES && top().directory == null) {
            screens.remove(screens.size() - 1);
            navigation.changed();
        }
        if (screens.size() >= 12) {
            notice = "目录层级过深，请用系统文件选择";
            return;
        }
        screens.add(screen);
        navigation.changed();
        notice = "读取目录…";
        final long token = ++generation;
        final boolean images = importRequest != null && importRequest.kind == ImportKind.COVER;
        final boolean fonts = importRequest != null && importRequest.kind == ImportKind.FONT;
        worker.submit(
                () -> {
                    try {
                        File[] entries =
                                directory.listFiles(
                                        file -> {
                                            String name = file.getName().toLowerCase(Locale.ROOT);
                                            return file.isDirectory()
                                                    || (images
                                                            ? (name.endsWith(".jpg")
                                                                    || name.endsWith(".jpeg")
                                                                    || name.endsWith(".png")
                                                                    || name.endsWith(".webp"))
                                                            : fonts
                                                                    ? (name.endsWith(".ttf")
                                                                            || name.endsWith(
                                                                                    ".otf"))
                                                                    : DocumentImportRegistry
                                                                            .bookName(name));
                                        });
                        if (entries == null) throw new IOException("Cannot list directory");
                        if (entries.length > 10000)
                            throw new IOException("Too many files; select a narrower folder");
                        Arrays.sort(
                                entries,
                                (left, right) -> {
                                    int kind =
                                            Boolean.compare(
                                                    right.isDirectory(), left.isDirectory());
                                    return kind != 0
                                            ? kind
                                            : left.getName().compareToIgnoreCase(right.getName());
                                });
                        List<Row> rows = new ArrayList<>();
                        rows.add(new Row("back", "↩ 上一层"));
                        for (File file : entries)
                            rows.add(
                                    new Row(
                                            file.isDirectory() ? "directory" : "file",
                                            (file.isDirectory() ? "▸ " : "") + file.getName(),
                                            file));
                        runOnUiThread(
                                () -> {
                                    if (!valid(token) || !screens.contains(screen)) return;
                                    screen.rows.addAll(rows);
                                    notice = entries.length == 0 ? "此目录没有可导入文件" : "上下选择，确认打开";
                                    invalidateLists();
                                    render();
                                });
                    } catch (Exception error) {
                        fail(token, "目录不可读取，请返回并检查权限或内存卡", error);
                    }
                });
        render();
    }

    // The file-copy action and native fixtures share the exact same production import path.
    void importFile(File file) {
        ImportRequest request = new ImportRequest(ImportKind.BOOK, "");
        importRequest = request;
        executeImport(AndroidImportSource.fromFile(file), ImportCoordinator.Mode.COPY, request);
    }

    private void importUri(Uri uri) {
        if (!"content".equals(uri.getScheme())) {
            notice = "仅支持通过文件提供程序打开图书";
            render();
            return;
        }
        pendingExport = null;
        ImportRequest request = new ImportRequest(ImportKind.BOOK, "");
        importRequest = request;
        offerSource(
                AndroidImportSource.fromUri(getContentResolver(), uri, getIntent().getFlags()),
                null,
                request);
    }

    private void offerSource(AndroidImportSource source, File file, ImportRequest request) {
        if (request == null) return;
        notice = "读取文件信息…";
        final long token = ++generation;
        worker.submit(
                () -> {
                    try {
                        source.snapshot();
                        boolean move = source.canDelete();
                        runOnUiThread(
                                () -> {
                                    if (!valid(token) || request != importRequest) return;
                                    selectedImportSource = source;
                                    selectedImportFile = file;
                                    modeRequest = request;
                                    Screen screen =
                                            new Screen(Kind.IMPORT_MODE, source.displayName());
                                    screen.rows.add(new Row("copy-import", "复制导入"));
                                    if (move) screen.rows.add(new Row("move-import", "移动导入"));
                                    screen.rows.add(new Row("back", "返回"));
                                    notice =
                                            (move ? "移动会在完整校验并提交后删除来源。" : "此来源只读或无法可靠核验，可使用复制导入。")
                                                    + "私有副本随卸载 / 清除数据移除，可从图书菜单导出。";
                                    push(screen);
                                    render();
                                });
                    } catch (Exception error) {
                        fail(token, "文件不可读取，请检查授权或重新选择", error);
                    }
                });
        render();
    }

    private void executeImport(
            AndroidImportSource source, ImportCoordinator.Mode mode, ImportRequest request) {
        if (base().kind == Kind.IMPORT) return;
        pendingExport = null;
        savePosition();
        cancelWork();
        closeOverlays();
        Screen screen =
                new Screen(
                        Kind.IMPORT,
                        request.kind == ImportKind.COVER
                                ? "导入封面"
                                : request.kind == ImportKind.FONT ? "导入字体" : "导入图书");
        screen.rows.add(new Row("back", "取消导入"));
        push(screen);
        notice = "准备导入：" + source.displayName();
        render();
        final long token = ++generation;
        worker.submit(
                () -> {
                    try {
                        final long[] last = {0};
                        TextImporter.Observer observer =
                                new TextImporter.Observer() {
                                    public boolean cancelled() {
                                        return Thread.currentThread().isInterrupted();
                                    }

                                    public void progress(String phase, long bytes) {
                                        long now = SystemClock.uptimeMillis();
                                        if (now - last[0] < 200) return;
                                        last[0] = now;
                                        String label =
                                                "copy".equals(phase)
                                                        ? "复制"
                                                        : "encoding".equals(phase)
                                                                ? "识别编码"
                                                                : "decode".equals(phase)
                                                                        ? "建立正文索引"
                                                                        : "保存";
                                        runOnUiThread(
                                                () -> {
                                                    if (valid(token)) {
                                                        notice =
                                                                label
                                                                        + " · "
                                                                        + size(bytes)
                                                                        + "　F4 取消";
                                                        render();
                                                    }
                                                });
                                    }
                                };
                        ImportCoordinator.Outcome outcome =
                                transfers.importAsset(
                                        source, mode, request.kind, request.targetBookId, observer);
                        BookRepository.Book book =
                                request.kind == ImportKind.BOOK
                                        ? repository.get(outcome.assetId)
                                        : null;
                        runOnUiThread(
                                () -> {
                                    if (!valid(token)) return;
                                    String result =
                                            outcome.status == ImportCoordinator.Status.MOVED
                                                    ? "移动完成"
                                                    : outcome.status
                                                                    == ImportCoordinator.Status
                                                                            .COPIED_SOURCE_RETAINED
                                                            ? "导入完成，原文件未删除"
                                                            : "导入完成";
                                    Screen help = top().kind == Kind.HELP ? top() : null;
                                    while (screens.size() > 1) screens.remove(screens.size() - 1);
                                    navigation.changed();
                                    importRequest = null;
                                    modeRequest = null;
                                    selectedImportSource = null;
                                    selectedImportFile = null;
                                    if (book != null) {
                                        openBook(book);
                                        importCompletionNotice = result;
                                        notice = result;
                                    } else if (request.kind == ImportKind.FONT) {
                                        readingSettings.invalidateFont();
                                        readingSettings.font = 4;
                                        readingSettings.save();
                                        goLibrary();
                                        notice = "字体导入完成，已设为正文字体";
                                    } else {
                                        libraryReturnId = request.targetBookId;
                                        goLibrary();
                                        notice = result;
                                    }
                                    if (help != null) push(help);
                                    Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG)
                                            .show();
                                    render();
                                });
                    } catch (Exception error) {
                        fail(token, "导入失败：" + operationError(error) + "；来源文件已保留", error);
                    }
                });
    }

    private void exportOriginal(String id, boolean cover) {
        Screen origin = top();
        final long token = ++generation;
        notice = "准备导出原件…";
        worker.submit(
                () -> {
                    try {
                        ExportRequest request = transfers.prepareExport(id, cover);
                        runOnUiThread(
                                () -> {
                                    if (!valid(token) || top() != origin) return;
                                    try {
                                        pendingExport = request;
                                        startActivityForResult(
                                                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                                        .setType(request.mime)
                                                        .putExtra(Intent.EXTRA_TITLE, request.name)
                                                        .addFlags(
                                                                Intent
                                                                                .FLAG_GRANT_WRITE_URI_PERMISSION
                                                                        | Intent
                                                                                .FLAG_GRANT_READ_URI_PERMISSION),
                                                3);
                                    } catch (ActivityNotFoundException error) {
                                        pendingExport = null;
                                        notice = "设备没有可用的系统导出入口";
                                        render();
                                    }
                                });
                    } catch (Exception error) {
                        fail(token, "原件不可读取，无法导出", error);
                    }
                });
    }

    private void writeExport(ExportRequest request, Uri destination, int grants) {
        if (!"content".equals(destination.getScheme())) {
            notice = "请选择系统文档目标";
            render();
            return;
        }
        final long token = ++generation;
        notice = "正在导出原件…";
        render();
        worker.submit(
                () -> {
                    try {
                        transfers.export(request, destination);
                        runOnUiThread(
                                () -> {
                                    if (!valid(token)) return;
                                    notice = "原件导出完成";
                                    if (top().kind == Kind.MENU) back();
                                    Toast.makeText(this, "原件导出完成", Toast.LENGTH_LONG).show();
                                    render();
                                });
                    } catch (Exception error) {
                        boolean removed = transfers.cleanupFailedExport(destination, grants);
                        runOnUiThread(
                                () -> {
                                    if (!valid(token)) return;
                                    notice = removed ? "导出失败，已清理不完整文件" : "导出失败，目标文件可能不完整";
                                    Toast.makeText(this, notice, Toast.LENGTH_LONG).show();
                                    render();
                                });
                    }
                });
    }

    private ReadingStyle readingStyle() {
        Palette palette = Palette.forTheme(theme);
        String family =
                readingSettings.font == 1
                        ? "serif"
                        : readingSettings.font == 3 ? "monospace" : "sans-serif";
        String fontPath =
                readingSettings.font == 4 && new FontStore(this).available()
                        ? new File(getFilesDir(), "font/custom.ttf").getAbsolutePath()
                        : null;
        return new ReadingStyle(
                fontSize * getResources().getDisplayMetrics().scaledDensity,
                dp(readingSettings.lineExtra),
                palette.foreground,
                palette.background,
                readingSettings.typeface(),
                family,
                fontPath);
    }

    private void openBook(BookRepository.Book book) {
        pausedBookId = "";
        savePosition();
        cancelWork();
        while (screens.size() > 1) screens.remove(screens.size() - 1);
        screens.add(new Screen(Kind.READER, book.title));
        navigation.changed();
        session = null;
        builtBase = null;
        notice = "打开图书…";
        readingController.open(book.id, livePositions.get(book.id), readingStyle());
        render();
    }

    private void requestLayoutIfReady() {
        if (readingController != null) readingController.apply(readingStyle());
    }

    private void seek(long offset) {
        seekTo(DocumentLocation.txt(offset));
    }

    private void turn(int direction) {
        readingController.move(direction);
    }

    private void contents() {
        if (session == null) return;
        Screen screen = new Screen(Kind.CONTENTS, "目录");
        screen.rows.add(new Row("back", "返回正文"));
        for (ContentEntry entry : session.document.contents()) {
            screen.rows.add(new Row("chapter", entry.title, entry));
            if (session.document.compare(entry.location, chapterReference()) < 0)
                screen.selected = screen.rows.size() - 1;
        }
        if (screen.rows.size() == 1) screen.rows.add(new Row("noop", "此书暂无目录"));
        push(screen);
    }

    private DocumentLocation chapterReference() {
        DocumentLocation end = readingController.end();
        return readingController.ready() && end != null ? end : session.position.location;
    }

    private void chapter(int direction) {
        if (session == null) return;
        List<ContentEntry> entries = session.document.contents();
        if (entries.isEmpty()) return;
        int current = 0;
        for (int i = 0; i < entries.size(); i++)
            if (session.document.compare(entries.get(i).location, chapterReference()) < 0)
                current = i;
        seekTo(entries.get(bound(current + direction, 0, entries.size() - 1)).location);
    }

    private void bookmarks() {
        if (session == null) return;
        Screen screen = new Screen(Kind.BOOKMARKS, "书签");
        screen.rows.add(new Row("back", "返回正文"));
        screen.rows.add(new Row("bookmark-add", "添加当前位置书签"));
        screen.selected = 1;
        push(screen);
        final ReaderSession target = session;
        final long token = ++generation;
        worker.submit(
                () -> {
                    try {
                        List<ContentEntry> marks = repository.bookmarkEntries(target.book.id);
                        runOnUiThread(
                                () -> {
                                    if (!valid(token) || !screens.contains(screen)) return;
                                    for (ContentEntry mark : marks)
                                        screen.rows.add(
                                                Row.action(
                                                        "bookmark-" + mark.location,
                                                        mark.title,
                                                        () ->
                                                                libraryFeatures.bookmark(
                                                                        target.book, mark)));
                                    builtOverlay = null;
                                    render();
                                });
                    } catch (Exception error) {
                        fail(token, "书签读取失败", error);
                    }
                });
    }

    private void addBookmark() {
        if (session == null || !readingController.ready()) {
            notice = "当前页仍在加载，请稍后添加书签";
            return;
        }
        final String id = session.book.id;
        final DocumentLocation location = session.position.location;
        String snippet = readingController.text().replace('\n', ' ').trim();
        final String label =
                snippet.substring(0, Math.min(24, snippet.length()))
                        + " · "
                        + String.format(Locale.ROOT, "%.1f%%", 100 * session.position.progression);
        closeOverlays();
        final long token = ++generation;
        worker.submit(
                () -> {
                    try {
                        repository.addBookmark(id, location, label);
                        runOnUiThread(
                                () -> {
                                    if (valid(token)) {
                                        notice = "已添加书签";
                                        render();
                                    }
                                });
                    } catch (Exception error) {
                        fail(token, "书签保存失败", error);
                    }
                });
    }

    private void cancelWork() {
        generation++;
        worker.cancel();
    }

    private boolean valid(long token) {
        return !destroyed && !isFinishing() && generation == token;
    }

    private void fail(long token, String message, Exception error) {
        if (error instanceof InterruptedIOException || Thread.currentThread().isInterrupted())
            return;
        Log.e("SH02LReader", message, error);
        runOnUiThread(
                () -> {
                    if (!valid(token)) return;
                    loadingLibrary = false;
                    notice = message;
                    if (!pausedBookId.isEmpty()) {
                        goLibrary();
                        notice = message;
                    }
                    render();
                });
    }

    private void savePosition() {
        if (session != null && session.displayed) {
            livePositions.put(session.book.id, session.position);
            progressWriter.save(session.book.id, session.position);
        }
    }

    public void render() {
        if (destroyed || isFinishing()) return;
        rememberSelection(builtBase, baseList);
        rememberSelection(builtOverlay, overlayList);
        Screen content = base();
        Palette palette = Palette.forTheme(content.kind == Kind.READER ? theme : uiTheme);
        background = palette.background;
        foreground = palette.foreground;
        secondary = palette.secondary;
        surface = palette.surface;
        String style =
                content.kind == Kind.READER
                        ? "reader:"
                                + theme
                                + ":"
                                + fontSize
                                + ":"
                                + infoVisible
                                + ":"
                                + readingSettings.signature()
                        : "ui:" + uiTheme + ":" + bookStyle;
        if (builtOverlayTheme != uiTheme) {
            builtOverlay = null;
            builtOverlayTheme = uiTheme;
        }
        if (builtBase != content || !style.equals(builtStyle)) {
            builtBase = content;
            builtStyle = style;
            builtOverlay = null;
            if (baseList != null && baseList.getAdapter() instanceof LibraryAdapter)
                ((LibraryAdapter) baseList.getAdapter()).close();
            root.removeAllViews();
            root.setBackgroundColor(background);
            readingView = null;
            helpView = null;
            inputView = null;
            baseList = null;
            overlayList = null;
            diagnosticView = infoView = noticeView = null;
            root.addView(buildContent(content), new FrameLayout.LayoutParams(-1, -1));
            overlayHost = new FrameLayout(this);
            root.addView(overlayHost, new FrameLayout.LayoutParams(-1, -1));
        }
        if (top().kind.overlay()) {
            if (builtOverlay != top()) {
                overlayHost.removeAllViews();
                buildOverlay(top());
                builtOverlay = top();
            }
        } else {
            overlayHost.removeAllViews();
            overlayFooter = null;
            builtOverlay = null;
            overlayList = null;
            helpView = null;
        }
        NavigationListView active = top().kind.overlay() ? overlayList : baseList;
        if (baseList != null) {
            if (baseList.getAdapter() instanceof LibraryAdapter)
                ((LibraryAdapter) baseList.getAdapter()).setSelectedBook(selectedId(content));
            ((BaseAdapter) baseList.getAdapter()).notifyDataSetChanged();
        }
        if (baseList != null) baseList.setFocusable(!top().kind.overlay());
        if (readingView != null)
            readingView.setFocusable(!top().kind.overlay() && top().kind == Kind.READER);
        if (active != null) {
            ((BaseAdapter) active.getAdapter()).notifyDataSetChanged();
            Integer offset = active.showSelection(top().selected);
            // Another key may open a screen before the scheduled list layout runs.
            if (offset != null) {
                top().selectionTop = offset;
                top().hasSelectionTop = true;
            }
            if (!active.hasFocus()) active.requestFocus();
        } else if (top().kind == Kind.INPUT && inputView != null) {
            if (!inputView.hasFocus()) inputView.editor.requestFocus();
        } else if (top().kind == Kind.HELP && helpView != null) helpView.requestFocus();
        else if (top().kind == Kind.READER && readingView != null && !readingView.hasFocus())
            readingView.requestFocus();
        if (noticeView != null) noticeView.setText(notice);
        if (infoView != null)
            infoView.setText(
                    session == null
                            ? ""
                            : String.format(
                                            Locale.ROOT,
                                            "%.1f%%",
                                            100 * session.position.progression)
                                    + " · "
                                    + session.book.charset
                                    + " · "
                                    + fontSize
                                    + "号");
        if (diagnosticView != null)
            diagnosticView.setText("累计事件：" + diagnostics.eventCount() + "\n" + diagnostics.tail(2));
        if (baseFooter != null)
            baseFooter.setHints(
                    InputBindings.hints(inputContext(), deviceKeys),
                    Palette.forTheme(content.kind == Kind.READER ? theme : uiTheme));
        if (overlayFooter != null)
            overlayFooter.setHints(
                    InputBindings.hints(inputContext(), deviceKeys), Palette.forTheme(uiTheme));
        windowPolicy();
        if (top().kind == Kind.READER) requestLayoutIfReady();
    }

    private View buildContent(Screen screen) {
        LinearLayout column = vertical();
        column.setPadding(
                dp(screen.kind == Kind.READER ? readingSettings.horizontal : 12),
                dp(screen.kind == Kind.READER ? readingSettings.vertical : 10),
                dp(screen.kind == Kind.READER ? readingSettings.horizontal : 12),
                dp(screen.kind == Kind.READER ? readingSettings.vertical : 6));
        TextView title =
                new UiStyle(this, Palette.forTheme(screen.kind == Kind.READER ? theme : uiTheme))
                        .title(screen.title);
        title.setTag("screen-title");
        if (screen.kind == Kind.READER) title.setTextSize(12);
        column.addView(title);
        if (screen.kind == Kind.READER) {
            readingView = readingController.view();
            if (readingView == null) {
                TextView loading = text("正在打开图书…", 14, secondary);
                loading.setGravity(Gravity.CENTER);
                readingView = loading;
            }
            if (readingView.getParent() instanceof ViewGroup)
                ((ViewGroup) readingView.getParent()).removeView(readingView);
            column.addView(readingView, new LinearLayout.LayoutParams(-1, 0, 1));
            if (infoVisible) {
                infoView = text("", 11, secondary);
                infoView.setTag("reading-info");
                column.addView(infoView);
            }
        } else {
            if (screen.kind == Kind.SETTINGS) column.addView(paragraph("上下选择，左右调整。返回保留正文位置。"));
            else if (screen.kind == Kind.SOURCES)
                column.addView(
                        paragraph("导入后不再依赖原文件或内存卡。\n应用导入文件夹：\n" + getExternalFilesDir("import")));
            else if (screen.kind == Kind.IMPORT)
                column.addView(paragraph("正在后台导入图书，F4 可取消。\n完成后自动打开，重复文件保留原进度。"));
            else if (screen.kind == Kind.DIAGNOSTICS) {
                column.addView(
                        paragraph(
                                Build.MODEL
                                        + " · Android "
                                        + Build.VERSION.RELEASE
                                        + "\n"
                                        + diagnosticStore.file()));
                diagnosticView = text("", 11, foreground);
                diagnosticView.setTypeface(Typeface.MONOSPACE);
                column.addView(diagnosticView);
            }
            if (screen.kind == Kind.LIBRARY && loadingLibrary) column.addView(paragraph("正在读取书架…"));
            baseList = list(screen);
            column.addView(baseList, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        noticeView = text(notice, 11, secondary);
        noticeView.setTag("notice");
        noticeView.setMaxLines(3);
        if (screen.kind == Kind.READER) {
            noticeView.setLines(1);
            noticeView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
        noticeView.setPadding(0, dp(4), 0, dp(3));
        column.addView(noticeView);
        SoftKeyBar footer = new SoftKeyBar(this);
        baseFooter = footer;
        Palette colors = Palette.forTheme(screen.kind == Kind.READER ? theme : uiTheme);
        footer.setHints(InputBindings.hints(inputContext(), deviceKeys), colors);
        column.addView(footer);
        return column;
    }

    private void buildOverlay(Screen screen) {
        helpView = null;
        overlayList = null;
        inputView = null;
        Palette palette = Palette.forTheme(uiTheme);
        UiStyle style = new UiStyle(this, palette);
        FrameLayout shade = new FrameLayout(this);
        shade.setTag("overlay");
        shade.setBackgroundColor(0x99000000);
        shade.setClickable(true);
        LinearLayout panel = vertical();
        panel.setPadding(dp(12), dp(12), dp(12), dp(10));
        panel.setBackground(round(palette.surface));
        TextView title = style.title(screen.title);
        panel.addView(title);
        if (screen.kind == Kind.IMPORT_MODE) {
            TextView explanation = text(notice, 12, palette.secondary);
            explanation.setPadding(0, 0, 0, dp(10));
            panel.addView(explanation);
        }
        if (screen.kind == Kind.INPUT) {
            inputView =
                    new ReaderInputView(
                            this,
                            palette,
                            inputInitial,
                            inputNumeric,
                            this::submitInput,
                            () -> {
                                back();
                                render();
                            });
            panel.addView(inputView, new LinearLayout.LayoutParams(-1, -2));
        } else if (screen.kind == Kind.HELP) {
            helpView = new KeyHelpView(this);
            helpView.setDeviceProfile(deviceKeys);
            helpView.setPalette(palette);
            helpView.setReadingContext(screen.helpReading);
            String[] functions = screen.helpFunctions;
            helpView.setFunctionLabels(functions[0], functions[1], functions[2], functions[3]);
            panel.addView(helpView, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            overlayList = list(screen);
            panel.addView(overlayList, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        SoftKeyBar footer = new SoftKeyBar(this);
        overlayFooter = footer;
        footer.setHints(InputBindings.hints(inputContext(), deviceKeys), palette);
        panel.addView(footer);
        int height =
                Math.min(
                        (root.getHeight() > 0
                                        ? root.getHeight()
                                        : getResources().getDisplayMetrics().heightPixels)
                                - dp(40),
                        Math.min(8, screen.rows.size()) * (style.rowHeight(64) + dp(6))
                                + dp(screen.kind == Kind.IMPORT_MODE ? 196 : 100));
        if (screen.kind == Kind.INPUT) height = dp(300);
        if (screen.kind == Kind.HELP)
            height =
                    (root.getHeight() > 0
                                    ? root.getHeight()
                                    : getResources().getDisplayMetrics().heightPixels)
                            - dp(40);
        FrameLayout.LayoutParams parameters =
                new FrameLayout.LayoutParams(-1, Math.max(dp(180), height), Gravity.CENTER);
        parameters.setMargins(dp(16), dp(20), dp(16), dp(20));
        shade.addView(panel, parameters);
        overlayHost.addView(shade, new FrameLayout.LayoutParams(-1, -1));
    }

    private NavigationListView list(Screen screen) {
        UiStyle style = new UiStyle(this, Palette.forTheme(uiTheme));
        NavigationListView list = new NavigationListView(this, uiTheme == AppearanceState.DARK);
        list.setTag("list:" + screen.kind.name());
        list.setDivider(null);
        list.setDividerHeight(dp(6));
        list.setItemsCanFocus(false);
        list.setFocusable(true);
        list.setFocusableInTouchMode(true);
        list.setDefaultFocusHighlightEnabled(false);
        list.setSelector(android.R.color.transparent);
        list.setCacheColorHint(Color.TRANSPARENT);
        if (screen.kind == Kind.LIBRARY) {
            LibraryAdapter adapter =
                    new LibraryAdapter(
                            this,
                            () -> screen.selected,
                            () -> top() == screen && resumed && hasWindowFocus());
            List<LibraryAdapter.Item> items = new ArrayList<>();
            String last = preferences.getString("lastBook", "");
            for (Row row : screen.rows) {
                BookRepository.Book book =
                        row.value instanceof BookRepository.Book
                                ? (BookRepository.Book) row.value
                                : null;
                items.add(
                        new LibraryAdapter.Item(
                                book,
                                book != null && book.id.equals(last),
                                book == null
                                        ? 0
                                        : livePositions.containsKey(book.id)
                                                ? Math.round(
                                                        livePositions.get(book.id).progression
                                                                * book.length)
                                                : book.offset,
                                row.id,
                                row.label));
            }
            adapter.setItems(items);
            adapter.setStyle(bookStyle, Palette.forTheme(uiTheme));
            list.setAdapter(adapter);
        } else
            list.setAdapter(
                    new BaseAdapter() {
                        public int getCount() {
                            return screen.rows.size();
                        }

                        public Row getItem(int position) {
                            return screen.rows.get(position);
                        }

                        public long getItemId(int position) {
                            return position;
                        }

                        public View getView(int position, View convert, ViewGroup parent) {
                            TextView row =
                                    convert instanceof UiStyle.Label
                                            ? (TextView) convert
                                            : style.actionRow("", false);
                            Row item = getItem(position);
                            row.setTag("item:" + item.id);
                            style.bind(
                                    row,
                                    item.label,
                                    position == screen.selected,
                                    top() == screen && resumed && hasWindowFocus());
                            row.setFocusable(false);
                            return row;
                        }
                    });
        list.setOnItemClickListener(
                (parent, view, position, id) -> {
                    if (top() != screen) return;
                    screen.selected = position;
                    activate();
                    render();
                });
        list.setOnScrollListener(
                new AbsListView.OnScrollListener() {
                    public void onScrollStateChanged(AbsListView view, int state) {}

                    public void onScroll(AbsListView view, int first, int visible, int total) {
                        rememberSelection(screen, list);
                        if (list.getAdapter() instanceof LibraryAdapter) {
                            LibraryAdapter adapter = (LibraryAdapter) list.getAdapter();
                            list.post(
                                    () -> {
                                        if (list.getAdapter() == adapter)
                                            adapter.setVisibleRange(first, visible);
                                    });
                        }
                    }
                });
        list.restoreSelection(screen.selected, screen.hasSelectionTop ? screen.selectionTop : 0);
        return list;
    }

    private void rememberSelection(Screen screen, NavigationListView list) {
        if (screen == null || list == null || list.getSelectedItemPosition() != screen.selected)
            return;
        View row = list.getSelectedView();
        if (row != null && list.getPositionForView(row) == screen.selected) {
            screen.selectionTop = row.getTop() - list.getListPaddingTop();
            screen.hasSelectionTop = true;
        }
    }

    private void invalidateLists() {
        builtBase = null;
        builtOverlay = null;
    }

    private String selectedId(Screen screen) {
        if (screen.rows.isEmpty()) return "";
        Row row = screen.rows.get(bound(screen.selected, 0, screen.rows.size() - 1));
        return row.value instanceof BookRepository.Book
                ? ((BookRepository.Book) row.value).id
                : row.id;
    }

    private void restoreSelection(Screen screen, String id) {
        screen.selected = bound(screen.selected, 0, Math.max(0, screen.rows.size() - 1));
        for (int i = 0; i < screen.rows.size(); i++) {
            Row row = screen.rows.get(i);
            String value =
                    row.value instanceof BookRepository.Book
                            ? ((BookRepository.Book) row.value).id
                            : row.id;
            if (value.equals(id)) {
                screen.selected = i;
                break;
            }
        }
    }

    private void windowPolicy() {
        int flags =
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (getWindow().getDecorView().getSystemUiVisibility() != flags)
            getWindow().getDecorView().setSystemUiVisibility(flags);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        if (attributes.screenBrightness != brightness / 10f) {
            attributes.screenBrightness = brightness / 10f;
            getWindow().setAttributes(attributes);
        }
    }

    private String snapshot() {
        return "SH02L Reader "
                + BuildConfig.VERSION_NAME
                + "\nmodel="
                + Build.MODEL
                + " api="
                + Build.VERSION.SDK_INT
                + "\nscreen="
                + top().kind
                + "\nbook="
                + (session == null ? "" : session.book.id)
                + "\nprofile="
                + deviceKeys.id()
                + "\nlocator="
                + (session == null ? "" : session.position.location)
                + "\ncommitted="
                + committedBook
                + ":"
                + committedLocation
                + "\n"
                + diagnostics.tail(256);
    }

    private static int bound(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long boundOffset(long value, long length) {
        return Math.max(0, Math.min(Math.max(0, length - 1), value));
    }

    private static String size(long bytes) {
        return String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0);
    }

    private static String percent(long position, long length) {
        return String.format(Locale.ROOT, "%.1f%%", length == 0 ? 0 : 100.0 * position / length);
    }

    private LinearLayout vertical() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private TextView paragraph(String value) {
        TextView view = text(value, 13, secondary);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private GradientDrawable round(int color) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color);
        result.setCornerRadius(dp(12));
        return result;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // Immutable observations used by instrumentation; no test-only behavior in production
    // navigation.
    long readingOffset() {
        return session == null || !session.displayed ? -1 : currentOffset();
    }

    long readingEnd() {
        DocumentLocation end = readingController.end();
        return end == null ? -1 : end.format.equals("txt") ? end.textOffset() : currentOffset();
    }

    String readingText() {
        return readingController.text();
    }

    boolean positionCommitted() {
        return session != null
                && session.displayed
                && committedBook.equals(session.book.id)
                && session.position.location.equals(committedLocation);
    }

    boolean readingReady() {
        return base().kind == Kind.READER && readingController.ready();
    }

    boolean libraryReady() {
        return base().kind == Kind.LIBRARY && !loadingLibrary;
    }

    String screenKind() {
        return top().kind.name();
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (router == null) return;
        if (!focused) {
            router.reset();
            UiStyle.pauseMarquees(root);
        } else {
            windowPolicy();
            render();
        }
        diagnostics.append("window-focus=" + focused);
    }

    @Override
    protected void onPause() {
        resumed = false;
        UiStyle.pauseMarquees(root);
        super.onPause();
        router.reset();
        savePosition();
        saveSettings();
        diagnosticStore.save(snapshot(), null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (progressWriter != null) progressWriter.retry();
        if (root != null) render();
    }

    @Override
    protected void onDestroy() {
        if (baseList != null && baseList.getAdapter() instanceof LibraryAdapter)
            ((LibraryAdapter) baseList.getAdapter()).close();
        destroyed = true;
        generation++;
        readingController.destroy();
        worker.close();
        progressWriter.close();
        diagnosticStore.close();
        // The application-scoped database remains available while outstanding durable writes
        // finish.
        super.onDestroy();
    }

    private void libraryPage(int direction) {
        Screen shelf = screens.get(0);
        shelf.page = Math.max(0, shelf.page + direction);
        shelf.rows.clear();
        shelf.selected = 0;
        shelf.hasSelectionTop = false;
        libraryReturnId = "";
        invalidateLists();
        loadLibrary(false);
        render();
    }

    @Override
    public Activity activity() {
        return this;
    }

    @Override
    public BookRepository repository() {
        return repository;
    }

    @Override
    public StorageManager storage() {
        return storageManager;
    }

    @Override
    public ReaderSession reading() {
        return session;
    }

    @Override
    public ReadingSettings readingSettings() {
        return readingSettings;
    }

    @Override
    public long currentOffset() {
        return session == null
                ? 0
                : session.book.format.equals("txt")
                        ? session.position.location.textOffset()
                        : Math.round(session.position.progression * session.book.length);
    }

    @Override
    public void notice(String message) {
        notice = message;
        render();
    }

    @Override
    public <T> void task(String message, FeatureHost.Work<T> work, Consumer<T> done) {
        Screen origin =
                top().kind == Kind.INPUT && screens.size() > 1
                        ? screens.get(screens.size() - 2)
                        : top();
        final long token = ++generation;
        origin.busy = true;
        notice = message;
        render();
        worker.submit(
                () -> {
                    try {
                        T result = work.run();
                        runOnUiThread(
                                () -> {
                                    if (!valid(token) || !screens.contains(origin)) return;
                                    origin.busy = false;
                                    if (notice.equals(message)) notice = "";
                                    done.accept(result);
                                    if (top() == origin)
                                        builtOverlay =
                                                null; // Async rows can need more space than their
                                    // loading placeholder.
                                    render();
                                });
                    } catch (Exception error) {
                        fail(token, operationError(error), error);
                    }
                });
    }

    @Override
    public void input(
            String title, String initial, boolean numeric, FeatureHost.InputSubmit submit) {
        inputScreen = new Screen(Kind.INPUT, title);
        inputInitial = initial;
        inputNumeric = numeric;
        inputSubmit = submit;
        push(inputScreen);
        render();
        if (inputView != null) {
            inputView.editor.requestFocus();
            inputView.editor.post(
                    () -> {
                        if (top().kind == Kind.INPUT && inputView != null)
                            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                                    .showSoftInput(
                                            inputView.editor, InputMethodManager.SHOW_IMPLICIT);
                    });
        }
    }

    private void submitInput() {
        if (inputView == null || inputSubmit == null || top() != inputScreen) return;
        Screen origin = inputScreen;
        FeatureHost.InputSubmit submit = inputSubmit;
        String value = inputView.editor.getText().toString();
        String error = submit.submit(value);
        if (error != null) {
            if (inputView != null) inputView.error(error);
        } else if (top() == origin) {
            back();
            render();
        }
    }

    @Override
    public void refreshShelf(String preferredId) {
        libraryReturnId = preferredId;
        goLibrary();
    }

    @Override
    public void open(BookRepository.Book book) {
        openBook(book);
    }

    @Override
    public void seekTo(long offset) {
        seek(offset);
    }

    @Override
    public void seekTo(DocumentLocation location) {
        if (session != null && session.book.format.equals(location.format))
            readingController.seek(location);
    }

    @Override
    public void quiesceBook(String id) {
        if (session != null && session.book.id.equals(id)) {
            savePosition();
            cancelWork();
            readingController.close();
            pausedBookId = id;
            session = null;
            preferences.edit().putBoolean("reopen", false).apply();
        }
        livePositions.remove(id);
    }

    @Override
    public void forgetBook(String id) {
        livePositions.remove(id);
        progressWriter.forget(id);
        if (committedBook.equals(id)) {
            committedBook = "";
            committedLocation = null;
        }
        if (id.equals(preferences.getString("lastBook", "")))
            preferences.edit().remove("lastBook").putBoolean("reopen", false).apply();
    }

    @Override
    public void retryProgress() {
        progressWriter.retry();
    }

    @Override
    public void awaitProgress() throws Exception {
        if (!progressWriter.awaitIdle(5000)) throw new IOException("阅读位置仍待保存，请释放空间后重试");
    }

    @Override
    public void layoutChanged() {
        appearanceChanged();
        for (Screen screen : screens) if (screen.kind == Kind.SETTINGS) refreshSettings(screen);
        invalidateLists();
        render();
    }

    @Override
    public void importFont() {
        sources(new ImportRequest(ImportKind.FONT, ""));
        render();
    }

    private static String operationError(Exception error) {
        String message = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("encrypted")) return "此 EPUB 含加密资源，暂不支持";
        if (message.contains("fixed-layout")) return "暂不支持固定版式 EPUB";
        if (message.contains("epub") || message.contains("zip")) return "EPUB 结构或资源无法读取，请检查文件";
        if (message.contains("space") || message.contains("storage") || message.contains("待保存"))
            return "操作未完成：空间不足或进度仍待保存，请清理空间后重试";
        if (message.contains("encoding")
                || message.contains("decode")
                || message.contains("malformed")) return "无法按所选编码读取，原始文件已保留";
        if (message.contains("outside")
                || message.contains("unsafe")
                || message.contains("selection")) return "文件位置已变化，请返回并重新选择";
        return "操作未完成，请检查文件、权限和可用空间";
    }
}

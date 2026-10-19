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
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.sh02l.reader.core.*;
import com.sh02l.reader.core.text.TextImporter;
import com.sh02l.reader.core.importing.*;
import com.sh02l.reader.txt.TxtPaginator;
import java.io.*;
import java.util.*;

/** One window, one key router, and bounded asynchronous TXT sessions. */
public final class MainActivity extends Activity implements KeyRouter.Target {
    private enum Kind {
        LIBRARY, SOURCES, FILES, IMPORT, IMPORT_MODE, READER, SETTINGS, HELP, DIAGNOSTICS,
        MENU, CONTENTS, BOOKMARKS, EXIT;
        boolean overlay() { return this==SETTINGS || this==HELP || this==MENU || this==CONTENTS || this==BOOKMARKS || this==EXIT || this==IMPORT_MODE; }
    }
    private static final class Row {
        final String id, label; final Object value;
        Row(String id, String label) { this(id, label, null); }
        Row(String id, String label, Object value) { this.id = id; this.label = label; this.value = value; }
    }
    private static final class Screen {
        final Kind kind; final List<Row> rows = new ArrayList<>();
        int selected, group; String title; File directory;final String targetBookId;
        boolean helpReading;String[] helpFunctions;
        Screen(Kind kind, String title) {this(kind,title,"");}
        Screen(Kind kind,String title,String targetBookId) {this.kind=kind;this.title=title;this.targetBookId=targetBookId;}
    }
    private static final class Session {
        final BookRepository.Book book; final TextImporter.Result index; final TxtPaginator paginator;
        Session(BookRepository.Book book, TextImporter.Result index, File directory) {
            this.book = book; this.index = index;
            this.paginator = new TxtPaginator(new File(directory, "text.u16"), index.length);
        }
    }
    private enum ImportKind { TXT,COVER }
    private static final class ImportRequest {
        final ImportKind kind;final String targetBookId;
        ImportRequest(ImportKind kind,String targetBookId){this.kind=kind;this.targetBookId=targetBookId;}
    }
    private static final class ExportRequest {
        final File original;final String name,mime,sha256;
        ExportRequest(File original,String name,String mime,String sha256){this.original=original;this.name=name;this.mime=mime;this.sha256=sha256;}
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
    private KeyHelpView helpView;
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
    private ImportRequest importRequest,pickerRequest,modeRequest,storagePermissionRequest;
    private AndroidImportSource selectedImportSource;
    private File selectedImportFile;
    private ExportRequest pendingExport;
    private String importCompletionNotice="";
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
        if(kind==Kind.HELP){switch(key){case ZERO:return Action.HELP;case F2:case F4:return Action.BACK;case UP:return Action.UP;case DOWN:return Action.DOWN;default:return Action.NONE;}}
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
        if(action!=Action.NONE)importCompletionNotice="";
        switch (action) {
            case UP: if(top().kind==Kind.HELP&&helpView!=null)helpView.move(-1);else select(top().selected - 1); break;
            case DOWN: if(top().kind==Kind.HELP&&helpView!=null)helpView.move(1);else select(top().selected + 1); break;
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
        if(removed.kind==Kind.IMPORT_MODE){selectedImportSource=null;selectedImportFile=null;modeRequest=null;}
        if(removed.kind==Kind.SOURCES){importRequest=null;pickerRequest=null;storagePermissionRequest=null;}
        if (removed.kind == Kind.IMPORT || removed.kind == Kind.FILES || removed.kind == Kind.SOURCES) cancelWork();
        if (removed.kind == Kind.READER) {
            if (session != null) libraryReturnId = session.book.id;
            savePosition(); cancelWork(); session = null; page = null; requestedSpec = null; pendingTurns = 0;
            preferences.edit().putBoolean("reopen", false).apply(); loadLibrary(false);
        } else if (top().kind == Kind.LIBRARY) loadLibrary(false);
    }
    private void closeOverlays() { while (top().kind.overlay()) back(); }
    private void goLibrary() {
        pendingExport=null;
        importRequest=null;pickerRequest=null;modeRequest=null;storagePermissionRequest=null;selectedImportSource=null;selectedImportFile=null;
        if (session != null) libraryReturnId = session.book.id;
        savePosition(); cancelWork();
        while (screens.size() > 1) screens.remove(screens.size() - 1);
        context++; session = null; page = null; requestedSpec = null;
        preferences.edit().putBoolean("reopen", false).apply(); loadLibrary(false);
    }
    private void menu() {
        Row selected=base().rows.isEmpty()?null:base().rows.get(bound(base().selected,0,base().rows.size()-1));
        BookRepository.Book target=base().kind==Kind.LIBRARY&&selected!=null&&selected.value instanceof BookRepository.Book?(BookRepository.Book)selected.value:null;
        Screen screen = new Screen(Kind.MENU,target==null?"菜单":target.title,target==null?"":target.id);
        if(target!=null){screen.rows.add(new Row("cover","设置 / 更换封面",target.id));screen.rows.add(new Row("export-text","导出原 TXT",target.id));}
        if (base().kind == Kind.READER && session != null) { screen.rows.add(new Row("contents", "目录")); screen.rows.add(new Row("bookmarks", "书签")); }
        else if (base().kind == Kind.LIBRARY) screen.rows.add(new Row("import", "导入 TXT"));
        if (base().kind == Kind.IMPORT) screen.rows.add(new Row("cancel-import", "取消导入"));
        screen.rows.add(new Row("settings", "阅读设置")); screen.rows.add(new Row("diagnostics", "按键诊断"));
        screen.rows.add(new Row("help", "按键说明"));
        if (session != null) screen.rows.add(new Row("library", "返回书架"));
        screen.rows.add(new Row("exit", "退出应用")); screen.rows.add(new Row("back", "关闭菜单")); push(screen);
        if(target!=null){final long token=++generation;worker.submit(()->{
            try{CoverStore.Record cover=new CoverStore(this).get(target.id);if(cover==null)return;
                runOnUiThread(()->{if(!valid(token)||!screens.contains(screen))return;String selectedItem=selectedId(screen);screen.rows.add(screen.rows.size()-1,new Row("export-cover","导出封面原图",target.id));restoreSelection(screen,selectedItem);if(top()==screen)builtOverlay=null;render();});
            }catch(IOException error){Log.w("SH02LReader","Cover metadata unavailable",error);}
        });}
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
        if(top().kind==Kind.HELP){back();return;}
        Screen screen = new Screen(Kind.HELP, "SH-02L 按键说明");screen.helpReading=top().kind==Kind.READER;
        screen.helpFunctions=new String[]{helpAction(resolve(Key.F1)),helpAction(resolve(Key.F2)),helpAction(resolve(Key.F3)),helpAction(resolve(Key.F4))};push(screen);
    }
    private static String helpAction(Action action){switch(action){case MENU:return "菜单";case IMPORT:return "导入";case BACK:return "返回";default:return "无操作";}}
    private void activate() {
        if (top().rows.isEmpty()) return;
        Row row = top().rows.get(bound(top().selected, 0, top().rows.size() - 1));
        switch (row.id) {
            case "import": sources(); break;
            case "book": openBook((BookRepository.Book) row.value); break;
            case "directory": files((File) row.value); break;
            case "file": offerSource(AndroidImportSource.fromFile((File)row.value),(File)row.value,importRequest); break;
            case "cover": sources(new ImportRequest(ImportKind.COVER,(String)row.value));break;
            case "copy-import": {
                AndroidImportSource source=selectedImportSource;File file=selectedImportFile;ImportRequest request=modeRequest;
                if(source!=null&&request!=null){if(file!=null&&request.kind==ImportKind.TXT)importFile(file);else executeImport(source,ImportCoordinator.Mode.COPY,request);}break;
            }
            case "move-import": if(selectedImportSource!=null&&modeRequest!=null)executeImport(selectedImportSource,ImportCoordinator.Mode.MOVE,modeRequest);break;
            case "export-text": exportOriginal((String)row.value,false);break;
            case "export-cover": exportOriginal((String)row.value,true);break;
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
                if (initial) {
                    repository.cleanupStaging();
                    try{new CoverStore(this).cleanupInterruptedImports();}catch(InterruptedIOException error){throw error;}catch(IOException error){Log.w("SH02LReader","Cover cleanup will retry on next startup",error);}
                }
                List<BookRepository.Book> books = repository.list();
                runOnUiThread(() -> {
                    if (!valid(token)) return; loadingLibrary = false;
                    Screen library = screens.get(0);
                    String selectedId = libraryReturnId.isEmpty() ? selectedId(library) : libraryReturnId;
                    libraryReturnId = "";
                    library.rows.clear();
                    String lastId = preferences.getString("lastBook", ""); BookRepository.Book last = null;
                    for (BookRepository.Book book : books) if (book.id.equals(lastId)) last = book;
                    if (last != null) library.rows.add(new Row("book", last.title, last));
                    library.rows.add(new Row("import", "＋ 导入 TXT"));
                    for (BookRepository.Book book : books)if(last==null||!last.id.equals(book.id))library.rows.add(new Row("book",book.title,book));
                    library.title = "书架 · " + books.size() + " 本"; restoreSelection(library, selectedId);
                    invalidateLists(); render();
                    if (pendingUri != null) { Uri uri = pendingUri; pendingUri = null; importUri(uri); }
                    else if (initial && top().kind == Kind.LIBRARY && preferences.getBoolean("reopen", false) && last != null) openBook(last);
                });
            } catch (Exception error) { fail(token, "书架读取失败", error); }
        });
    }
    private void sources() {
        sources(new ImportRequest(ImportKind.TXT,""));
    }
    private void sources(ImportRequest request) {
        if (base().kind == Kind.IMPORT) { notice = "请先完成或取消当前导入"; return; }
        pendingExport=null;
        closeOverlays();importRequest=request;Screen screen = new Screen(Kind.SOURCES, request.kind==ImportKind.COVER?"导入封面":"导入 TXT");
        File importDirectory = getExternalFilesDir("import");
        if (importDirectory != null) { importDirectory.mkdirs(); screen.rows.add(new Row("directory", "应用导入文件夹", importDirectory)); }
        screen.rows.add(new Row("shared", "手机存储 / 内存卡"));
        screen.rows.add(new Row("system-picker", "系统文件选择")); screen.rows.add(new Row("back", "返回"));
        push(screen); notice = "选择文件后可复制或移动导入；私有原件可从图书菜单导出";
    }
    private void requestSharedStorage() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            storagePermissionRequest=importRequest;
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
            ImportRequest requested=storagePermissionRequest;storagePermissionRequest=null;
            if(requested==null||requested!=importRequest||top().kind!=Kind.SOURCES)return;
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) sharedRoots();
            else notice = "未获得读取权限，可使用系统文件选择或应用导入文件夹";
            render();
        }
    }
    private void systemPicker() {
        try {
            pickerRequest=importRequest;if(pickerRequest==null)return;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(pickerRequest.kind==ImportKind.COVER?"image/*":"text/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, 2);
        } catch (ActivityNotFoundException error) {pickerRequest=null; notice = "设备没有系统文件选择器，请使用手机存储入口"; }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if(request==2){ImportRequest selected=pickerRequest;pickerRequest=null;
            if(selected!=null&&selected==importRequest&&result==RESULT_OK&&data!=null&&data.getData()!=null){
                int grants=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if((data.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0)try{
                    boolean read=(grants&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0,write=(grants&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0;
                    if(read&&write)getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    else if(read)getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    else if(write)getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }catch(SecurityException ignored){}
                offerSource(AndroidImportSource.fromUri(getContentResolver(),data.getData(),grants),null,selected);
            }else if(selected!=null&&selected==importRequest)notice="已取消文件选择";render();
        }else if(request==3){ExportRequest export=pendingExport;pendingExport=null;if(export==null)return;if(result==RESULT_OK&&data!=null&&data.getData()!=null)writeExport(export,data.getData(),data.getFlags());else{notice="已取消导出";render();}}
    }
    private void files(File directory) {
        // Directories replace the file screen while parent frames retain their exact selection.
        Screen screen = new Screen(Kind.FILES, directory.getName().isEmpty() ? "文件" : directory.getName());
        screen.directory = directory;
        if (top().kind == Kind.FILES && top().directory == null) { screens.remove(screens.size() - 1); context++; }
        if (screens.size() >= 12) { notice = "目录层级过深，请用系统文件选择"; return; }
        screens.add(screen); context++; notice = "读取目录…";
        final long token = ++generation;
        final boolean images=importRequest!=null&&importRequest.kind==ImportKind.COVER;
        worker.submit(() -> {
            try {
                File[] entries = directory.listFiles(file -> {String name=file.getName().toLowerCase(Locale.ROOT);return file.isDirectory()||(images?(name.endsWith(".jpg")||name.endsWith(".jpeg")||name.endsWith(".png")||name.endsWith(".webp")):name.endsWith(".txt"));});
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
                    screen.rows.addAll(rows); notice = entries.length == 0 ? "此目录没有可导入文件" : "上下选择，确认打开";
                    invalidateLists(); render();
                });
            } catch (Exception error) { fail(token, "目录不可读取，请返回并检查权限或内存卡", error); }
        });
        render();
    }
    // The file-copy action and native fixtures share the exact same production import path.
    void importFile(File file) {ImportRequest request=new ImportRequest(ImportKind.TXT,"");importRequest=request;executeImport(AndroidImportSource.fromFile(file),ImportCoordinator.Mode.COPY,request);}
    private void importUri(Uri uri) {
        if (!"content".equals(uri.getScheme())) { notice = "仅支持通过文件提供程序打开 TXT"; render(); return; }
        pendingExport=null;
        ImportRequest request=new ImportRequest(ImportKind.TXT,"");importRequest=request;
        offerSource(AndroidImportSource.fromUri(getContentResolver(),uri,getIntent().getFlags()),null,request);
    }
    private void offerSource(AndroidImportSource source,File file,ImportRequest request) {
        if(request==null)return;notice="读取文件信息…";final long token=++generation;
        worker.submit(()->{try{source.snapshot();boolean move=source.canDelete();runOnUiThread(()->{
            if(!valid(token)||request!=importRequest)return;selectedImportSource=source;selectedImportFile=file;modeRequest=request;
            Screen screen=new Screen(Kind.IMPORT_MODE,source.displayName());screen.rows.add(new Row("copy-import","复制导入"));if(move)screen.rows.add(new Row("move-import","移动导入"));screen.rows.add(new Row("back","返回"));
            notice=(move?"移动会在完整校验并提交后删除来源。":"此来源只读或无法可靠核验，可使用复制导入。")+"私有副本随卸载 / 清除数据移除，可从图书菜单导出。";push(screen);render();
        });}catch(Exception error){fail(token,"文件不可读取，请检查授权或重新选择",error);}});render();
    }
    private void executeImport(AndroidImportSource source,ImportCoordinator.Mode mode,ImportRequest request) {
        if (base().kind == Kind.IMPORT) return;
        pendingExport=null;
        savePosition(); cancelWork(); closeOverlays();
        Screen screen = new Screen(Kind.IMPORT, request.kind==ImportKind.COVER?"导入封面":"导入 TXT"); screen.rows.add(new Row("back", "取消导入")); push(screen);
        notice = "准备导入：" + source.displayName(); render();
        final long token = ++generation;
        worker.submit(() -> {
            try {
                ImportCoordinator coordinator=new ImportCoordinator(new SpaceGuard(()->getFilesDir().getUsableSpace(),8L*1024*1024));
                final long[] last = {0};
                TextImporter.Observer observer=new TextImporter.Observer() {
                    public boolean cancelled() { return Thread.currentThread().isInterrupted(); }
                    public void progress(String phase, long bytes) {
                        long now = SystemClock.uptimeMillis(); if (now - last[0] < 200) return; last[0] = now;
                        String label = "copy".equals(phase) ? "复制" : "encoding".equals(phase) ? "识别编码" : "decode".equals(phase) ? "建立正文索引" : "保存";
                        runOnUiThread(() -> { if (valid(token)) { notice = label + " · " + size(bytes) + "　F2 取消"; render(); } });
                    }
                };
                ImportCoordinator.Outcome outcome=coordinator.run(source,mode,new ImportCoordinator.Committer(){
                    public long peakBytes(long known)throws IOException{if(request.kind==ImportKind.TXT)return ImportCoordinator.Committer.super.peakBytes(known);try{return known<0?1024*1024:Math.addExact(known,1024*1024);}catch(ArithmeticException error){throw new IOException("Import budget overflow",error);}}
                    public ImportCoordinator.CommittedAsset commit(ImportSource input,TextImporter.Observer guarded)throws IOException{
                        if(request.kind==ImportKind.TXT){BookRepository.Book book=repository.importBook(input.open(),source.displayName(),guarded);return new ImportCoordinator.CommittedAsset(book.id,new File(repository.directory(book.id),"original.bin"),book.id);}
                        if(repository.get(request.targetBookId)==null)throw new IOException("Cover target unavailable");CoverStore.Record cover=new CoverStore(MainActivity.this).importCover(request.targetBookId,input.open(),source.displayName(),guarded);
                        return new ImportCoordinator.CommittedAsset(cover.bookId,cover.original,cover.sha256);
                    }
                },observer);
                BookRepository.Book book=request.kind==ImportKind.TXT?repository.get(outcome.assetId):null;
                runOnUiThread(() -> {
                    if (!valid(token)) return;
                    String result=outcome.status==ImportCoordinator.Status.MOVED?"移动完成":outcome.status==ImportCoordinator.Status.COPIED_SOURCE_RETAINED?"导入完成，原文件未删除":"导入完成";
                    Screen help=top().kind==Kind.HELP?top():null;
                    while (screens.size() > 1) screens.remove(screens.size() - 1);
                    context++;importRequest=null;modeRequest=null;selectedImportSource=null;selectedImportFile=null;
                    if(book!=null){openBook(book);importCompletionNotice=result;notice=result;}
                    else{libraryReturnId=request.targetBookId;goLibrary();notice=result;}
                    if(help!=null)push(help);
                    Toast.makeText(MainActivity.this,result,Toast.LENGTH_LONG).show();render();
                });
            } catch (Exception error) { fail(token, "导入失败：请检查文本编码、权限和可用空间；原文件未改动", error); }
        });
    }
    private void exportOriginal(String id,boolean cover) {
        Screen origin=top();final long token=++generation;notice="准备导出原件…";
        worker.submit(()->{try{
            ExportRequest request;
            if(cover){CoverStore.Record record=new CoverStore(this).get(id);if(record==null||!record.sha256.equals(CoverStore.hash(record.original)))throw new IOException("Cover original unavailable");request=new ExportRequest(record.original,record.name,record.mime,record.sha256);}
            else{BookRepository.Book book=repository.get(id);if(book==null)throw new IOException("Book unavailable");request=new ExportRequest(repository.original(id),book.title+".txt","text/plain",id);}
            runOnUiThread(()->{if(!valid(token)||top()!=origin)return;try{pendingExport=request;startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(request.mime).putExtra(Intent.EXTRA_TITLE,request.name).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION),3);}catch(ActivityNotFoundException error){pendingExport=null;notice="设备没有可用的系统导出入口";render();}});
        }catch(Exception error){fail(token,"原件不可读取，无法导出",error);}});
    }
    private void writeExport(ExportRequest request,Uri destination,int grants) {
        if(!"content".equals(destination.getScheme())){notice="请选择系统文档目标";render();return;}
        final long token=++generation;notice="正在导出原件…";render();worker.submit(()->{
            try{if(!request.sha256.equals(ImportCoordinator.sha256(request.original)))throw new IOException("Original changed");
                OriginalExporter.copy(request.original,getContentResolver().openOutputStream(destination,"w"),null);
                runOnUiThread(()->{if(!valid(token))return;notice="原件导出完成";if(top().kind==Kind.MENU)back();Toast.makeText(this,"原件导出完成",Toast.LENGTH_LONG).show();render();});
            }catch(Exception error){boolean removed=cleanupFailedExport(destination,grants);runOnUiThread(()->{if(!valid(token))return;notice=removed?"导出失败，已清理不完整文件":"导出失败，目标文件可能不完整";Toast.makeText(this,notice,Toast.LENGTH_LONG).show();render();});}
        });
    }
    private boolean cleanupFailedExport(Uri destination,int grants) {
        if((grants&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0)return false;
        try(Cursor cursor=getContentResolver().query(destination,new String[]{DocumentsContract.Document.COLUMN_FLAGS},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()&&(cursor.getInt(0)&DocumentsContract.Document.FLAG_SUPPORTS_DELETE)!=0)return DocumentsContract.deleteDocument(getContentResolver(),destination);
        }catch(Exception ignored){}return false;
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
            notice = !importCompletionNotice.isEmpty()?importCompletionNotice:boundary ? result.start == 0 ? "已经是第一页" : "已经是最后一页" : target.book.length == 0 ? "这是空文本" : "";
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
    private boolean valid(long token) { return !destroyed && !isFinishing() && generation == token; }
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
        if (destroyed || isFinishing()) return;
        Screen content = base();Palette palette=Palette.forTheme(content.kind==Kind.READER?theme:uiTheme);
        background=palette.background;foreground=palette.foreground;secondary=palette.secondary;surface=palette.surface;
        String style = content.kind==Kind.READER ? "reader:"+theme+":"+fontSize+":"+infoVisible : "ui:"+uiTheme+":"+bookStyle;
        if(builtOverlayTheme!=uiTheme){builtOverlay=null;builtOverlayTheme=uiTheme;}
        if (builtBase != content || !style.equals(builtStyle)) {
            builtBase = content; builtStyle = style; builtOverlay = null;
            if(baseList!=null&&baseList.getAdapter() instanceof LibraryAdapter)((LibraryAdapter)baseList.getAdapter()).close();
            root.removeAllViews(); root.setBackgroundColor(background);
            pageView = null; helpView=null; baseList = null; overlayList = null; diagnosticView = infoView = noticeView = null;
            root.addView(buildContent(content), new FrameLayout.LayoutParams(-1, -1));
            overlayHost = new FrameLayout(this); root.addView(overlayHost, new FrameLayout.LayoutParams(-1, -1));
        }
        if (top().kind.overlay()) {
            if (builtOverlay != top()) { overlayHost.removeAllViews(); buildOverlay(top()); builtOverlay = top(); }
        } else { overlayHost.removeAllViews(); builtOverlay = null; overlayList = null;helpView=null; }
        ListView active = top().kind.overlay() ? overlayList : baseList;
        if(baseList!=null){if(baseList.getAdapter() instanceof LibraryAdapter)((LibraryAdapter)baseList.getAdapter()).setSelectedBook(selectedId(content));((BaseAdapter)baseList.getAdapter()).notifyDataSetChanged();}
        if (baseList != null) baseList.setFocusable(!top().kind.overlay());
        if (pageView != null) pageView.setFocusable(!top().kind.overlay() && top().kind == Kind.READER);
        if (active != null) {
            ((BaseAdapter) active.getAdapter()).notifyDataSetChanged();
            if (active.getSelectedItemPosition() != top().selected) active.setSelection(top().selected);
            if (!active.hasFocus()) active.requestFocus();
        } else if(top().kind==Kind.HELP&&helpView!=null)helpView.requestFocus();
        else if (top().kind == Kind.READER && pageView != null && !pageView.hasFocus()) pageView.requestFocus();
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
            if (screen.kind == Kind.SETTINGS) column.addView(paragraph("上下选择，左右调整。返回保留正文位置。"));
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
        helpView=null;overlayList=null;
        Palette palette=Palette.forTheme(uiTheme);UiStyle style=new UiStyle(this,palette);
        FrameLayout shade = new FrameLayout(this); shade.setTag("overlay"); shade.setBackgroundColor(0x99000000); shade.setClickable(true);
        LinearLayout panel = vertical(); panel.setPadding(dp(12), dp(12), dp(12), dp(10)); panel.setBackground(round(palette.surface));
        TextView title=style.title(screen.title);panel.addView(title);
        if(screen.kind==Kind.IMPORT_MODE){TextView explanation=text(notice,12,palette.secondary);explanation.setPadding(0,0,0,dp(10));panel.addView(explanation);}
        if(screen.kind==Kind.HELP){helpView=new KeyHelpView(this);helpView.setPalette(palette);helpView.setReadingContext(screen.helpReading);String[] functions=screen.helpFunctions;helpView.setFunctionLabels(functions[0],functions[1],functions[2],functions[3]);panel.addView(helpView,new LinearLayout.LayoutParams(-1,0,1));}
        else{overlayList = list(screen); panel.addView(overlayList, new LinearLayout.LayoutParams(-1, 0, 1));}
        SoftKeyBar footer=new SoftKeyBar(this);footer.setHints(screen.kind==Kind.HELP?"↑↓ 滚动":"↑↓ 选择",screen.kind==Kind.HELP?"0 关闭":"确认 执行","F2 返回",palette);panel.addView(footer);
        int height = Math.min((root.getHeight()>0?root.getHeight():getResources().getDisplayMetrics().heightPixels) - dp(40), Math.min(8,screen.rows.size())*(style.rowHeight(64)+dp(6))+dp(screen.kind==Kind.IMPORT_MODE?196:100));
        if(screen.kind==Kind.HELP)height=(root.getHeight()>0?root.getHeight():getResources().getDisplayMetrics().heightPixels)-dp(40);
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
        if(screen.kind==Kind.LIBRARY){
            LibraryAdapter adapter=new LibraryAdapter(this,()->screen.selected,()->top()==screen&&resumed&&hasWindowFocus());
            List<LibraryAdapter.Item> items=new ArrayList<>();String last=preferences.getString("lastBook","");
            for(Row row:screen.rows){BookRepository.Book book=row.value instanceof BookRepository.Book?(BookRepository.Book)row.value:null;
                items.add(new LibraryAdapter.Item(book,book!=null&&book.id.equals(last),book==null?0:livePositions.containsKey(book.id)?livePositions.get(book.id):book.offset));}
            adapter.setItems(items);adapter.setStyle(bookStyle,Palette.forTheme(uiTheme));list.setAdapter(adapter);
            list.setOnScrollListener(new AbsListView.OnScrollListener(){
                public void onScrollStateChanged(AbsListView view,int state){}
                public void onScroll(AbsListView view,int first,int visible,int total){list.post(()->{if(list.getAdapter()==adapter)adapter.setVisibleRange(first,visible);});}
            });
        }else list.setAdapter(new BaseAdapter() {
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
    boolean readingReady() { return base().kind==Kind.READER && session != null && page != null && !layoutBusy && page.spec.equals(requestedSpec); }
    boolean libraryReady() { return base().kind==Kind.LIBRARY&&!loadingLibrary; }
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
        if(baseList!=null&&baseList.getAdapter() instanceof LibraryAdapter)((LibraryAdapter)baseList.getAdapter()).close();
        destroyed = true; generation++; worker.close(); progressWriter.close(); diagnosticStore.close();
        // The application-scoped database remains available while outstanding durable writes finish.
        super.onDestroy();
    }
}

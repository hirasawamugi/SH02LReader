package com.sh02l.reader;

import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import com.sh02l.reader.core.AppearanceState;
import java.io.ByteArrayInputStream;

@SuppressWarnings("deprecation")
public final class AppearanceMigrationTest extends InstrumentationTestCase {
    private SharedPreferences prefs() { return getInstrumentation().getTargetContext().getSharedPreferences("reader",0); }
    @Override protected void setUp() throws Exception { super.setUp(); prefs().edit().clear().commit(); }
    public void testLegacyThemeInitializesBothScopesOnce() {
        AppearanceStore store=new AppearanceStore(getInstrumentation().getTargetContext());
        AppearanceState first=store.load(2);
        assertEquals(2,first.uiTheme); assertEquals(2,first.readerTheme); assertEquals(1,first.bookStyle);
        store.save(first.withReaderTheme(0));
        AppearanceState restored=new AppearanceStore(getInstrumentation().getTargetContext()).load(1);
        assertEquals(2,restored.uiTheme); assertEquals(0,restored.readerTheme);
    }
    public void testInvalidAppearanceDoesNotResetReadingData() throws Exception {
        BookRepository repo=BookRepository.obtain(getInstrumentation().getTargetContext());
        BookRepository.Book book=repo.importBook(new ByteArrayInputStream("migration fixture original text".getBytes("UTF-8")),"migration.txt",null);
        repo.saveProgress(book.id,7); repo.addBookmark(book.id,4,"saved bookmark");
        prefs().edit().putInt("appearance_version",1).putString("ui_theme","bad").putInt("reader_theme",2).putInt("shelf_style",99)
                .putInt("font",21).putInt("brightness",4).putBoolean("info",false).putString("lastBook",book.id).commit();
        AppearanceState state=new AppearanceStore(getInstrumentation().getTargetContext()).load(1);
        assertEquals(0,state.uiTheme); assertEquals(2,state.readerTheme); assertEquals(1,state.bookStyle);
        assertEquals(7,repo.get(book.id).offset); assertEquals(4,repo.bookmarks(book.id).get(0).offset);
        assertEquals(21,prefs().getInt("font",0)); assertEquals(4,prefs().getInt("brightness",0));
        assertFalse(prefs().getBoolean("info",true)); assertEquals(book.id,prefs().getString("lastBook",""));
    }
}

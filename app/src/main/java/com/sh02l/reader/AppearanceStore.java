package com.sh02l.reader;

import android.content.Context;
import android.content.SharedPreferences;
import com.sh02l.reader.core.AppearanceState;

final class AppearanceStore {
    private final SharedPreferences preferences;
    AppearanceStore(Context context) { preferences=context.getSharedPreferences("reader",Context.MODE_PRIVATE); }
    static int integer(SharedPreferences preferences,String key,int fallback) {
        Object value=preferences.getAll().get(key); return value instanceof Integer ? (Integer)value : fallback;
    }
    AppearanceState load(int legacyTheme) {
        if(integer(preferences,"appearance_version",0)!=1) {
            AppearanceState migrated=new AppearanceState(legacyTheme,legacyTheme,AppearanceState.ICON);
            write(migrated).commit(); return migrated;
        }
        return new AppearanceState(integer(preferences,"ui_theme",0),integer(preferences,"reader_theme",0),integer(preferences,"shelf_style",1));
    }
    private SharedPreferences.Editor write(AppearanceState state) {
        return preferences.edit().putInt("ui_theme",state.uiTheme).putInt("reader_theme",state.readerTheme)
                .putInt("shelf_style",state.bookStyle).putInt("appearance_version",1);
    }
    void save(AppearanceState state) { write(state).apply(); }
}

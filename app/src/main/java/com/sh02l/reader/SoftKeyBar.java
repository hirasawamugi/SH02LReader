package com.sh02l.reader;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.*;

import com.sh02l.reader.core.Key;
import com.sh02l.reader.core.Palette;

import java.util.Map;

final class SoftKeyBar extends LinearLayout {
    private final TextView[] labels = new TextView[4];

    SoftKeyBar(Context context) {
        super(context);
        setTag("soft-keys");
        setOrientation(VERTICAL);
        int padding = Math.round(7 * getResources().getDisplayMetrics().density);
        setPadding(0, padding, 0, 0);
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(HORIZONTAL);
            addView(line, new LayoutParams(-1, -2));
            for (int column = 0; column < 2; column++) {
                int i = row * 2 + column;
                TextView label = new TextView(context);
                label.setTextSize(11);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setGravity(column == 0 ? Gravity.START : Gravity.END);
                labels[i] = label;
                line.addView(label, new LayoutParams(0, -2, 1));
            }
        }
    }

    void setHints(Map<Key, String> hints, Palette palette) {
        Key[] keys = {Key.F1, Key.F2, Key.F3, Key.F4};
        for (int i = 0; i < 4; i++) {
            labels[i].setText(hints.get(keys[i]));
            labels[i].setTextColor(palette.secondary);
        }
        setBackgroundColor(palette.background);
    }
}

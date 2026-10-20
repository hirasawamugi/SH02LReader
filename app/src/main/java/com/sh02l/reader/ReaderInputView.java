package com.sh02l.reader;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sh02l.reader.core.Palette;

/** Native text entry: ordinary keys remain owned by EditText and the device IME. */
final class ReaderInputView extends LinearLayout {
    final EditText editor;
    private final TextView error;
    private final Runnable confirm;

    ReaderInputView(
            Context context,
            Palette palette,
            String initial,
            boolean numeric,
            Runnable confirm,
            Runnable cancel) {
        super(context);
        this.confirm = confirm;
        setOrientation(VERTICAL);
        editor = new EditText(context);
        editor.setTag("reader-input");
        editor.setTextColor(palette.foreground);
        editor.setSingleLine(true);
        editor.setTextSize(18);
        editor.setInputType(
                numeric
                        ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        : InputType.TYPE_CLASS_TEXT);
        editor.setFilters(new InputFilter[] {new InputFilter.LengthFilter(256)});
        editor.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editor.setText(initial);
        editor.setSelection(editor.length());
        editor.setOnEditorActionListener(
                (view, action, event) -> {
                    if (action == EditorInfo.IME_ACTION_DONE) {
                        confirm.run();
                        return true;
                    }
                    return false;
                });
        addView(editor, new LayoutParams(-1, -2));
        error = new TextView(context);
        error.setTextColor(palette.secondary);
        error.setTextSize(13);
        addView(error);
        LinearLayout buttons = new LinearLayout(context);
        Button ok = new Button(context);
        ok.setText("确认（F3）");
        ok.setOnClickListener(view -> confirm.run());
        Button no = new Button(context);
        no.setText("取消（F4）");
        no.setOnClickListener(view -> cancel.run());
        buttons.addView(ok, new LayoutParams(0, -2, 1));
        buttons.addView(no, new LayoutParams(0, -2, 1));
        addView(buttons);
    }

    void submit() {
        confirm.run();
    }

    void error(String value) {
        error.setText(value);
    }
}

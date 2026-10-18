package com.sh02l.reader;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.sh02l.reader.core.Action;
import com.sh02l.reader.core.DiagnosticBuffer;
import com.sh02l.reader.core.KeyInput;
import com.sh02l.reader.core.KeyRouter;
import com.sh02l.reader.core.ReaderModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private ReaderModel model;
    private KeyRouter router;
    private final DiagnosticBuffer diagnostics = new DiagnosticBuffer(256);
    private DiagnosticStore store;
    private SharedPreferences preferences;
    private FrameLayout root;
    private final List<TextView> activeRows = new ArrayList<>();
    private TextView titleView, readingView, informationView, noticeView, diagnosticView;
    private ScrollView readingScroll;
    private String structure = "";
    private int lastPage = -1;
    private int rememberedScrollPage = -1, rememberedScrollY;
    private boolean scrollRestorePending;
    private boolean rendering;
    private int background, foreground, secondary, surface;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        preferences = getSharedPreferences("prototype", MODE_PRIVATE);
        String state = savedState == null ? preferences.getString("state", null) : savedState.getString("state");
        model = ReaderModel.restore(state);
        rememberedScrollPage = savedState == null ? preferences.getInt("scrollPage", -1) : savedState.getInt("scrollPage", -1);
        rememberedScrollY = savedState == null ? preferences.getInt("scrollY", 0) : savedState.getInt("scrollY", 0);
        router = new KeyRouter(new KeyRouter.Target() {
            @Override public long contextVersion() { return model.contextVersion(); }
            @Override public Action resolve(com.sh02l.reader.core.Key key) { return model.resolve(key); }
            @Override public void perform(Action action) {
                // The sample remains accessible at large font sizes before M1 pagination exists.
                if (model.top().kind == ReaderModel.Kind.READER && readingScroll != null) {
                    int direction = action == Action.NEXT_PAGE ? 1 : action == Action.PREVIOUS_PAGE ? -1 : 0;
                    if (direction != 0 && readingScroll.canScrollVertically(direction)) {
                        readingScroll.scrollBy(0, direction * Math.max(dp(40), readingScroll.getHeight() - dp(32)));
                        return;
                    }
                }
                model.perform(action);
            }
        });
        File external = getExternalFilesDir("diagnostics");
        store = new DiagnosticStore(external != null ? external : new File(getFilesDir(), "diagnostics"));
        diagnostics.append("session-created sdk=" + Build.VERSION.SDK_INT + " model=" + Build.MODEL);
        root = new FrameLayout(this);
        root.setTag("app-root");
        setContentView(root);
        applyWindowPolicy();
        render();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (router == null) return super.dispatchKeyEvent(event);
        KeyInput input = new KeyInput(event.getAction(), event.getKeyCode(), event.getScanCode(),
                event.getRepeatCount(), event.getDeviceId(), event.getDownTime(), event.getEventTime(), event.isCanceled());
        KeyRouter.Result result = router.dispatch(input);
        diagnostics.record(input, result);
        Log.d("SH02LKeys", diagnostics.tail(1).trim());
        if (result.consumed) {
            handleEffect();
            if (!isFinishing()) render();
            return true;
        }
        updateDiagnostics();
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (model.top().kind == ReaderModel.Kind.HOME) super.onBackPressed();
        else { model.back(); render(); }
    }

    private void handleEffect() {
        switch (model.takeEffect()) {
            case SAVE_LOG:
                model.setNotice("正在保存诊断记录…");
                store.save(diagnosticSnapshot(), (file, error) -> runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    model.setNotice(error == null ? "已保存：" + file.getName() : "保存失败，请检查存储空间");
                    render();
                }));
                break;
            case CLEAR_LOG: diagnostics.clear(); model.setNotice("记录已清空"); break;
            case EXIT: finish(); break;
            default: break;
        }
    }

    private void applyWindowPolicy() {
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (getWindow().getDecorView().getSystemUiVisibility() != flags) getWindow().getDecorView().setSystemUiVisibility(flags);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        float brightness = model.brightness() / 10f;
        if (attributes.screenBrightness != brightness) {
            attributes.screenBrightness = brightness;
            getWindow().setAttributes(attributes);
        }
    }

    private void render() {
        rendering = true;
        try {
            rememberScroll();
            boolean dark = model.theme() == 2;
            background = Color.parseColor(dark ? "#17212B" : model.theme() == 1 ? "#FAFBFD" : "#F4F1E9");
            foreground = Color.parseColor(dark ? "#EDF2F7" : "#202C38");
            secondary = Color.parseColor(dark ? "#ADBCCB" : "#596A79");
            surface = Color.parseColor(dark ? "#243241" : "#FFFFFF");
            String wanted = model.contextVersion() + ":" + model.theme() + ":" + model.fontSize() + ":" + model.infoVisible();
            if (!wanted.equals(structure)) {
                structure = wanted;
                root.removeAllViews(); activeRows.clear();
                titleView = readingView = informationView = noticeView = diagnosticView = null;
                readingScroll = null;
                root.setBackgroundColor(background);
                root.addView(buildContent(model.contentFrame()), new FrameLayout.LayoutParams(-1, -1));
                if (model.top().kind.overlay) buildOverlay();
            }
            List<ReaderModel.Item> items = model.items(model.top());
            for (int i = 0; i < activeRows.size() && i < items.size(); i++) activeRows.get(i).setText(items.get(i).label);
            if (titleView != null) titleView.setText(model.title(model.contentFrame()));
            if (readingView != null) {
                if (!model.sampleText().contentEquals(readingView.getText())) readingView.setText(model.sampleText());
                if (lastPage != model.page()) {
                    if (rememberedScrollPage != model.page()) { rememberedScrollPage = model.page(); rememberedScrollY = 0; }
                    restoreScrollAfterLayout();
                    lastPage = model.page();
                }
            }
            if (informationView != null) {
                informationView.setText("样例 " + (model.page() + 1) + " / 9  ·  " + model.fontSize() + " 号  ·  " + model.themeName());
                informationView.setVisibility(model.infoVisible() ? View.VISIBLE : View.GONE);
            }
            if (noticeView != null) noticeView.setText(model.notice());
            updateDiagnostics();
            restoreFocus();
            applyWindowPolicy();
        } finally { rendering = false; }
    }

    private View buildContent(ReaderModel.Frame frame) {
        LinearLayout column = vertical();
        column.setPadding(dp(16), dp(16), dp(16), dp(8));
        TextView brand = text("SH02L 阅读", 13, secondary);
        brand.setTypeface(null, Typeface.BOLD);
        column.addView(brand);
        titleView = text(model.title(frame), 24, foreground);
        titleView.setTag("screen-title");
        titleView.setPadding(0, dp(8), 0, dp(12));
        titleView.setTypeface(null, Typeface.BOLD);
        column.addView(titleView);
        ScrollView body = new ScrollView(this);
        body.setFillViewport(false);
        body.setFocusable(false);
        body.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        LinearLayout bodyColumn = vertical();
        body.addView(bodyColumn);
        column.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        if (frame.kind == ReaderModel.Kind.READER) {
            readingScroll = body;
            readingScroll.setTag("reading-scroll");
            readingView = text(model.sampleText(), model.fontSize(), foreground);
            readingView.setTag("reading-content");
            readingView.setLineSpacing(dp(5), 1f);
            readingView.setPadding(dp(2), dp(4), dp(2), dp(16));
            readingView.setFocusable(true);
            readingView.setFocusableInTouchMode(true);
            bodyColumn.addView(readingView, new LinearLayout.LayoutParams(-1, -2));
            restoreScrollAfterLayout();
            informationView = text("", 12, secondary);
            informationView.setTag("reading-info");
            column.addView(informationView);
        } else {
            if (frame.kind == ReaderModel.Kind.HOME) {
                bodyColumn.addView(paragraph("按键验证版 · M0\n先试样例，再检查实体按键。此版本尚未提供 TXT 文件导入。"));
            } else if (frame.kind == ReaderModel.Kind.DIAGNOSTICS) {
                bodyColumn.addView(paragraph(Build.MODEL + "  ·  Android " + Build.VERSION.RELEASE
                        + "\n" + getResources().getDisplayMetrics().widthPixels + " × "
                        + getResources().getDisplayMetrics().heightPixels + "  ·  API " + Build.VERSION.SDK_INT));
                diagnosticView = text("", 11, foreground);
                diagnosticView.setTypeface(Typeface.MONOSPACE);
                diagnosticView.setTag("key-events");
                diagnosticView.setPadding(dp(8), dp(8), dp(8), dp(8));
                diagnosticView.setBackgroundColor(surface);
                bodyColumn.addView(diagnosticView, new LinearLayout.LayoutParams(-1, -2));
                bodyColumn.addView(paragraph("记录文件：\n" + store.file().getAbsolutePath()));
            } else if (frame.kind == ReaderModel.Kind.HELP) {
                bodyColumn.addView(paragraph("列表：上下移动，确认打开。\n菜单：F1 / F3 打开，F2 关闭当前层。\n正文：上 / 左上一页，下 / 右 / 确认下一页，F4 返回首页。\n\n1 / 3：上一节 / 下一节\n2：目录　4 / 6：字号\n5：主题　7 / 9：应用亮度\n8：信息栏　0：帮助\n星号：设置　井号：书签\n\n大字号样例超出一屏时，翻页键先浏览当前段剩余内容。"));
            } else if (frame.kind == ReaderModel.Kind.SETTINGS) {
                bodyColumn.addView(paragraph("上下选择项目，左右调整数值。\nF2 或 F4 返回原页面。"));
            }
            addRows(bodyColumn, frame, !model.top().kind.overlay);
        }
        noticeView = text(model.notice(), 12, secondary);
        noticeView.setPadding(0, dp(6), 0, dp(4));
        noticeView.setTag("notice");
        column.addView(noticeView);
        String keys = frame.kind == ReaderModel.Kind.READER ? "F1 菜单   F2 保持   F3 菜单   F4 书架"
                : frame.kind == ReaderModel.Kind.HOME ? "F1 菜单   确认 打开   F3 诊断" : "↑↓ 选择   确认 执行   F2 返回";
        TextView footer = text(keys, 12, secondary);
        footer.setPadding(0, dp(6), 0, dp(4));
        footer.setTag("softkeys");
        column.addView(footer);
        return column;
    }

    private void buildOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setTag("overlay");
        overlay.setBackgroundColor(0x99000000);
        overlay.setClickable(true);
        LinearLayout panel = vertical();
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));
        panel.setBackground(round(surface, 12));
        TextView heading = text(model.title(model.top()), 22, foreground);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, 0, 0, dp(12));
        panel.addView(heading);
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        LinearLayout choices = vertical();
        addRows(choices, model.top(), true);
        scroll.addView(choices);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        panel.addView(text("↑↓ 选择   确认 执行   F2 关闭", 12, secondary));
        int available = getResources().getDisplayMetrics().heightPixels - dp(64);
        int desired = dp(model.items(model.top()).size() * 52 + 100);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, Math.min(available, desired), Gravity.CENTER);
        params.setMargins(dp(16), dp(24), dp(16), dp(24));
        overlay.addView(panel, params);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void addRows(LinearLayout parent, ReaderModel.Frame frame, boolean active) {
        List<ReaderModel.Item> items = model.items(frame);
        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            TextView row = text(items.get(i).label, 17, foreground);
            row.setTag("item:" + items.get(i).id);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(46));
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            StateListDrawable rowBackground = new StateListDrawable();
            rowBackground.addState(new int[]{android.R.attr.state_focused}, round(Color.parseColor("#2464A8"), 6));
            rowBackground.addState(new int[]{}, round(surface, 6));
            row.setBackground(rowBackground);
            row.setFocusable(active);
            row.setFocusableInTouchMode(active);
            row.setOnFocusChangeListener((view, focused) -> {
                row.setTextColor(focused ? Color.WHITE : foreground);
                if (focused && !rendering && active && model.top() == frame) model.select(index);
            });
            if (active) {
                activeRows.add(row);
                row.setOnClickListener(view -> { model.select(index); model.perform(Action.ACTIVATE); handleEffect(); if (!isFinishing()) render(); });
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(6);
            parent.addView(row, params);
        }
    }

    private void restoreFocus() {
        if (!activeRows.isEmpty()) {
            TextView wanted = activeRows.get(Math.min(model.top().selected(), activeRows.size() - 1));
            if (!wanted.hasFocus()) wanted.requestFocus();
        } else if (readingView != null && model.top().kind == ReaderModel.Kind.READER && !readingView.hasFocus()) {
            readingView.requestFocus();
        }
    }

    private void updateDiagnostics() {
        if (diagnosticView != null) diagnosticView.setText("累计事件：" + diagnostics.eventCount() + "\n" + diagnostics.tail(2));
    }
    private void rememberScroll() {
        if (readingScroll != null && !scrollRestorePending && lastPage == model.page()) {
            rememberedScrollPage = lastPage;
            rememberedScrollY = readingScroll.getScrollY();
        }
    }
    private void restoreScrollAfterLayout() {
        final ScrollView target = readingScroll;
        final int page = model.page();
        final int position = rememberedScrollPage == page ? rememberedScrollY : 0;
        scrollRestorePending = true;
        target.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (target.getViewTreeObserver().isAlive()) target.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (target == readingScroll && model.page() == page) {
                    target.scrollTo(0, position);
                    scrollRestorePending = false;
                }
            }
        });
    }
    private String diagnosticSnapshot() {
        return "SH02L Reader " + BuildConfig.VERSION_NAME + "\nmodel=" + Build.MANUFACTURER + "/" + Build.MODEL
                + "\nandroid=" + Build.VERSION.RELEASE + " api=" + Build.VERSION.SDK_INT
                + "\nabis=" + String.join(",", Build.SUPPORTED_ABIS)
                + "\nstate=" + model.save() + "\nevents=" + diagnostics.eventCount() + "\n\n" + diagnostics.tail(256);
    }
    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout;
    }
    private TextView paragraph(String text) {
        TextView view = text(text, 14, secondary);
        view.setLineSpacing(dp(3), 1f); view.setPadding(0, 0, 0, dp(14)); return view;
    }
    private TextView text(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (router == null) return;
        if (!hasFocus) router.reset();
        else { applyWindowPolicy(); restoreFocus(); }
        diagnostics.append("window-focus=" + hasFocus);
    }
    @Override protected void onPause() {
        super.onPause();
        router.reset();
        rememberScroll();
        preferences.edit().putString("state", model.save()).putInt("scrollPage", rememberedScrollPage)
                .putInt("scrollY", rememberedScrollY).apply();
        diagnostics.append("lifecycle=pause");
        store.save(diagnosticSnapshot(), null);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        rememberScroll();
        state.putString("state", model.save()); state.putInt("scrollPage", rememberedScrollPage);
        state.putInt("scrollY", rememberedScrollY); super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { store.close(); super.onDestroy(); }
}

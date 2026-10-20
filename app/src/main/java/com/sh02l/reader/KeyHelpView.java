package com.sh02l.reader;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.View;

import com.sh02l.reader.core.DeviceKeyMap;
import com.sh02l.reader.core.Palette;

import java.util.*;

final class KeyHelpView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint paragraph = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Palette palette = Palette.forTheme(0);
    private boolean reading;
    private String f1 = "书籍菜单", f2 = "导入图书", f3 = "通用菜单", f4 = "返回";
    private DeviceKeyMap profile;
    private HelpLayout layout;
    private StaticLayout caption, notes;
    private float scroll, total;
    private final float density, scaled;

    KeyHelpView(Context context) {
        super(context);
        setTag("key-help");
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDefaultFocusHighlightEnabled(false);
        density = getResources().getDisplayMetrics().density;
        scaled = getResources().getDisplayMetrics().scaledDensity;
        setContentDescription("实体按键说明。上下滚动，0 或 F4 关闭。");
    }

    void setDeviceProfile(DeviceKeyMap profile) {
        this.profile = profile;
        caption = null;
        invalidate();
    }

    void setPalette(Palette palette) {
        this.palette = palette;
        invalidate();
    }

    void setReadingContext(boolean reading) {
        this.reading = reading;
        caption = null;
        invalidate();
    }

    void setFunctionLabels(String f1, String f2, String f3, String f4) {
        this.f1 = f1;
        this.f2 = f2;
        this.f3 = f3;
        this.f4 = f4;
        invalidate();
    }

    void move(int direction) {
        ensureLayout();
        scroll =
                Math.max(
                        0,
                        Math.min(
                                Math.max(0, total - getHeight()),
                                scroll + direction * 48 * density));
        invalidate();
    }

    private StaticLayout text(String value, int width) {
        return StaticLayout.Builder.obtain(value, 0, value.length(), paragraph, Math.max(1, width))
                .setIncludePad(false)
                .setLineSpacing(2 * density, 1)
                .build();
    }

    private void ensureLayout() {
        if (layout != null && caption != null) return;
        paragraph.setTextSize(12 * scaled);
        layout = HelpLayout.measure(getWidth(), density, scaled);
        caption =
                text(
                        (reading ? "正文：方向键翻页 · 确认下一页" : "列表：↑↓ 选择 · 确认打开")
                                + "\n本页：↑↓ 滚动 · 0 或 F4 关闭",
                        getWidth() - Math.round(6 * density));
        notes =
                text(
                        "数字快捷键仅在正文生效，灰字为当前未启用。\n"
                            + "列表首尾循环；正文翻页不循环。\n"
                            + "输入时 F3 确认，F4 取消。\n"
                            + "拨号、清除、挂断与 I / II / III 保留系统含义。\n"
                            + "↑↓ 滚动说明，0 或 F4 关闭。"
                                + (profile != null && !profile.id().startsWith("sh02l")
                                        ? "\n当前使用通用按键映射；示意图供参考。"
                                        : ""),
                        getWidth() - Math.round(6 * density));
        total =
                caption.getHeight()
                        + 10 * density
                        + layout.height
                        + 10 * density
                        + notes.getHeight();
        scroll = Math.min(scroll, Math.max(0, total - getHeight()));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layout = null;
        caption = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() < 1) return;
        ensureLayout();
        canvas.save();
        canvas.translate(0, -scroll);
        paragraph.setColor(palette.secondary);
        caption.draw(canvas);
        canvas.translate(0, caption.getHeight() + 10 * density);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(palette.divider);
        canvas.drawRoundRect(0, 0, getWidth(), layout.height, 16 * density, 16 * density, paint);
        for (HelpLayout.KeyBox key : layout.keys) {
            HelpLayout.Box b = key.bounds;
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(palette.secondary);
            canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, 6 * density, 6 * density, paint);
            boolean function = key.id.startsWith("F"),
                    numeric = key.digitBaseline != key.labelBaseline && !function;
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(palette.foreground);
            paint.setTextSize((numeric ? 16 : 12) * scaled);
            String name = key.id;
            if (function) name = key.id;
            else if (key.id.equals("CALL")) name = "拨号";
            else if (key.id.equals("CLEAR")) name = "清除";
            else if (key.id.equals("END")) name = "挂断";
            canvas.drawText(name, b.centerX(), key.digitBaseline, paint);
            if (function || numeric) {
                paint.setTextSize(11 * scaled);
                String label = label(key.id);
                float available = b.width() - 8 * density;
                if (paint.measureText(label) > available)
                    paint.setTextSize(paint.getTextSize() * available / paint.measureText(label));
                paint.setColor(
                        numeric && !reading && !key.id.equals("0")
                                ? palette.secondary
                                : palette.foreground);
                canvas.drawText(label, b.centerX(), key.labelBaseline, paint);
            }
        }
        drawPad(canvas);
        canvas.translate(0, layout.height + 10 * density);
        paragraph.setColor(palette.secondary);
        notes.draw(canvas);
        canvas.restore();
        if (total > getHeight()) {
            paint.setColor(palette.accent);
            paint.setStyle(Paint.Style.FILL);
            float thumb = Math.max(20 * density, getHeight() * getHeight() / total),
                    y = scroll / (total - getHeight()) * (getHeight() - thumb);
            canvas.drawRoundRect(
                    getWidth() - 2 * density, y, getWidth(), y + thumb, density, density, paint);
        }
    }

    private String label(String id) {
        switch (id) {
            case "F1":
                return f1;
            case "F2":
                return f2;
            case "F3":
                return f3;
            case "F4":
                return f4;
            case "1":
                return "上一章";
            case "2":
                return "目录";
            case "3":
                return "下一章";
            case "4":
                return "字号−";
            case "5":
                return "阅读配色";
            case "6":
                return "字号＋";
            case "7":
                return "亮度−";
            case "8":
                return "信息栏";
            case "9":
                return "亮度＋";
            case "*":
                return "设置";
            case "0":
                return "说明开关";
            case "#":
                return "书签";
            default:
                return "";
        }
    }

    private void drawPad(Canvas canvas) {
        float cx = layout.centerX, cy = layout.centerY, r = layout.padRadius;
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(palette.secondary);
        paint.setStrokeWidth(density);
        canvas.drawCircle(cx, cy, r, paint);
        canvas.drawCircle(cx, cy, r * .40f, paint);
        for (int i = 0; i < 4; i++) {
            canvas.save();
            canvas.rotate(i * 90, cx, cy);
            float y = cy - r * .73f, s = 4 * density;
            canvas.drawLine(cx - s, y + s, cx, y - s, paint);
            canvas.drawLine(cx, y - s, cx + s, y + s, paint);
            canvas.restore();
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(palette.foreground);
        paint.setTextSize(12 * scaled);
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText("确认", cx, cy - (fm.ascent + fm.descent) / 2, paint);
    }
}

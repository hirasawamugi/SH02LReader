package com.sh02l.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import com.sh02l.reader.txt.TxtPaginator;

/** Drawing only: layout and all file access belong to the worker. */
final class PageView extends View {
    interface SizeListener { void changed(int width, int height); }
    private final SizeListener listener;
    private TxtPaginator.Page page;
    private String message = "正在排版…";
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    PageView(Context context, SizeListener listener, int color) {
        super(context); this.listener = listener; hint.setColor(color);
        hint.setTextSize(16 * getResources().getDisplayMetrics().scaledDensity);
        setFocusable(true); setFocusableInTouchMode(true); setDefaultFocusHighlightEnabled(false);
        setTag("reading-content"); setContentDescription("正文");
    }
    void show(TxtPaginator.Page page) {
        if (this.page == page) return;
        this.page = page; message = null; setContentDescription("正文\n" + page.text()); invalidate();
    }
    void message(String value) { page = null; message = value; invalidate(); }
    void setHintColor(int color) { hint.setColor(color); invalidate(); }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width > 0 && height > 0) listener.changed(width, height);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (page == null) { if (message != null) canvas.drawText(message, 0, hint.getTextSize() * 1.5f, hint); return; }
        int saved = canvas.save();
        canvas.clipRect(0, 0, getWidth(), Math.min(getHeight(), page.bottom - page.top));
        canvas.translate(0, -page.top); page.layout.draw(canvas); canvas.restoreToCount(saved);
    }
}

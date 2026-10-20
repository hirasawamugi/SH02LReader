package com.sh02l.reader.txt;

import android.content.Context;
import android.graphics.*;
import android.view.View;

import com.sh02l.reader.android.*;
import com.sh02l.reader.core.*;

import java.util.concurrent.*;

/** Owns TXT layout scheduling, bounded turn queue, paint and stale-result rejection. */
public final class TxtReadingSurface implements ReadingSurface {
    private final CanvasView view;
    private final ThreadPoolExecutor worker =
            new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1));
    private Future<?> active;
    private TxtDocumentSession document;
    private ReadingStyle style;
    private Listener listener;
    private TxtPaginator.Page page;
    private long position, epoch;
    private int pending;
    private boolean busy, closed;

    public TxtReadingSurface(Context context) {
        view = new CanvasView(context);
    }

    public View view() {
        return view;
    }

    public void open(
            DocumentSession document,
            DocumentLocation location,
            ReadingStyle style,
            Listener listener) {
        this.document = (TxtDocumentSession) document;
        this.style = style;
        this.listener = listener;
        position = location.textOffset();
        view.post(this::layout);
    }

    public boolean ready() {
        return page != null && !busy && !closed;
    }

    public String text() {
        return page == null ? "" : page.text();
    }

    public DocumentLocation endLocation() {
        return DocumentLocation.txt(page == null ? position : page.end);
    }

    public void seek(DocumentLocation location) {
        position = location.textOffset();
        pending = 0;
        layout();
    }

    public void apply(ReadingStyle next) {
        if (next.equals(style)) return;
        style = next;
        layout();
    }

    public void move(int direction) {
        if (closed) return;
        pending = Math.max(-32, Math.min(32, pending + direction));
        pump();
    }

    private void layout() {
        if (closed
                || document == null
                || style == null
                || view.getWidth() < 1
                || view.getHeight() < 1) return;
        TxtPaginator.Spec spec =
                new TxtPaginator.Spec(
                        view.getWidth(),
                        view.getHeight(),
                        style.textSize,
                        style.lineExtra,
                        style.foreground,
                        style.typeface);
        final long target = position;
        submit(() -> document.seek(target, spec));
    }

    private interface LayoutWork {
        TxtPaginator.Page run() throws Exception;
    }

    private void submit(LayoutWork work) {
        if (active != null) active.cancel(true);
        worker.getQueue().clear();
        busy = true;
        long token = ++epoch;
        active =
                worker.submit(
                        () -> {
                            try {
                                TxtPaginator.Page result = work.run();
                                view.post(
                                        () -> {
                                            if (closed || epoch != token) return;
                                            page = result;
                                            position = result.start;
                                            busy = false;
                                            view.setContentDescription("正文\n" + result.text());
                                            view.invalidate();
                                            listener.onLocation(
                                                    DocumentLocation.txt(position),
                                                    document.progressionAt(
                                                            DocumentLocation.txt(position)));
                                            listener.onReady();
                                            pump();
                                        });
                            } catch (Exception error) {
                                view.post(
                                        () -> {
                                            if (!closed && epoch == token) {
                                                busy = false;
                                                listener.onError(error);
                                            }
                                        });
                            }
                        });
    }

    private void pump() {
        if (busy || page == null || pending == 0 || closed) return;
        int direction = pending > 0 ? 1 : -1;
        pending -= direction;
        TxtPaginator.Page previous = page;
        submit(() -> document.move(previous, direction));
    }

    public void close() {
        closed = true;
        epoch++;
        pending = 0;
        if (active != null) active.cancel(true);
        worker.shutdownNow();
        page = null;
    }

    private final class CanvasView extends View {
        final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CanvasView(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setDefaultFocusHighlightEnabled(false);
            setTag("reading-content");
            setContentDescription("正文加载中");
            hint.setTextSize(16 * getResources().getDisplayMetrics().scaledDensity);
        }

        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            if (w > 0 && h > 0) TxtReadingSurface.this.layout();
        }

        protected void onDraw(Canvas canvas) {
            if (style != null) {
                canvas.drawColor(style.background);
                hint.setColor(style.foreground);
            }
            if (page == null) {
                canvas.drawText("正在排版…", 0, hint.getTextSize() * 1.5f, hint);
                return;
            }
            int saved = canvas.save();
            canvas.clipRect(0, 0, getWidth(), Math.min(getHeight(), page.bottom - page.top));
            canvas.translate(0, -page.top);
            page.layout.draw(canvas);
            canvas.restoreToCount(saved);
        }
    }
}

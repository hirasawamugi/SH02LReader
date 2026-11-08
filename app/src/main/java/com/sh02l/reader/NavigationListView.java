package com.sh02l.reader;

import android.content.Context;
import android.view.View;
import android.widget.ListView;

/** Keeps visible rows stationary until the selected row crosses a viewport edge. */
final class NavigationListView extends ListView {
    private boolean restoring;
    private final android.graphics.Paint thumb =
            new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private float density;

    NavigationListView(Context context, boolean dark) {
        super(context, null, 0, dark ? R.style.ReaderListDark : R.style.ReaderList);
        density = getResources().getDisplayMetrics().density;
        thumb.setColor(dark ? 0xff93b4ce : 0xff727b7c);
        setVerticalScrollBarEnabled(false);
        setPadding(Math.round(6 * density), 0, Math.round(6 * density), 0);
        setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        setScrollBarSize(Math.round(2 * density));
        setScrollbarFadingEnabled(false);
        setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
    }

    @Override
    protected void dispatchDraw(android.graphics.Canvas canvas) {
        super.dispatchDraw(canvas);
        int range = computeVerticalScrollRange(), extent = computeVerticalScrollExtent();
        if (extent <= 0 || range <= extent) return;
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        float length = Math.max(18 * density, height * extent / range);
        float y =
                getPaddingTop()
                        + Math.max(
                                        0,
                                        Math.min(
                                                1,
                                                computeVerticalScrollOffset()
                                                        / (float) (range - extent)))
                                * (height - length);
        // Draw in the symmetric outer gutter, never over the selected card or its text.
        canvas.drawRoundRect(
                getWidth() - 2 * density, y, getWidth(), y + length, density, density, thumb);
    }

    void restoreSelection(int position, int offset) {
        // Fresh launches can inherit touch mode; the selected row must still be a real native
        // selection.
        if (isInTouchMode()) requestFocusFromTouch();
        restoring = true;
        setSelectionFromTop(position, offset);
    }

    Integer showSelection(int position) {
        if (position < 0 || position >= getCount()) return null;
        if (getChildCount() == 0) {
            if (getSelectedItemPosition() != position) setSelectionFromTop(position, 0);
            return null;
        }
        int index = position - getFirstVisiblePosition();
        View row = index >= 0 && index < getChildCount() ? getChildAt(index) : null;
        int height = row == null ? getChildAt(0).getHeight() : row.getHeight();
        int top = getListPaddingTop(), bottom = getHeight() - getListPaddingBottom();
        int desired = row != null ? row.getTop() : index < 0 ? top : bottom - height;
        desired = Math.max(top, Math.min(desired, bottom - height));
        // Data refresh also runs on key-up and can replace the pending native scroll anchor.
        setSelectionFromTop(position, desired - top);
        return desired - top;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (restoring && getChildCount() > 0) {
            restoring = false;
            showSelection(getSelectedItemPosition());
        }
    }
}

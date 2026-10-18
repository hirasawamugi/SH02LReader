package com.sh02l.reader;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import com.sh02l.reader.core.Palette;

/** Common geometry and focus treatment; no file or database work. */
final class UiStyle {
    final Context context; final Palette palette;
    UiStyle(Context context,Palette palette) { this.context=context;this.palette=palette; }
    int dp(int value) { return Math.round(value*context.getResources().getDisplayMetrics().density); }
    int rowHeight(int minimum) {
        android.graphics.Paint paint=new android.graphics.Paint();paint.setTextSize(17*context.getResources().getDisplayMetrics().scaledDensity);
        android.graphics.Paint.FontMetrics fm=paint.getFontMetrics();return Math.max(dp(minimum),(int)Math.ceil(fm.bottom-fm.top)+dp(28));
    }
    TextView title(String value) {
        TextView view=new TextView(context);view.setText(value);view.setTextSize(20);view.setTextColor(palette.foreground);
        view.setSingleLine(true);view.setEllipsize(TextUtils.TruncateAt.END);view.setPadding(0,0,0,dp(8));return view;
    }
    TextView actionRow(String value,boolean selected) {
        Label view=new Label(context);view.setTextSize(17);view.setTextColor(palette.foreground);
        view.setGravity(Gravity.CENTER_VERTICAL);view.setSingleLine(true);view.setMarqueeRepeatLimit(-1);
        view.setPadding(dp(14),dp(14),dp(14),dp(14));view.setFocusable(false);
        view.setLayoutParams(new AbsListView.LayoutParams(-1,rowHeight(64)));bind(view,value,selected,false);return view;
    }
    void bind(TextView view,String value,boolean selected,boolean active) {
        if(!TextUtils.equals(view.getText(),value))view.setText(value);
        view.setContentDescription(value);view.setTextColor(palette.foreground);
        view.setTypeface(null,android.graphics.Typeface.NORMAL);applySelection(view,selected);
        if(view instanceof Label)((Label)view).active(selected&&active);
    }
    void applySelection(View view,boolean selected) {
        GradientDrawable shape=new GradientDrawable();shape.setColor(selected?blend(palette.selected,palette.accent,4):Color.TRANSPARENT);
        shape.setCornerRadius(dp(12));view.setBackground(shape);
    }
    static int blend(int base,int accent,int percent) {
        int r=(Color.red(base)*(100-percent)+Color.red(accent)*percent)/100;
        int g=(Color.green(base)*(100-percent)+Color.green(accent)*percent)/100;
        int b=(Color.blue(base)*(100-percent)+Color.blue(accent)*percent)/100;return Color.rgb(r,g,b);
    }
    static void pauseMarquees(View view) {
        if(view instanceof Label)((Label)view).active(false);
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)pauseMarquees(((ViewGroup)view).getChildAt(i));
    }
    static final class Label extends TextView {
        private boolean active;
        Label(Context context) { super(context);setSingleLine(true);setMarqueeRepeatLimit(-1); }
        void active(boolean value) {
            active=value;TextUtils.TruncateAt ellipsize=value?TextUtils.TruncateAt.MARQUEE:TextUtils.TruncateAt.END;
            if(getEllipsize()!=ellipsize)setEllipsize(ellipsize);if(super.isSelected()!=value)super.setSelected(value);
        }
        @Override public boolean isSelected() { return active&&super.isSelected(); }
    }
}

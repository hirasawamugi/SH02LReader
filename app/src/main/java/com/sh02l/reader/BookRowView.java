package com.sh02l.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import com.sh02l.reader.core.*;
import java.util.Locale;

/** Equal-height native rows. Only an active selected name is allowed to marquee. */
final class BookRowView extends LinearLayout {
    private final ImageView image;
    private final UiStyle.Label name;
    private final TextView detail;
    private int mode; private boolean importing;
    BookRowView(Context context) {
        super(context);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setFocusable(false);
        image=new ImageView(context);image.setTag("book-cover");addView(image);
        LinearLayout labels=new LinearLayout(context);labels.setOrientation(VERTICAL);
        name=new UiStyle.Label(context);name.setTextSize(17);name.setTag("book-title");name.setFocusable(false);
        detail=new TextView(context);detail.setTextSize(12);detail.setSingleLine(true);detail.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name,new LayoutParams(-1,-2));LayoutParams meta=new LayoutParams(-1,-2);meta.topMargin=dp(4);labels.addView(detail,meta);
        addView(labels,new LayoutParams(0,-2,1));
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private int textHeight(TextView view) { android.graphics.Paint.FontMetrics fm=view.getPaint().getFontMetrics();return (int)Math.ceil(fm.bottom-fm.top); }
    void bind(LibraryAdapter.Item item,int mode,Palette palette,boolean selected) {
        this.mode=mode;importing=item.book==null;UiStyle style=new UiStyle(getContext(),palette);
        String title=importing?"导入 TXT":item.book.title;
        String meta=importing?"手机存储 / 内存卡":(item.continuing?"继续阅读 · ":"已读 ")+String.format(Locale.ROOT,"%.1f%%",item.book.length==0?0:100.0*item.offset/item.book.length);
        if(!TextUtils.equals(name.getText(),title))name.setText(title);detail.setText(meta);
        name.setTextColor(palette.foreground);detail.setTextColor(palette.secondary);
        setContentDescription(title+"，"+meta);setTag(importing?"item:import":"item:book");
        setPadding(dp(14),dp(14),dp(14),dp(14));
        image.setVisibility(mode==AppearanceState.TEXT?GONE:VISIBLE);
        LayoutParams icon=new LayoutParams(dp(mode==AppearanceState.COVER?56:20),dp(mode==AppearanceState.COVER?84:20));icon.rightMargin=dp(10);image.setLayoutParams(icon);
        image.setImageResource(importing?android.R.drawable.ic_input_add:mode==AppearanceState.COVER?R.drawable.cover_default:R.drawable.ic_book_line);
        image.setImageTintList(android.content.res.ColorStateList.valueOf(palette.accent));image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setBackgroundColor(mode==AppearanceState.COVER?palette.surface:android.graphics.Color.TRANSPARENT);
        int height=Math.max(dp(mode==AppearanceState.COVER?84:52),textHeight(name)+textHeight(detail)+dp(4))+dp(28);
        setMinimumHeight(height);setLayoutParams(new AbsListView.LayoutParams(-1,height));style.applySelection(this,selected);name.active(false);
    }
    void setActive(boolean active) { name.active(active); }
    void setCover(Bitmap bitmap) {
        if(mode!=AppearanceState.COVER||importing||bitmap==null)return;
        image.setImageTintList(null);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(bitmap);
    }
}

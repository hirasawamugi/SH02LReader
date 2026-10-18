package com.sh02l.reader;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.*;
import com.sh02l.reader.core.Palette;

final class SoftKeyBar extends LinearLayout {
    private final TextView[] labels=new TextView[3];
    SoftKeyBar(Context context) {
        super(context);setTag("soft-keys");setOrientation(HORIZONTAL);
        int padding=Math.round(7*getResources().getDisplayMetrics().density);setPadding(0,padding,0,0);
        for(int i=0;i<3;i++){TextView label=new TextView(context);label.setTextSize(12);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(i==0?Gravity.START:i==1?Gravity.CENTER:Gravity.END);labels[i]=label;addView(label,new LayoutParams(0,-2,1));}
    }
    void setHints(String left,String center,String right,Palette palette) {
        String[] values={left,center,right};for(int i=0;i<3;i++){labels[i].setText(values[i]);labels[i].setTextColor(palette.secondary);}setBackgroundColor(palette.background);
    }
}

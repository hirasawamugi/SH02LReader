package com.sh02l.reader;

import android.content.Context;
import android.view.View;
import android.widget.ListView;

/** Keeps visible rows stationary until the selected row crosses a viewport edge. */
final class NavigationListView extends ListView {
    private boolean restoring;

    NavigationListView(Context context,boolean dark) {
        super(context,null,0,dark?R.style.ReaderListDark:R.style.ReaderList);
        float density=getResources().getDisplayMetrics().density;
        setPadding(0,0,Math.round(12*density),0);
        setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
    }

    void restoreSelection(int position,int offset) {
        restoring=true;setSelectionFromTop(position,offset);
    }

    Integer showSelection(int position) {
        if(position<0||position>=getCount())return null;
        if(getChildCount()==0){if(getSelectedItemPosition()!=position)setSelectionFromTop(position,0);return null;}
        int index=position-getFirstVisiblePosition();
        View row=index>=0&&index<getChildCount()?getChildAt(index):null;
        int height=row==null?getChildAt(0).getHeight():row.getHeight();
        int top=getListPaddingTop(),bottom=getHeight()-getListPaddingBottom();
        int desired=row!=null?row.getTop():index<0?top:bottom-height;
        desired=Math.max(top,Math.min(desired,bottom-height));
        // Data refresh also runs on key-up and can replace the pending native scroll anchor.
        setSelectionFromTop(position,desired-top);
        return desired-top;
    }

    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        super.onLayout(changed,left,top,right,bottom);
        if(restoring&&getChildCount()>0){restoring=false;showSelection(getSelectedItemPosition());}
    }
}

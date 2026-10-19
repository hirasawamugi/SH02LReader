package com.sh02l.reader;

import android.view.*;
import android.widget.ListView;
import java.io.*;
import java.lang.reflect.Method;
import java.util.UUID;

/** Verifies the viewport as well as the selected action, using production key routing. */
public final class ListNavigationTest extends NativeTestBase {
    private static final class Frame {
        int first,firstTop,selected,selectedTop,selectedBottom,bottom,lastFull,count,rightGap,barWidth;
    }
    private Frame frame() {
        await(()->selected()!=null,5000);Frame value=new Frame();
        ui(()->{
            ListView list=(ListView)find("list:"+activity.screenKind());View row=list.getSelectedView();
            value.first=list.getFirstVisiblePosition();value.firstTop=list.getChildAt(0).getTop();
            value.selected=list.getSelectedItemPosition();value.selectedTop=row.getTop();value.selectedBottom=row.getBottom();
            value.bottom=list.getHeight()-list.getListPaddingBottom();value.count=list.getCount();
            value.rightGap=list.getWidth()-row.getRight();value.barWidth=list.getVerticalScrollbarWidth();
            value.lastFull=-1;
            for(int i=0;i<list.getChildCount();i++)if(list.getChildAt(i).getBottom()<=value.bottom)value.lastFull=value.first+i;
        });return value;
    }
    private void sameViewport(Frame before,Frame after) {
        assertEquals("Visible first row must remain fixed",before.first,after.first);
        assertEquals("Visible first row must keep its pixel offset",before.firstTop,after.firstTop);
    }
    private void longContents()throws Exception {
        File source=new File(activity.getCacheDir(),"list-navigation.txt");
        try(Writer out=new OutputStreamWriter(new FileOutputStream(source),"UTF-8")){
            for(int chapter=1;chapter<=24;chapter++){out.write("第"+chapter+"章 导航验证\n");for(int line=0;line<8;line++)out.write("章节内容，用于验证选中项及列表位置。\n");}
        }
        ui(()->activity.importFile(source));await(this::ready,15000);press(KeyEvent.KEYCODE_2);
        await(()->"CONTENTS".equals(kind())&&listCount()>=24,5000);
    }
    public void testVisibleSelectionMovesWithoutScrolling()throws Exception {
        longContents();Frame start=frame();press(KeyEvent.KEYCODE_DPAD_UP);sameViewport(start,frame());
        press(KeyEvent.KEYCODE_DPAD_DOWN);Frame next=frame();
        assertEquals(start.selected+1,next.selected);sameViewport(start,next);
        assertTrue("Focus must move down inside the page",next.selectedTop>start.selectedTop);
    }
    public void testEdgesScrollMinimallyAndReverseMovesInsidePage()throws Exception {
        longContents();Frame initial=frame();
        for(int i=initial.selected;i<initial.lastFull;i++)press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_DOWN);Frame edge=frame();
        assertEquals("The new selection must be fully visible at the lower edge",edge.bottom,edge.selectedBottom);
        assertTrue("Only crossing the visible edge should scroll",edge.first>initial.first||edge.firstTop<initial.firstTop);
        press(KeyEvent.KEYCODE_DPAD_DOWN);Frame scrolling=frame();assertEquals(edge.selectedBottom,scrolling.selectedBottom);
        press(KeyEvent.KEYCODE_DPAD_UP);Frame reversed=frame();sameViewport(scrolling,reversed);
        assertTrue("Reversing direction should move focus into the page",reversed.selectedTop<scrolling.selectedTop);
        for(int i=reversed.selected;i<reversed.count-1;i++)press(KeyEvent.KEYCODE_DPAD_DOWN);
        Frame end=frame();press(KeyEvent.KEYCODE_DPAD_DOWN);sameViewport(end,frame());assertEquals(end.selected,frame().selected);
    }
    public void testHelpReturnRestoresTheScrolledContentsViewport()throws Exception {
        longContents();for(int i=0;i<12;i++)press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_UP);press(KeyEvent.KEYCODE_DPAD_UP);Frame before=frame();
        assertTrue("Fixture must place the selection inside the page",before.selectedTop>before.firstTop);
        press(KeyEvent.KEYCODE_0);press(KeyEvent.KEYCODE_0);Frame after=frame();
        assertEquals(before.selected,after.selected);assertEquals(before.selectedTop,after.selectedTop);sameViewport(before,after);
    }
    public void testHelpReturnKeepsASelectionAwaitingLayout()throws Exception {
        longContents();Frame before=frame();
        ui(()->{
            for(int code:new int[]{KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_0}){
                long time=android.os.SystemClock.uptimeMillis();
                activity.dispatchKeyEvent(new KeyEvent(time,time,KeyEvent.ACTION_DOWN,code,0));
                activity.dispatchKeyEvent(new KeyEvent(time,time+1,KeyEvent.ACTION_UP,code,0));
            }
        });
        await(()->"HELP".equals(kind()),5000);press(KeyEvent.KEYCODE_0);Frame after=frame();
        assertEquals(before.selected+1,after.selected);sameViewport(before,after);
        assertTrue("The new focus must remain below the first row",after.selectedTop>before.selectedTop);
    }
    public void testParentFolderRestoresItsViewport()throws Exception {
        File root=new File(activity.getExternalFilesDir("import"),"navigation-"+UUID.randomUUID());assertTrue(root.mkdirs());
        File[] children=new File[16];
        try {
            for(int i=0;i<children.length;i++){children[i]=new File(root,String.format(java.util.Locale.ROOT,"folder-%02d",i));assertTrue(children[i].mkdir());}
            press(KeyEvent.KEYCODE_F3);
            ui(()->{try {Method files=MainActivity.class.getDeclaredMethod("files",File.class);files.setAccessible(true);files.invoke(activity,root);}
                catch(ReflectiveOperationException error){throw new AssertionError(error);}});
            await(()->"FILES".equals(kind())&&listCount()==17&&selected()!=null,5000);
            for(int i=0;i<11;i++)press(KeyEvent.KEYCODE_DPAD_DOWN);
            press(KeyEvent.KEYCODE_DPAD_UP);press(KeyEvent.KEYCODE_DPAD_UP);Frame before=frame();
            press(KeyEvent.KEYCODE_DPAD_CENTER);await(()->listCount()==1&&selected()!=null,5000);
            press(KeyEvent.KEYCODE_F2);await(()->listCount()==17&&selected()!=null,5000);Frame after=frame();
            assertEquals(before.selected,after.selected);assertEquals(before.selectedTop,after.selectedTop);sameViewport(before,after);
        } finally {for(File child:children)if(child!=null)child.delete();root.delete();}
    }
    public void testLibraryStylesKeepVisibleRowsStationary()throws Exception {
        for(int i=0;i<8;i++)BookRepository.obtain(activity).importBook(new ByteArrayInputStream(("Navigation shelf seed "+i).getBytes("UTF-8")),"Navigation "+i+".txt",null);
        press(KeyEvent.KEYCODE_F3);press(KeyEvent.KEYCODE_F2);awaitLibrary();
        for(int style=0;style<3;style++){
            press(KeyEvent.KEYCODE_F1);select("settings");press(KeyEvent.KEYCODE_DPAD_CENTER);
            select("appearance");press(KeyEvent.KEYCODE_DPAD_CENTER);select("shelf-style");
            int current=activity.getSharedPreferences("reader",0).getInt("shelf_style",1);
            for(int step=0;step<(style-current+3)%3;step++)press(KeyEvent.KEYCODE_DPAD_RIGHT);
            for(int level=0;level<4&&!"LIBRARY".equals(kind());level++)press(KeyEvent.KEYCODE_F2);
            while(frame().selected>0)press(KeyEvent.KEYCODE_DPAD_UP);
            Frame before=frame();press(KeyEvent.KEYCODE_DPAD_DOWN);Frame after=frame();
            assertEquals(1,after.selected);sameViewport(before,after);assertTrue(after.selectedTop>before.selectedTop);
        }
    }
    public void testScrollbarHasSpaceBesideTheSelectedRow()throws Exception {
        longContents();Frame current=frame();
        int gap=Math.round(6*activity.getResources().getDisplayMetrics().density);
        assertTrue("The scroll thumb must have a separate gutter beside the selection",current.rightGap>=current.barWidth+gap);
    }
}

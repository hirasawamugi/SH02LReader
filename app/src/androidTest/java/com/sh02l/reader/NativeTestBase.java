package com.sh02l.reader;

import android.content.Intent;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.*;
import android.widget.ListView;
import java.io.*;

@SuppressWarnings("deprecation")
abstract class NativeTestBase extends InstrumentationTestCase {
    MainActivity activity;
    interface Condition { boolean value(); }
    void ui(Runnable action) { if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())action.run();else getInstrumentation().runOnMainSync(action); }
    @Override protected void setUp() throws Exception {
        super.setUp();
        getInstrumentation().getTargetContext().getSharedPreferences("reader",0).edit().clear().commit();
        getInstrumentation().getTargetContext().getSharedPreferences("prototype",0).edit().clear().commit();
        Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity=(MainActivity)getInstrumentation().startActivitySync(intent);
        ui(()->activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        await(()->find("list:LIBRARY")!=null && listCount()>0,15000);
    }
    @Override protected void tearDown() throws Exception { ui(()->activity.finish()); getInstrumentation().waitForIdleSync(); super.tearDown(); }
    View find(String tag) { final View[] result={null}; ui(()->result[0]=activity.getWindow().getDecorView().findViewWithTag(tag)); return result[0]; }
    String kind() { final String[] value={null}; ui(()->value[0]=activity.screenKind()); return value[0]; }
    int listCount() { final int[] value={0}; ui(()->{ListView list=activity.getWindow().getDecorView().findViewWithTag("list:"+activity.screenKind()); if(list!=null)value[0]=list.getCount();}); return value[0]; }
    String selected() {
        final String[] value={null}; ui(()->{ListView list=activity.getWindow().getDecorView().findViewWithTag("list:"+activity.screenKind());View row=list==null?null:list.getSelectedView();if(row!=null)value[0]=String.valueOf(row.getTag());});return value[0];
    }
    void await(Condition condition,long timeout) { long end=SystemClock.uptimeMillis()+timeout;while(!condition.value()&&SystemClock.uptimeMillis()<end)SystemClock.sleep(30);assertTrue(getName()+" timed out, screen="+kind(),condition.value()); }
    void press(int code) { getInstrumentation().sendKeyDownUpSync(code);getInstrumentation().waitForIdleSync(); if(listCount()>0)await(()->selected()!=null,3000); }
    void select(String id) { for(int i=0;i<50;i++){if(("item:"+id).equals(selected()))return;press(KeyEvent.KEYCODE_DPAD_DOWN);}fail("No selectable item: "+id); }
    void fixture() throws Exception {
        File file=new File(activity.getCacheDir(),"appearance-fixture.txt");
        try(Writer out=new OutputStreamWriter(new FileOutputStream(file),"UTF-8")){out.write("第一章 外观验证\n");for(int i=0;i<400;i++)out.write("正文位置、中文、English 与标点。第"+i+"段。\n");}
        ui(()->activity.importFile(file));await(this::ready,15000);
    }
    boolean ready(){final boolean[] value={false};ui(()->value[0]=activity.readingReady());return value[0];}
    long offset(){final long[] value={-1};ui(()->value[0]=activity.readingOffset());return value[0];}
}

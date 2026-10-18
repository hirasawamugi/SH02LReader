package com.sh02l.reader;

import android.app.*;
import android.content.*;
import android.view.KeyEvent;
import android.widget.TextView;
import java.io.*;

public final class OriginalExportFlowTest extends NativeTestBase {
    public void testStaleExportResultDoesNotInterruptANewImport()throws Exception {
        prepare();activity.getContentResolver().call(ImportTestProvider.uri("stale-export"),"seed","stale-export",null);
        File next=new File(activity.getCacheDir(),"after-export.txt");try(OutputStream out=new FileOutputStream(next)){out.write(("after export "+System.nanoTime()).getBytes("UTF-8"));}
        Intent result=new Intent().setData(ImportTestProvider.uri("stale-export")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor(){
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(!Intent.ACTION_CREATE_DOCUMENT.equals(intent.getAction()))return null;
                activity.perform(com.sh02l.reader.core.Action.HOME);activity.importFile(next);return new Instrumentation.ActivityResult(Activity.RESULT_OK,result);
            }
        };
        getInstrumentation().addMonitor(monitor);
        try{press(KeyEvent.KEYCODE_F1);select("export-text");press(KeyEvent.KEYCODE_DPAD_CENTER);await(()->monitor.getHits()>0,5000);await(this::ready,10000);
            assertEquals(CoverFixtures.hash(CoverFixtures.bytes(next)),activity.getSharedPreferences("reader",0).getString("lastBook",""));assertEquals("provider fixture\n",new String(read("stale-export"),"UTF-8"));
        }finally{getInstrumentation().removeMonitor(monitor);}
    }
    private String notice(){final String[] result={""};ui(()->{TextView view=(TextView)find("notice");if(view!=null)result[0]=view.getText().toString();});return result[0];}
    private void export(String destination,int resultCode)throws Exception{
        activity.getContentResolver().call(ImportTestProvider.uri(destination),"seed",destination,null);
        IntentFilter filter=new IntentFilter(Intent.ACTION_CREATE_DOCUMENT);filter.addCategory(Intent.CATEGORY_OPENABLE);filter.addDataType("*/*");
        Intent result=new Intent().setData(ImportTestProvider.uri(destination)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(filter,new Instrumentation.ActivityResult(resultCode,result),true);
        try{press(KeyEvent.KEYCODE_F1);select("export-text");press(KeyEvent.KEYCODE_DPAD_CENTER);await(()->monitor.getHits()>0,5000);
            if(resultCode==Activity.RESULT_CANCELED)await(()->notice().contains("已取消导出"),5000);else await(()->notice().contains("导出完成")||notice().contains("导出失败"),10000);
        }finally{getInstrumentation().removeMonitor(monitor);}
    }
    private byte[] read(String id)throws IOException{try(InputStream in=activity.getContentResolver().openInputStream(ImportTestProvider.uri(id));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    private File prepare()throws Exception{fixture();File source=new File(activity.getCacheDir(),"appearance-fixture.txt");press(KeyEvent.KEYCODE_F4);await(()->selected()!=null,10000);return source;}
    public void testDocumentExportContainsTheOriginalBytes()throws Exception{
        File source=prepare();String expected=CoverFixtures.hash(CoverFixtures.bytes(source));export("destination",Activity.RESULT_OK);
        assertTrue(notice(),notice().contains("导出完成"));assertEquals(expected,CoverFixtures.hash(read("destination")));assertEquals("LIBRARY",kind());
    }
    public void testFailedDocumentExportRemovesOnlyItsOwnTarget()throws Exception{
        File source=prepare();String expected=CoverFixtures.hash(CoverFixtures.bytes(source));export("write-failed",Activity.RESULT_OK);
        assertTrue(notice(),notice().contains("导出失败"));assertTrue(activity.getContentResolver().call(ImportTestProvider.uri("write-failed"),"attempts","write-failed",null).getInt("count")>0);
        assertEquals(expected,CoverFixtures.hash(CoverFixtures.bytes(source)));assertEquals("MENU",kind());assertEquals("item:export-text",selected());
    }
    public void testCancelledDocumentExportLeavesTheTargetUntouched()throws Exception{
        prepare();export("cancelled-export",Activity.RESULT_CANCELED);assertEquals("provider fixture\n",new String(read("cancelled-export"),"UTF-8"));assertEquals(0,activity.getContentResolver().call(ImportTestProvider.uri("cancelled-export"),"attempts","cancelled-export",null).getInt("count"));assertEquals("MENU",kind());
    }
}

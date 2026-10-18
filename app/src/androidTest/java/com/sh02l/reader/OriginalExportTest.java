package com.sh02l.reader;
import android.test.InstrumentationTestCase;
import java.io.*;
@SuppressWarnings("deprecation")
public final class OriginalExportTest extends InstrumentationTestCase {
    public void testExportKeepsOriginalBytes()throws Exception{
        File source=new File(getInstrumentation().getTargetContext().getCacheDir(),"export-source.bin");byte[] bytes={(byte)0xff,(byte)0xfe,65,0,13,0,10,0};try(OutputStream out=new FileOutputStream(source)){out.write(bytes);}
        final boolean[] closed={false};ByteArrayOutputStream target=new ByteArrayOutputStream(){public void close()throws IOException{closed[0]=true;super.close();}};
        OriginalExporter.copy(source,target,null);assertEquals(CoverFixtures.hash(bytes),CoverFixtures.hash(target.toByteArray()));assertTrue(closed[0]);assertEquals(CoverFixtures.hash(bytes),CoverFixtures.hash(CoverFixtures.bytes(source)));
    }
    public void testFailedExportClosesTheDestinationAndKeepsOriginal()throws Exception{
        File source=new File(getInstrumentation().getTargetContext().getCacheDir(),"export-failed.bin");try(OutputStream out=new FileOutputStream(source)){out.write(new byte[100]);}
        final boolean[] closed={false};OutputStream target=new OutputStream(){public void write(int value)throws IOException{throw new IOException("target full");}public void close(){closed[0]=true;}};
        try{OriginalExporter.copy(source,target,null);fail("Write failure ignored");}catch(IOException expected){}assertTrue(closed[0]);assertEquals(100,source.length());
    }
}

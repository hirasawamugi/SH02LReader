package com.sh02l.reader;

import android.graphics.*;
import android.media.ExifInterface;
import android.test.InstrumentationTestCase;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("deprecation")
public final class CoverRenderingTest extends InstrumentationTestCase {
    public void testExifAndWideImageUseCenteredTwoByThree() throws Exception {
        File file=new File(getInstrumentation().getTargetContext().getCacheDir(),"cover-orientation.jpg");
        Bitmap source=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(source);Paint p=new Paint();
        p.setColor(Color.RED);canvas.drawRect(0,0,40,80,p);p.setColor(Color.GREEN);canvas.drawRect(40,0,80,80,p);p.setColor(Color.BLUE);canvas.drawRect(80,0,120,80,p);
        try(OutputStream out=new FileOutputStream(file)){source.compress(Bitmap.CompressFormat.JPEG,100,out);}source.recycle();
        ExifInterface exif=new ExifInterface(file.getAbsolutePath());exif.setAttribute(ExifInterface.TAG_ORIENTATION,"6");exif.saveAttributes();
        Bitmap decoded=CoverDecoder.decode(file,56,84);assertNotNull("Cover must decode",decoded);assertEquals(56,decoded.getWidth());assertEquals(84,decoded.getHeight());
        assertTrue(Color.red(decoded.getPixel(28,5))>200);assertTrue(Color.blue(decoded.getPixel(28,78))>200);decoded.recycle();
        exif=new ExifInterface(file.getAbsolutePath());exif.setAttribute(ExifInterface.TAG_ORIENTATION,"2");exif.saveAttributes();
        decoded=CoverDecoder.decode(file,56,84);assertTrue(Color.blue(decoded.getPixel(5,42))>200);assertTrue(Color.red(decoded.getPixel(50,42))>200);decoded.recycle();assertTrue(file.delete());
    }
    public void testRequestedOutputAndLargeSourceRespectWorkingBudget() throws Exception {
        File file=new File(getInstrumentation().getTargetContext().getCacheDir(),"large-cover.png");try(OutputStream out=new FileOutputStream(file)){out.write(CoverFixtures.png(3000,1800));}
        Bitmap decoded=CoverDecoder.decode(file,84,126);assertNotNull(decoded);assertTrue(decoded.getAllocationByteCount()<=4*1024*1024);assertEquals(Color.GREEN,decoded.getPixel(42,63));decoded.recycle();
        try {CoverDecoder.decode(file,2000,3000);fail("Output over working bitmap budget accepted");}catch(IOException expected){}assertTrue(file.delete());
    }
    public void testLateThumbnailCannotBindAnotherBook() throws Exception {
        CoverStore store=new CoverStore(getInstrumentation().getTargetContext());String a=CoverFixtures.id(),b=CoverFixtures.id();byte[] image=CoverFixtures.png(80,120);
        store.importCover(a,new ByteArrayInputStream(image),"a.png",null);store.importCover(b,new ByteArrayInputStream(image),"b.png",null);
        CoverLoader loader=new CoverLoader(store);CountDownLatch done=new CountDownLatch(1);List<String> delivered=Collections.synchronizedList(new ArrayList<>());
        getInstrumentation().runOnMainSync(()->{loader.retain(new HashSet<>(Arrays.asList(a,b)));loader.load(a,56,84,1,(id,revision,token,bitmap)->delivered.add(id+":"+token));loader.load(b,56,84,2,(id,revision,token,bitmap)->{delivered.add(id+":"+token);done.countDown();});loader.retain(Collections.singleton(b));});
        try {assertTrue("Current book thumbnail never arrived",done.await(5,TimeUnit.SECONDS));assertEquals(Collections.singletonList(b+":2"),delivered);}finally{loader.close();}
    }
}

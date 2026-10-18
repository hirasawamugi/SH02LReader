package com.sh02l.reader;

import android.test.InstrumentationTestCase;
import java.io.*;

@SuppressWarnings("deprecation")
public final class CoverStoreTest extends InstrumentationTestCase {
    public void testFailedReplacementKeepsOriginal() throws Exception {
        CoverStore store=new CoverStore(getInstrumentation().getTargetContext());String id=CoverFixtures.id();byte[] bytes=CoverFixtures.png(80,120);
        CoverStore.Record first=store.importCover(id,new ByteArrayInputStream(bytes),"original.png",null);assertNotNull("Imported image must be durably recorded",first);
        try {store.importCover(id,new ByteArrayInputStream(new byte[]{1,2,3}),"fake.jpg",null);fail("Damaged replacement accepted");}catch(IOException expected){}
        CoverStore.Record restored=new CoverStore(getInstrumentation().getTargetContext()).get(id);assertNotNull(restored);
        assertEquals(first.revision,restored.revision);assertEquals(CoverFixtures.hash(bytes),CoverFixtures.hash(CoverFixtures.bytes(restored.original)));
        assertEquals("image/png",restored.mime);
    }
    public void testOversizedAndUnknownLengthImagesAreBounded() throws Exception {
        CoverStore store=new CoverStore(getInstrumentation().getTargetContext());String id=CoverFixtures.id();
        try {store.importCover(id,new InputStream(){long count;public int read(){return count++<32L*1024*1024+1?0:-1;}public int read(byte[] b,int off,int len){if(count>=32L*1024*1024+1)return -1;int n=(int)Math.min(len,32L*1024*1024+1-count);java.util.Arrays.fill(b,off,off+n,(byte)0);count+=n;return n;}},"oversize.png",null);fail("Source over 32 MiB must be rejected");}catch(IOException expected){}
        assertNull(store.get(id));
    }
}

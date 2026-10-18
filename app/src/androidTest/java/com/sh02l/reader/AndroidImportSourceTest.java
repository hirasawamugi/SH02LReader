package com.sh02l.reader;

import android.content.*;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import com.sh02l.reader.core.importing.*;
import java.io.*;

@SuppressWarnings("deprecation")
public final class AndroidImportSourceTest extends InstrumentationTestCase {
    private ContentResolver resolver(){return getInstrumentation().getTargetContext().getContentResolver();}
    private AndroidImportSource source(String id,int flags){resolver().call(ImportTestProvider.uri(id),"seed",id,null);return AndroidImportSource.fromUri(resolver(),ImportTestProvider.uri(id),flags);}
    private ImportCoordinator coordinator(){return new ImportCoordinator(new SpaceGuard(()->1024L*1024*1024,8*1024*1024));}
    private ImportCoordinator.CommittedAsset copy(ImportSource source)throws IOException{
        File destination=File.createTempFile("source-copy-",".bin",getInstrumentation().getTargetContext().getCacheDir());
        try(InputStream in=source.open();OutputStream out=new FileOutputStream(destination)){byte[] buffer=new byte[1024];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
        return new ImportCoordinator.CommittedAsset("copy",destination,ImportCoordinator.sha256(destination));
    }
    private static final int READ=Intent.FLAG_GRANT_READ_URI_PERMISSION,WRITE=Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    public void testRevokedProviderGrantKeepsSourceAndReportsIoFailure()throws Exception{
        AndroidImportSource source=source("denied",READ|WRITE);boolean ioFailure=false;
        try{coordinator().run(source,ImportCoordinator.Mode.MOVE,(s,o)->copy(s),null);}catch(IOException expected){ioFailure=true;}catch(SecurityException unnormalized){}
        assertTrue("Revoked grant must be reported as a source I/O failure",ioFailure);assertEquals(0,resolver().call(ImportTestProvider.uri("denied"),"attempts","denied",null).getInt("count"));assertTrue(source.snapshot().size>0);
    }
    public void testReadOnlyProviderCannotMove()throws Exception{
        AndroidImportSource source=source("read-only",READ|WRITE);source.snapshot();assertFalse(source.canDelete());
        final File[] copied={null};ImportCoordinator.Outcome result=coordinator().run(source,ImportCoordinator.Mode.MOVE,(s,o)->{ImportCoordinator.CommittedAsset asset=copy(s);copied[0]=asset.original;return asset;},null);
        assertEquals(ImportCoordinator.Status.COPIED_SOURCE_RETAINED,result.status);assertEquals("provider fixture\n",new String(CoverFixtures.bytes(copied[0]),"UTF-8"));assertNotNull(resolver().openInputStream(ImportTestProvider.uri("read-only")));
    }
    public void testReadGrantDoesNotImplyDeleteAuthority()throws Exception{
        AndroidImportSource source=source("move",READ);source.snapshot();assertFalse(source.canDelete());try(InputStream in=source.open()){assertEquals('p',in.read());}
    }
    public void testDeleteFailureReportsRetainedSource()throws Exception{
        AndroidImportSource source=source("delete-failed",READ|WRITE);ImportCoordinator.Outcome result=coordinator().run(source,ImportCoordinator.Mode.MOVE,(s,o)->copy(s),null);
        assertEquals(ImportCoordinator.Status.COPIED_SOURCE_RETAINED,result.status);assertTrue(resolver().call(ImportTestProvider.uri("delete-failed"),"attempts","delete-failed",null).getInt("count")>0);
    }
    public void testChangedProviderKeepsSource()throws Exception{
        AndroidImportSource source=source("changed",READ|WRITE);ImportCoordinator.Outcome result=coordinator().run(source,ImportCoordinator.Mode.MOVE,(s,o)->copy(s),null);
        assertEquals(ImportCoordinator.Status.COPIED_SOURCE_RETAINED,result.status);try(InputStream in=source.open()){assertEquals('p',in.read());}
    }
    public void testUnknownSizeIsCopiedAndNeverDeleted()throws Exception{
        AndroidImportSource source=source("unknown",READ|WRITE);assertEquals(-1,source.snapshot().size);assertFalse(source.canDelete());
        final File[] copied={null};coordinator().run(source,ImportCoordinator.Mode.MOVE,(s,o)->{ImportCoordinator.CommittedAsset asset=copy(s);copied[0]=asset.original;return asset;},null);
        assertEquals("provider fixture\n",new String(CoverFixtures.bytes(copied[0]),"UTF-8"));assertEquals(0,resolver().call(ImportTestProvider.uri("unknown"),"attempts","unknown",null).getInt("count"));
    }
    public void testMoveFileRemovesOnlySelectedSourceAfterCopy()throws Exception{
        File file=File.createTempFile("file-source-",".txt",getInstrumentation().getTargetContext().getCacheDir());try(OutputStream out=new FileOutputStream(file)){out.write("file fixture".getBytes("UTF-8"));}
        ImportCoordinator.Outcome result=coordinator().run(AndroidImportSource.fromFile(file),ImportCoordinator.Mode.MOVE,(s,o)->copy(s),null);assertEquals(ImportCoordinator.Status.MOVED,result.status);assertFalse(file.exists());
    }
}

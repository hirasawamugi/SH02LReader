package com.sh02l.reader.core;
import com.sh02l.reader.core.importing.*;
import com.sh02l.reader.core.text.TextImporter;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;
public class ImportCoordinatorTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static final String ABC="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private final class Source implements ImportSource {
        final File file;boolean deleteFailure;int version,deletes,opens;
        Source()throws Exception{file=temporary.newFile();Files.write(file.toPath(),"abc".getBytes("UTF-8"));}
        public Snapshot snapshot(){return new Snapshot(file.getAbsolutePath(),file.length(),String.valueOf(version));}
        public InputStream open()throws IOException{opens++;return new FileInputStream(file);}
        public boolean canDelete(){return true;}
        public boolean isSameFile(File other)throws IOException{return file.getCanonicalFile().equals(other.getCanonicalFile());}
        public boolean deleteIfUnchanged(Snapshot expected,String sha)throws IOException{deletes++;if(deleteFailure)throw new IOException("provider deletion failed");return expected.same(snapshot())&&file.delete();}
    }
    private ImportCoordinator coordinator(){return new ImportCoordinator(new SpaceGuard(()->1024L*1024*1024,8*1024*1024));}
    private ImportCoordinator.CommittedAsset copy(ImportSource source)throws IOException{
        File destination=temporary.newFile();try(InputStream input=source.open();OutputStream out=new FileOutputStream(destination)){byte[] buffer=new byte[16];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);}
        return new ImportCoordinator.CommittedAsset("book",destination,ABC);
    }
    @Test public void copyNeverDeletesSource()throws Exception{
        Source source=new Source();ImportCoordinator.Outcome result=coordinator().run(source,ImportCoordinator.Mode.COPY,(s,o)->copy(s),null);
        assertTrue(source.file.exists());assertEquals(0,source.deletes);assertEquals("book",result.assetId);assertEquals(ImportCoordinator.Status.COPIED,result.status);assertTrue(source.opens>0);
    }
    @Test public void moveDeletesOnlyAfterVerifiedCommit()throws Exception{
        Source source=new Source();final File[] committed={null};ImportCoordinator.Outcome result=coordinator().run(source,ImportCoordinator.Mode.MOVE,(s,o)->{ImportCoordinator.CommittedAsset asset=copy(s);committed[0]=asset.original;return asset;},null);
        assertNotNull(committed[0]);assertTrue(committed[0].isFile());assertArrayEquals("abc".getBytes("UTF-8"),Files.readAllBytes(committed[0].toPath()));assertFalse(source.file.exists());assertEquals(ImportCoordinator.Status.MOVED,result.status);
    }
    @Test public void cancelOrCommitFailureKeepsSource()throws Exception{
        Source source=new Source();TextImporter.Observer cancelled=new TextImporter.Observer(){public void progress(String phase,long bytes){}public boolean cancelled(){return true;}};
        try{coordinator().run(source,ImportCoordinator.Mode.MOVE,(s,o)->copy(s),cancelled);fail("Cancellation ignored");}catch(InterruptedIOException expected){}
        try{coordinator().run(source,ImportCoordinator.Mode.MOVE,(s,o)->{throw new IOException("commit failed");},null);fail("Commit failure ignored");}catch(IOException expected){}
        assertTrue(source.file.exists());assertEquals(0,source.deletes);
    }
    @Test public void changedOrCorruptDuplicateKeepsSource()throws Exception{
        Source source=new Source();ImportCoordinator.Outcome result=coordinator().run(source,ImportCoordinator.Mode.MOVE,(s,o)->{ImportCoordinator.CommittedAsset asset=copy(s);source.version++;return asset;},null);
        assertTrue(source.file.exists());assertEquals(ImportCoordinator.Status.COPIED_SOURCE_RETAINED,result.status);
        try{coordinator().run(source,ImportCoordinator.Mode.MOVE,(s,o)->{ImportCoordinator.CommittedAsset asset=copy(s);Files.write(asset.original.toPath(),"xyz".getBytes("UTF-8"));return asset;},null);fail("Corrupt private duplicate accepted");}catch(IOException expected){}
        assertTrue(source.file.exists());
    }
    @Test public void deleteFailureReportsRetainedSource()throws Exception{
        Source source=new Source();source.deleteFailure=true;ImportCoordinator.Outcome result=coordinator().run(source,ImportCoordinator.Mode.MOVE,(s,o)->copy(s),null);
        assertTrue(source.file.exists());assertEquals(ImportCoordinator.Status.COPIED_SOURCE_RETAINED,result.status);assertFalse(result.reason.isEmpty());
    }
    @Test public void lateCancelAfterCommitKeepsCompletedAsset()throws Exception{
        Source source=new Source();final boolean[] cancelled={false};TextImporter.Observer observer=new TextImporter.Observer(){public void progress(String phase,long bytes){}public boolean cancelled(){return cancelled[0];}};
        final File[] committed={null};ImportCoordinator.Outcome result=coordinator().run(source,ImportCoordinator.Mode.MOVE,(s,o)->{ImportCoordinator.CommittedAsset asset=copy(s);committed[0]=asset.original;cancelled[0]=true;return asset;},observer);
        assertNotNull(committed[0]);assertTrue(committed[0].isFile());assertTrue(source.file.exists());assertEquals(ImportCoordinator.Status.COPIED_SOURCE_RETAINED,result.status);
    }
    @Test public void selectedPrivateOriginalCannotBeDeleted()throws Exception{
        Source source=new Source();ImportCoordinator.Outcome result=coordinator().run(source,ImportCoordinator.Mode.MOVE,(s,o)->new ImportCoordinator.CommittedAsset("book",source.file,ABC),null);
        assertTrue(source.file.exists());assertEquals(0,source.deletes);assertEquals(ImportCoordinator.Status.COPIED_SOURCE_RETAINED,result.status);
    }
}

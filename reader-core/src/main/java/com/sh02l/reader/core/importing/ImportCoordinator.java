package com.sh02l.reader.core.importing;
import com.sh02l.reader.core.text.TextImporter;
import java.io.*;
import java.security.*;
import java.util.Locale;
public final class ImportCoordinator {
    public enum Mode{COPY,MOVE}
    public enum Status{COPIED,MOVED,COPIED_SOURCE_RETAINED}
    public static final class CommittedAsset {
        public final String assetId,sha256;public final File original;
        public CommittedAsset(String assetId,File original,String sha256){this.assetId=assetId;this.original=original;this.sha256=sha256;}
    }
    public static final class Outcome {
        public final String assetId,reason;public final Status status;
        public Outcome(String assetId,Status status,String reason){this.assetId=assetId;this.status=status;this.reason=reason;}
    }
    public interface Committer {
        CommittedAsset commit(ImportSource source,TextImporter.Observer observer)throws IOException;
        default long peakBytes(long knownSize)throws IOException{try{return knownSize<0?0:Math.multiplyExact(knownSize,4);}catch(ArithmeticException overflow){throw new IOException("Import budget overflow",overflow);}}
    }
    private final SpaceGuard guard;
    public ImportCoordinator(SpaceGuard guard){this.guard=guard;}
    public Outcome run(ImportSource source,Mode mode,Committer committer,TextImporter.Observer observer)throws IOException{
        if(mode==null)throw new IOException("Import mode missing");TextImporter.check(observer);ImportSource.Snapshot expected=source.snapshot();guard.require(committer.peakBytes(expected.size));
        TextImporter.Observer guarded=new TextImporter.Observer(){
            public boolean cancelled(){return observer!=null&&observer.cancelled();}
            public void progress(String phase,long bytes){if(observer!=null)observer.progress(phase,bytes);}
            public void beforeWrite(long bytes)throws IOException{TextImporter.check(this);guard.beforeWrite(bytes);if(observer!=null)observer.beforeWrite(bytes);}
        };
        CommittedAsset asset=committer.commit(source,guarded);
        if(asset==null||asset.assetId==null||asset.assetId.isEmpty()||asset.original==null||!asset.original.isFile()||asset.sha256==null||!asset.sha256.matches("[0-9a-f]{64}"))throw new IOException("No complete private asset");
        if(Thread.currentThread().isInterrupted()||(observer!=null&&observer.cancelled()))return new Outcome(asset.assetId,mode==Mode.COPY?Status.COPIED:Status.COPIED_SOURCE_RETAINED,"cancelled");
        if(!asset.sha256.equals(sha256(asset.original)))throw new IOException("Committed original failed verification");
        if(mode==Mode.COPY)return new Outcome(asset.assetId,Status.COPIED,"");
        try{
            if(Thread.currentThread().isInterrupted()||(observer!=null&&observer.cancelled()))return retained(asset,"cancelled");
            if(source.isSameFile(asset.original))return retained(asset,"private-target");
            if(!source.canDelete())return retained(asset,"read-only");
            if(!expected.same(source.snapshot()))return retained(asset,"source-changed");
            if(source.deleteIfUnchanged(expected,asset.sha256))return new Outcome(asset.assetId,Status.MOVED,"");
            return retained(asset,"source-retained");
        }catch(IOException|RuntimeException failure){return retained(asset,"delete-failed");}
    }
    private static Outcome retained(CommittedAsset asset,String reason){return new Outcome(asset.assetId,Status.COPIED_SOURCE_RETAINED,reason);}
    public static String sha256(File file)throws IOException{
        MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[16384];int n;while((n=input.read(buffer))!=-1)digest.update(buffer,0,n);}
        StringBuilder hex=new StringBuilder(64);for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();
    }
}

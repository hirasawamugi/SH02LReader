package com.sh02l.reader;
import android.content.Context;
import android.util.AtomicFile;
import android.graphics.Bitmap;
import com.sh02l.reader.core.text.TextImporter;
import java.io.*;
import java.security.*;
import java.util.*;
import java.nio.file.*;
final class CoverStore {
    private static final Object LOCK=new Object();
    private final File root;
    static final class Record {
        final String bookId,revision,name,mime,sha256;final File original;
        Record(String bookId,String revision,File original,String name,String mime,String sha256){this.bookId=bookId;this.revision=revision;this.original=original;this.name=name;this.mime=mime;this.sha256=sha256;}
    }
    CoverStore(Context context){root=new File(context.getFilesDir(),"covers");}
    private File directory(String id)throws IOException {if(id==null||!id.matches("[0-9a-f]{64}"))throw new IOException("Invalid cover target");return new File(root,id);}
    void cleanupInterruptedImports()throws IOException{
        synchronized(LOCK){
            if(!root.isDirectory())return;
            // Stream directory entries; never read image contents or follow directory symlinks.
            try(DirectoryStream<Path> books=Files.newDirectoryStream(root.toPath())){
                for(Path entry:books){TextImporter.check(null);File book=entry.toFile();
                    if(!book.getName().matches("[0-9a-f]{64}")||!Files.isDirectory(entry,LinkOption.NOFOLLOW_LINKS)||!book.getCanonicalFile().getParentFile().equals(root.getCanonicalFile()))continue;
                    String current=null;boolean known=!new File(book,"current.bin").exists()&&!new File(book,"current.bin.bak").exists();
                    if(!known)try{current=currentRevision(book);known=true;}catch(IOException ignored){/* Preserve revisions when a damaged pointer cannot establish ownership. */}
                    try(DirectoryStream<Path> revisions=Files.newDirectoryStream(entry)){
                        for(Path revision:revisions){TextImporter.check(null);File folder=revision.toFile();String name=folder.getName();
                            if(Files.isDirectory(revision,LinkOption.NOFOLLOW_LINKS)&&(name.startsWith(".staging-")||(known&&name.matches("[0-9a-f-]{36}")&&!name.equals(current))))removeOwned(book,folder);
                        }
                    }
                }
            }
        }
    }
    private String currentRevision(File book)throws IOException{
        // openRead restores the durable .bak before any unreferenced revision is reclaimed.
        try(DataInputStream in=new DataInputStream(new AtomicFile(new File(book,"current.bin")).openRead())){
            String revision=in.readUTF();if(!revision.matches("[0-9a-f-]{36}")||in.read()!=-1)throw new IOException("Invalid cover pointer");return revision;
        }
    }
    Record importCover(String bookId,InputStream source,String name,TextImporter.Observer observer)throws IOException{
        synchronized(LOCK){
            File book=directory(bookId),staging=new File(book,".staging-"+UUID.randomUUID()),published=null;
            try(InputStream input=source){
                if(!staging.mkdirs())throw new IOException("Cannot create cover staging");
                File original=new File(staging,"original.bin");MessageDigest digest=digest();long copied=0;
                try(FileOutputStream out=new FileOutputStream(original)){
                    byte[] buffer=new byte[16384];int n;while((n=input.read(buffer))!=-1){TextImporter.check(observer);if(copied+n>32L*1024*1024)throw new IOException("Cover exceeds 32 MiB");
                        if(root.getUsableSpace()<n+8L*1024*1024)throw new IOException("Not enough cover space");if(observer!=null)observer.beforeWrite(n);out.write(buffer,0,n);digest.update(buffer,0,n);copied+=n;if(observer!=null)observer.progress("copy",copied);}out.getFD().sync();
                }
                CoverDecoder.Info info=CoverDecoder.inspect(original);Bitmap probe=CoverDecoder.decode(original,84,126);probe.recycle();TextImporter.check(observer);
                String revision=UUID.randomUUID().toString(),sha=hex(digest.digest()),safeName=name==null?"cover":name.replaceAll("[\\p{Cntrl}]"," ");safeName=safeName.substring(0,Math.min(512,safeName.length()));
                if(observer!=null)observer.beforeWrite(4096);
                try(FileOutputStream file=new FileOutputStream(new File(staging,"record.bin"))){DataOutputStream out=new DataOutputStream(file);out.writeUTF(safeName);out.writeUTF(info.mime);out.writeUTF(sha);out.flush();file.getFD().sync();}
                DurableFiles.syncDirectory(staging);
                published=new File(book,revision);if(!staging.renameTo(published))throw new IOException("Cannot publish cover revision");
                DurableFiles.syncDirectory(book);if(observer!=null)observer.beforeWrite(4096);
                AtomicFile pointer=new AtomicFile(new File(book,"current.bin"));FileOutputStream file=null;
                File committedDirectory=published;
                try{TextImporter.check(observer);file=pointer.startWrite();DataOutputStream out=new DataOutputStream(file);out.writeUTF(revision);out.flush();file.getFD().sync();pointer.finishWrite(file);published=null;}catch(IOException|RuntimeException error){if(file!=null)pointer.failWrite(file);throw error;}
                DurableFiles.syncDirectory(book);DurableFiles.syncDirectory(root);
                Record record=new Record(bookId,revision,new File(committedDirectory,"original.bin"),safeName,info.mime,sha);
                File[] old=book.listFiles();if(old!=null)for(File path:old)if(path.isDirectory()&&!path.getName().equals(revision)&&(path.getName().startsWith(".staging-")||path.getName().matches("[0-9a-f-]{36}")))try{removeOwned(book,path);}catch(IOException ignored){/* Valid committed cover stays usable; retry old revision cleanup on the next import. */}
                return record;
            }finally{removeOwned(book,staging);if(published!=null)removeOwned(book,published);}
        }
    }
    Record get(String bookId)throws IOException{
        synchronized(LOCK){File book=directory(bookId);
            try{
                String revision=currentRevision(book);File folder=new File(book,revision),original=new File(folder,"original.bin");
                if(!original.isFile()||original.length()>32L*1024*1024)return null;
                try(DataInputStream data=new DataInputStream(new FileInputStream(new File(folder,"record.bin")))){
                    String name=data.readUTF(),mime=data.readUTF(),sha=data.readUTF();if(!sha.matches("[0-9a-f]{64}")||data.read()!=-1)return null;return new Record(bookId,revision,original,name,mime,sha);
                }
            }catch(IOException|RuntimeException error){return null;}
        }
    }
    static String hash(File file)throws IOException{MessageDigest digest=digest();try(InputStream in=new FileInputStream(file)){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();digest.update(b,0,n);}}return hex(digest.digest());}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private void removeOwned(File book,File folder)throws IOException{
        if(!folder.exists())return;if(!book.getCanonicalFile().getParentFile().equals(root.getCanonicalFile())||!folder.getCanonicalFile().getParentFile().equals(book.getCanonicalFile())||!(folder.getName().startsWith(".staging-")||folder.getName().matches("[0-9a-f-]{36}")))throw new IOException("Unsafe cover cleanup");
        File[] files=folder.listFiles();if(files!=null)for(File file:files)if(file.isFile())file.delete();folder.delete();
    }
}

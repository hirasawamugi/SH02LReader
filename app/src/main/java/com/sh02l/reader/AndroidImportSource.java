package com.sh02l.reader;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.*;
import android.system.*;
import com.sh02l.reader.core.importing.ImportSource;
import com.sh02l.reader.core.importing.ImportCoordinator;
import com.sh02l.reader.core.text.TextImporter;
import java.io.*;
import java.nio.file.Files;
import java.security.*;
import java.util.Locale;
final class AndroidImportSource implements ImportSource {
    private final File file;private final ContentResolver resolver;private final Uri uri;private final int grants;
    private String name="文档.txt",documentId="";private long modified,size=-1;private int flags;
    private AndroidImportSource(File file,ContentResolver resolver,Uri uri,int grants){this.file=file;this.resolver=resolver;this.uri=uri;this.grants=grants;if(file!=null)name=file.getName();}
    static AndroidImportSource fromFile(File file){return new AndroidImportSource(file,null,null,0);}
    static AndroidImportSource fromUri(ContentResolver resolver,Uri uri,int flags){if(uri==null||!"content".equals(uri.getScheme()))throw new IllegalArgumentException("Document URI required");return new AndroidImportSource(null,resolver,uri,flags);}
    String displayName(){return name;}
    public Snapshot snapshot()throws IOException{
        if(file!=null)try{
            File canonical=file.getCanonicalFile();if(!canonical.isFile()||!canonical.canRead())throw new IOException("Source unreadable");StructStat stat=Os.stat(canonical.getAbsolutePath());
            return new Snapshot("file:"+canonical.getPath(),stat.st_size,stat.st_dev+":"+stat.st_ino+":"+canonical.lastModified()+":"+stat.st_ctime);
        }catch(ErrnoException error){throw new IOException("Source identity unavailable",error);}
        flags=0;modified=0;size=-1;documentId="";
        try{documentId=DocumentsContract.getDocumentId(uri);}catch(IllegalArgumentException ignored){}
        try{query(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED,DocumentsContract.Document.COLUMN_FLAGS});}
        catch(RuntimeException unsupported){try{query(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});}catch(RuntimeException unavailable){flags=0;modified=0;size=-1;}}
        String identity=documentId.isEmpty()?uri.normalizeScheme().toString():uri.getAuthority()+":"+documentId;
        return new Snapshot(identity,size,modified>0&&size>=0?modified+":"+size:"");
    }
    private void query(String[] columns){
        try(Cursor cursor=resolver.query(uri,columns,null,null,null)){
            if(cursor==null||!cursor.moveToFirst())return;int n=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=cursor.getColumnIndex(OpenableColumns.SIZE),m=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED),f=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS);
            if(n>=0&&!cursor.isNull(n))name=cursor.getString(n);if(s>=0&&!cursor.isNull(s))size=cursor.getLong(s);if(m>=0&&!cursor.isNull(m))modified=cursor.getLong(m);if(f>=0&&!cursor.isNull(f))flags=cursor.getInt(f);
        }
    }
    public InputStream open()throws IOException{
        try{if(file!=null)return new FileInputStream(file);InputStream input=resolver.openInputStream(uri);if(input==null)throw new IOException("Source unavailable");return input;}
        catch(SecurityException denied){throw new IOException("Source permission unavailable",denied);}
    }
    public boolean canDelete(){
        if(file!=null)return file.isFile()&&file.canWrite()&&file.getParentFile()!=null&&file.getParentFile().canWrite()&&!Files.isSymbolicLink(file.toPath())&&file.lastModified()>0;
        return (grants&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0&&!documentId.isEmpty()&&modified>0&&size>=0&&(flags&DocumentsContract.Document.FLAG_SUPPORTS_DELETE)!=0;
    }
    public boolean isSameFile(File other)throws IOException{return file!=null&&Files.isSameFile(file.toPath(),other.toPath());}
    public boolean deleteIfUnchanged(Snapshot expected,String sha256)throws IOException{
        if(!expected.same(snapshot())||!canDelete())return false;String actual;
        if(file!=null)actual=ImportCoordinator.sha256(file);else{
            MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
            try(InputStream input=open()){byte[] buffer=new byte[16384];int n;while((n=input.read(buffer))!=-1){TextImporter.check(null);digest.update(buffer,0,n);}}
            StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));actual=hex.toString();
        }
        TextImporter.check(null);if(!sha256.equals(actual)||!expected.same(snapshot())||!canDelete())return false;
        try{return file!=null?file.delete():DocumentsContract.deleteDocument(resolver,uri);}catch(RuntimeException error){throw new IOException("Source could not be deleted",error);}
    }
}

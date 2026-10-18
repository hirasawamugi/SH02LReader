package com.sh02l.reader;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.*;
import java.util.*;

/** Test APK only. Every document is generated here, never a user file. */
public final class ImportTestProvider extends DocumentsProvider {
    static final String AUTHORITY="com.sh02l.reader.debug.test.imports";
    private final Map<String,Long> versions=new HashMap<>();private final Map<String,Integer> attempts=new HashMap<>();
    @Override public void attachInfo(Context context,ProviderInfo info){
        // Exercise the real DocumentsProvider dispatcher without granting a system signature
        // permission to the app under test. Test-only entry points enforce its exact UID below.
        ProviderInfo registration=new ProviderInfo(info);registration.readPermission="android.permission.MANAGE_DOCUMENTS";registration.writePermission=registration.readPermission;
        super.attachInfo(context,registration);setReadPermission(null);setWritePermission(null);
    }
    private void checkFixtureCaller(){try{int uid=Binder.getCallingUid();if(uid!=android.os.Process.myUid()&&uid!=getContext().getPackageManager().getPackageUid("com.sh02l.reader.debug",0))throw new SecurityException("Test fixture caller only");}catch(android.content.pm.PackageManager.NameNotFoundException e){throw new SecurityException(e);}}
    @Override public boolean onCreate(){return true;}
    private File file(String id)throws FileNotFoundException{if(!id.matches("[a-z-]+"))throw new FileNotFoundException("invalid fixture");return new File(getContext().getCacheDir(),"provider-"+id);}
    @Override public Bundle call(String method,String arg,Bundle extras){
        checkFixtureCaller();
        if("seed".equals(method))try{byte[] bytes="image".equals(arg)?CoverFixtures.png(80,120):"provider fixture\n".getBytes("UTF-8");try(OutputStream out=new FileOutputStream(file(arg))){out.write(bytes);}versions.put(arg,1000L);attempts.put(arg,0);return new Bundle();}catch(IOException error){throw new IllegalStateException(error);}
        if("attempts".equals(method)){Bundle out=new Bundle();out.putInt("count",attempts.getOrDefault(arg,0));return out;}
        return super.call(method,arg,extras);
    }
    @Override public Cursor queryRoots(String[] projection){return new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE});}
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String order){return new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID});}
    @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException{
        checkFixtureCaller();
        File file=file(id);if(!file.isFile())throw new FileNotFoundException("fixture removed");
        String[] columns=projection==null?new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED,DocumentsContract.Document.COLUMN_FLAGS,DocumentsContract.Document.COLUMN_MIME_TYPE}:projection;
        MatrixCursor cursor=new MatrixCursor(columns);MatrixCursor.RowBuilder row=cursor.newRow();for(String column:columns){
            Object value=null;
            if(DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(column))value=id;
            else if(DocumentsContract.Document.COLUMN_DISPLAY_NAME.equals(column))value="image".equals(id)?"cover.png":"provider.txt";
            else if(DocumentsContract.Document.COLUMN_SIZE.equals(column))value="unknown".equals(id)?null:file.length();
            else if(DocumentsContract.Document.COLUMN_LAST_MODIFIED.equals(column))value=versions.getOrDefault(id,1000L);
            else if(DocumentsContract.Document.COLUMN_FLAGS.equals(column))value="read-only".equals(id)?0:DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
            else if(DocumentsContract.Document.COLUMN_MIME_TYPE.equals(column))value="image".equals(id)?"image/png":"text/plain";
            row.add(value);
        }return cursor;
    }
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
        checkFixtureCaller();
        if("denied".equals(id))throw new SecurityException("fixture grant revoked");
        if(mode.contains("w"))try{
            if("write-failed".equals(id)){ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();pipe[0].close();return pipe[1];}
            return ParcelFileDescriptor.open(file(id),ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_CREATE);
        }catch(IOException error){throw new FileNotFoundException(error.getMessage());}
        if("changed".equals(id))versions.put(id,versions.getOrDefault(id,1000L)+1);return ParcelFileDescriptor.open(file(id),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public void deleteDocument(String id)throws FileNotFoundException{
        checkFixtureCaller();
        attempts.put(id,attempts.getOrDefault(id,0)+1);if("delete-failed".equals(id)||"read-only".equals(id))throw new FileNotFoundException("fixture does not allow delete");if(!file(id).delete())throw new FileNotFoundException("delete failed");
    }
    static Uri uri(String id){return DocumentsContract.buildDocumentUri(AUTHORITY,id);}
}

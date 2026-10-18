package com.sh02l.reader;
import com.sh02l.reader.core.text.TextImporter;
import com.sh02l.reader.core.importing.ImportCoordinator;
import java.io.*;
import java.security.*;
import java.util.Locale;
final class OriginalExporter {
    static void copy(File source,OutputStream destination,TextImporter.Observer observer)throws IOException{
        if(destination==null)throw new IOException("Export destination unavailable");
        try(OutputStream out=destination){
            String expected=ImportCoordinator.sha256(source);MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
            try(InputStream input=new FileInputStream(source)){byte[] buffer=new byte[16384];int n;long copied=0;while((n=input.read(buffer))!=-1){TextImporter.check(observer);if(observer!=null)observer.beforeWrite(n);out.write(buffer,0,n);digest.update(buffer,0,n);copied+=n;if(observer!=null)observer.progress("export",copied);}}
            StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(Locale.ROOT,"%02x",b&255));if(!expected.equals(actual.toString()))throw new IOException("Original changed while exporting");out.flush();
        }
    }
}

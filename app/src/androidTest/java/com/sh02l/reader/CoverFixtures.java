package com.sh02l.reader;

import android.graphics.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

final class CoverFixtures {
    static byte[] png(int width,int height) {
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.GREEN);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();return bytes.toByteArray();
    }
    static String id() throws Exception { return hash(UUID.randomUUID().toString().getBytes("UTF-8")); }
    static String hash(byte[] bytes) throws Exception {StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    static byte[] bytes(File file) throws IOException {try(InputStream input=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}}
}

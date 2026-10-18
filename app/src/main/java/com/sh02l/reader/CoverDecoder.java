package com.sh02l.reader;
import android.graphics.*;
import android.media.ExifInterface;
import java.io.*;

/** Sample once, then orient and crop directly into the final bitmap. */
final class CoverDecoder {
    private static final long BUDGET=4L*1024*1024;
    static final class Info {
        final int width,height,orientation;final String mime;
        Info(int width,int height,int orientation,String mime){this.width=width;this.height=height;this.orientation=orientation;this.mime=mime;}
    }
    static Info inspect(File source)throws IOException {
        if(source.length()==0||source.length()>32L*1024*1024)throw new IOException("Invalid cover size");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getAbsolutePath(),options);
        String mime=options.outMimeType;
        if(!("image/jpeg".equals(mime)||"image/png".equals(mime)||"image/webp".equals(mime))||options.outWidth<=0||options.outHeight<=0||options.outWidth>1000000||options.outHeight>1000000)throw new IOException("Unsupported cover image");
        int orientation=1;
        try{orientation=new ExifInterface(source.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){/* Container fallback below covers API 27 PNG/WebP. */}
        int container=containerOrientation(source,mime);if(container>0)orientation=container;
        return new Info(options.outWidth,options.outHeight,orientation>=1&&orientation<=8?orientation:1,mime);
    }
    private static long little32(RandomAccessFile input)throws IOException{return Integer.reverseBytes(input.readInt())&0xffffffffL;}
    private static int containerOrientation(File source,String mime)throws IOException {
        if("image/jpeg".equals(mime))return 0;
        try(RandomAccessFile in=new RandomAccessFile(source,"r")){
            boolean png="image/png".equals(mime);long end=in.length();
            if(png)in.seek(8);else{in.seek(4);long riffEnd=little32(in)+8;if(riffEnd>end||riffEnd<12)throw new IOException("Truncated WebP");end=riffEnd;in.seek(12);}
            int orientation=0;
            while(in.getFilePointer()+8<=end){
                long length;int type;
                if(png){length=in.readInt()&0xffffffffL;type=in.readInt();}else{type=in.readInt();length=little32(in);}
                long start=in.getFilePointer(),next=start+length+(png?4:length&1);
                if(next<start||next>end)throw new IOException("Invalid image chunk");
                if(type==0x6163544c||type==0x414e494d||type==0x414e4d46)throw new IOException("Animated covers are not supported");
                if(!png&&type==0x56503858&&length>=1&&(in.readUnsignedByte()&2)!=0)throw new IOException("Animated WebP");
                if(type==0x65584966||type==0x45584946)orientation=tiffOrientation(in,start,length);
                in.seek(next);if(png&&type==0x49454e44)break;
            }
            return orientation;
        }
    }
    private static int tiffOrientation(RandomAccessFile in,long start,long length)throws IOException {
        if(length<8)return 0;in.seek(start);if(in.readInt()==0x45786966&&length>=14){start+=6;length-=6;}
        in.seek(start);int order=in.readUnsignedShort();boolean little=order==0x4949;if(!little&&order!=0x4d4d)return 0;
        int magic=in.readUnsignedShort();if((little?Short.toUnsignedInt(Short.reverseBytes((short)magic)):magic)!=42)return 0;
        long offset=little?little32(in):in.readInt()&0xffffffffL;if(offset<8||offset>length-2)return 0;in.seek(start+offset);
        int entries=in.readUnsignedShort();if(little)entries=Short.toUnsignedInt(Short.reverseBytes((short)entries));
        if(entries>(length-offset-2)/12)return 0;
        for(int i=0;i<entries;i++){
            int tag=in.readUnsignedShort(),type=in.readUnsignedShort();if(little){tag=Short.toUnsignedInt(Short.reverseBytes((short)tag));type=Short.toUnsignedInt(Short.reverseBytes((short)type));}
            long count=little?little32(in):in.readInt()&0xffffffffL;int value=in.readUnsignedShort();in.skipBytes(2);
            if(tag==0x112&&type==3&&count==1){if(little)value=Short.toUnsignedInt(Short.reverseBytes((short)value));return value>=1&&value<=8?value:0;}
        }return 0;
    }
    static Bitmap decode(File source,int width,int height)throws IOException {
        if(width<1||height<1||(long)width*height*4>BUDGET/2)throw new IOException("Cover output exceeds bitmap budget");
        Info info=inspect(source);long outputBytes=(long)width*height*4;int sample=1;
        boolean swap=info.orientation>=5;int orientedWidth=swap?info.height:info.width,orientedHeight=swap?info.width:info.height;
        while((long)((info.width+sample-1)/sample)*((info.height+sample-1)/sample)*4+outputBytes>BUDGET
                ||(orientedWidth/(sample*2)>=width&&orientedHeight/(sample*2)>=height)){
            if(sample>1<<28)throw new IOException("Invalid sampling dimensions");sample*=2;
        }
        Bitmap bitmap=null,result=null;
        try{
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            bitmap=BitmapFactory.decodeFile(source.getAbsolutePath(),options);if(bitmap==null)throw new IOException("Image decode failed");
            if(bitmap.getAllocationByteCount()+outputBytes>BUDGET)throw new IOException("Image working budget exceeded");
            float[][] rotations={{1,0,0,1},{-1,0,0,1},{-1,0,0,-1},{1,0,0,-1},{0,1,1,0},{0,-1,1,0},{0,-1,-1,0},{0,1,-1,0}};
            float[] r=rotations[info.orientation-1];Matrix matrix=new Matrix();matrix.setValues(new float[]{r[0],r[1],0,r[2],r[3],0,0,0,1});
            RectF rect=new RectF(0,0,bitmap.getWidth(),bitmap.getHeight());matrix.mapRect(rect);matrix.postTranslate(-rect.left,-rect.top);
            float scale=Math.max(width/rect.width(),height/rect.height());matrix.postScale(scale,scale);matrix.postTranslate((width-rect.width()*scale)/2,(height-rect.height()*scale)/2);
            result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(result);canvas.drawBitmap(bitmap,matrix,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));return result;
        }catch(OutOfMemoryError error){if(result!=null)result.recycle();throw new IOException("Not enough memory for cover",error);}
        finally{if(bitmap!=null)bitmap.recycle();}
    }
}

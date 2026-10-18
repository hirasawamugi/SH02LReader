package com.sh02l.reader;
import android.graphics.Bitmap;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
final class CoverLoader implements AutoCloseable {
    interface Callback{void ready(String bookId,String revision,long bindToken,Bitmap bitmap);}
    private static final int CACHE_BYTES=2*1024*1024;
    private static final class Request {
        final String id;final int width,height;final long token;final Callback callback;
        Request(String id,int width,int height,long token,Callback callback){this.id=id;this.width=width;this.height=height;this.token=token;this.callback=callback;}
    }
    private final CoverStore store;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String,Request> pending=new LinkedHashMap<>(),latest=new LinkedHashMap<>();
    private final LinkedHashMap<String,Bitmap> cache=new LinkedHashMap<>(16,.75f,true);
    private Set<String> retained=new HashSet<>();private int bytes;private boolean running,closed;
    CoverLoader(CoverStore store){this.store=store;}
    synchronized void load(String bookId,int width,int height,long bindToken,Callback callback){
        if(closed||!retained.contains(bookId))return;Request request=new Request(bookId,width,height,bindToken,callback);pending.put(bookId,request);latest.put(bookId,request);
        if(!running){running=true;executor.execute(this::drain);}
    }
    synchronized void retain(Set<String> bookIds){
        Set<String> bounded=new HashSet<>();for(String id:bookIds){if(bounded.size()==16)break;bounded.add(id);}retained=bounded;
        pending.keySet().retainAll(retained);latest.keySet().retainAll(retained);
        Iterator<Map.Entry<String,Bitmap>> iterator=cache.entrySet().iterator();while(iterator.hasNext()){Map.Entry<String,Bitmap> entry=iterator.next();if(!retained.contains(entry.getKey().substring(0,64))){bytes-=entry.getValue().getAllocationByteCount();iterator.remove();}}
    }
    private void drain(){
        while(true){Request request;
            synchronized(this){if(closed||pending.isEmpty()){running=false;return;}Iterator<Request> iterator=pending.values().iterator();request=iterator.next();iterator.remove();}
            Bitmap bitmap=null;String revision="";
            try{CoverStore.Record record=store.get(request.id);if(record!=null){revision=record.revision;String key=record.bookId+":"+revision+":"+request.width+"x"+request.height;
                synchronized(this){bitmap=cache.get(key);}if(bitmap==null){if(!record.sha256.equals(CoverStore.hash(record.original)))throw new java.io.IOException("Damaged cover");bitmap=CoverDecoder.decode(record.original,request.width,request.height);
                    synchronized(this){if(!closed&&retained.contains(request.id)){Bitmap previous=cache.put(key,bitmap);if(previous!=null)bytes-=previous.getAllocationByteCount();bytes+=bitmap.getAllocationByteCount();Iterator<Bitmap> iterator=cache.values().iterator();while(bytes>CACHE_BYTES&&iterator.hasNext()){bytes-=iterator.next().getAllocationByteCount();iterator.remove();}}}
                }
            }}catch(java.io.IOException|RuntimeException ignored){bitmap=null;}
            final Bitmap delivered=bitmap;final String version=revision;
            main.post(()->{synchronized(CoverLoader.this){if(closed||!retained.contains(request.id)||latest.get(request.id)!=request)return;}request.callback.ready(request.id,version,request.token,delivered);});
        }
    }
    public synchronized void close(){closed=true;pending.clear();latest.clear();cache.clear();retained.clear();bytes=0;executor.shutdownNow();}
}

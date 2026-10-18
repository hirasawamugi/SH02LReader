package com.sh02l.reader.core.importing;
import java.io.*;
public interface ImportSource {
    final class Snapshot {
        public final String identity,version;public final long size;
        public Snapshot(String identity,long size,String version){this.identity=identity;this.size=size;this.version=version;}
        public boolean same(Snapshot other){return other!=null&&identity.equals(other.identity)&&size==other.size&&version.equals(other.version);}
    }
    Snapshot snapshot()throws IOException;
    InputStream open()throws IOException;
    boolean canDelete();
    boolean deleteIfUnchanged(Snapshot expected,String sha256)throws IOException;
    default boolean isSameFile(File file)throws IOException{return false;}
}

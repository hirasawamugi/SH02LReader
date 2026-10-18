package com.sh02l.reader;

import android.system.*;
import java.io.*;

/** Persist directory entries before a move import is allowed to remove its source. */
final class DurableFiles {
    static void syncDirectory(File directory)throws IOException {
        if(!directory.isDirectory())throw new IOException("Private directory unavailable");
        try{FileDescriptor descriptor=Os.open(directory.getAbsolutePath(),OsConstants.O_RDONLY,0);
            try{Os.fsync(descriptor);}finally{Os.close(descriptor);}
        }catch(ErrnoException error){throw new IOException("Cannot sync private directory",error);}
    }
}

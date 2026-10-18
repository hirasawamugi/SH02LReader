package com.sh02l.reader.core.importing;
import java.io.IOException;
import java.util.function.LongSupplier;
public final class SpaceGuard {
    private final LongSupplier usableBytes;private final long reserveBytes;
    public SpaceGuard(LongSupplier usableBytes,long reserveBytes){if(usableBytes==null||reserveBytes<0)throw new IllegalArgumentException("space guard");this.usableBytes=usableBytes;this.reserveBytes=reserveBytes;}
    public void require(long peakBytes)throws IOException{
        if(peakBytes<0||peakBytes>Long.MAX_VALUE-reserveBytes)throw new IOException("Import budget overflow");
        if(usableBytes.getAsLong()<peakBytes+reserveBytes)throw new IOException("Not enough storage; safety reserve retained");
    }
    public void beforeWrite(long bytes)throws IOException{require(bytes);}
}

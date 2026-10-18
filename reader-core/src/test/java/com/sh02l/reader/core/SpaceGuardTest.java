package com.sh02l.reader.core;
import com.sh02l.reader.core.importing.SpaceGuard;
import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;
public class SpaceGuardTest {
    @Test public void unknownSizeAndShrinkingSpaceStopBeforeNextWrite()throws Exception {
        AtomicLong free=new AtomicLong(10*1024*1024);SpaceGuard guard=new SpaceGuard(free::get,8*1024*1024);guard.require(1024*1024);guard.beforeWrite(2*1024*1024);
        free.set(9*1024*1024);try{guard.beforeWrite(2*1024*1024);fail("Write crossed safety reserve");}catch(IOException expected){}
    }
    @Test public void budgetOverflowRejectsImport()throws Exception {
        SpaceGuard guard=new SpaceGuard(()->Long.MAX_VALUE,8*1024*1024);
        for(long size:new long[]{Long.MAX_VALUE,-1})try{guard.require(size);fail("Invalid budget accepted");}catch(IOException expected){}
    }
}

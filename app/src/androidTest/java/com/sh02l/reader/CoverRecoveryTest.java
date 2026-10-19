package com.sh02l.reader;

import android.content.Intent;
import java.io.*;
import java.util.UUID;

public final class CoverRecoveryTest extends NativeTestBase {
    private File orphan(File book,String name)throws IOException{
        File folder=new File(book,name);assertTrue(folder.mkdirs());try(OutputStream out=new FileOutputStream(new File(folder,"original.bin"))){out.write(new byte[32768]);}return folder;
    }
    public void testStartupReclaimsInterruptedCoversAfterRecoveringCommittedPointer()throws Exception{
        CoverStore store=new CoverStore(activity);String id=CoverFixtures.id();byte[] image=CoverFixtures.png(80,120);
        CoverStore.Record committed=store.importCover(id,new ByteArrayInputStream(image),"keep.png",null);File book=committed.original.getParentFile().getParentFile();
        File staging=orphan(book,".staging-"+UUID.randomUUID()),unpublished=orphan(book,UUID.randomUUID().toString()),unrelated=orphan(book,"keep-unrelated");
        File pointer=new File(book,"current.bin");assertTrue(pointer.renameTo(new File(book,"current.bin.bak")));
        try(DataOutputStream out=new DataOutputStream(new FileOutputStream(pointer))){out.writeUTF(unpublished.getName());}
        File firstBook=new File(new File(activity.getFilesDir(),"covers"),CoverFixtures.id());
        File firstStaging=orphan(firstBook,".staging-"+UUID.randomUUID()),firstRevision=orphan(firstBook,UUID.randomUUID().toString());
        File damagedBook=new File(new File(activity.getFilesDir(),"covers"),CoverFixtures.id());File uncertain=orphan(damagedBook,UUID.randomUUID().toString()),damagedStaging=orphan(damagedBook,".staging-"+UUID.randomUUID());
        try(DataOutputStream out=new DataOutputStream(new FileOutputStream(new File(damagedBook,"current.bin")))){out.writeUTF("damaged pointer");}
        ui(()->activity.finish());await(()->activity.isDestroyed(),5000);
        activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(()->find("list:LIBRARY")!=null&&listCount()>0,10000);
        assertFalse("Interrupted cover staging must be reclaimed on startup",staging.exists());assertFalse("Uncommitted revision must be reclaimed",unpublished.exists());
        assertFalse(firstStaging.exists());assertFalse(firstRevision.exists());assertTrue("Cleanup must preserve unrelated directories",unrelated.isDirectory());
        assertTrue("Uncertain committed revisions must be retained",uncertain.isDirectory());assertFalse(damagedStaging.exists());
        CoverStore.Record recovered=new CoverStore(activity).get(id);assertNotNull(recovered);assertEquals(committed.revision,recovered.revision);assertEquals(CoverFixtures.hash(image),CoverStore.hash(recovered.original));
    }
}

package com.sh02l.reader;

import android.app.*;
import android.content.*;
import android.view.KeyEvent;
import android.widget.*;

import java.io.*;

public final class ImportFlowTest extends NativeTestBase {
    public void testFinishedActivityCannotPublishACompletedImport() throws Exception {
        fixture();
        File file = new File(activity.getCacheDir(), "finish-fixture.txt");
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(("finish fixture " + System.nanoTime()).getBytes("UTF-8"));
        }
        final Exception[] setupFailure = {null};
        ui(
                () -> {
                    try {
                        activity.importFile(file);
                        // Hold UI delivery until the real worker has committed and queued its
                        // result.
                        java.lang.reflect.Field workerField =
                                MainActivity.class.getDeclaredField("worker");
                        workerField.setAccessible(true);
                        Object worker = workerField.get(activity);
                        java.lang.reflect.Field futureField =
                                LatestWorker.class.getDeclaredField("active");
                        futureField.setAccessible(true);
                        ((java.util.concurrent.Future<?>) futureField.get(worker))
                                .get(5, java.util.concurrent.TimeUnit.SECONDS);
                        activity.finish();
                        activity.getSharedPreferences("reader", 0)
                                .edit()
                                .remove("lastBook")
                                .putBoolean("reopen", false)
                                .commit();
                    } catch (Exception error) {
                        setupFailure[0] = error;
                    }
                });
        assertNull("Could not arrange completed-worker delivery", setupFailure[0]);
        await(() -> activity.isDestroyed(), 5000);
        assertEquals("Finishing activity must reject navigation callbacks", "IMPORT", kind());
        assertFalse(activity.getSharedPreferences("reader", 0).getBoolean("reopen", true));
    }

    public void testLateStoragePermissionResultCannotOpenAnotherFlow() throws Exception {
        press(KeyEvent.KEYCODE_F2);
        press(KeyEvent.KEYCODE_F4);
        ui(
                () ->
                        activity.onRequestPermissionsResult(
                                1,
                                new String[] {android.Manifest.permission.READ_EXTERNAL_STORAGE},
                                new int[] {android.content.pm.PackageManager.PERMISSION_GRANTED}));
        assertEquals("LIBRARY", kind());
    }

    public void testCancelledPickerKeepsLibraryAndSource() throws Exception {
        int before = BookRepository.obtain(activity).list().size();
        activity.getContentResolver().call(ImportTestProvider.uri("move"), "seed", "move", null);
        press(KeyEvent.KEYCODE_F2);
        IntentFilter filter = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
        filter.addCategory(Intent.CATEGORY_OPENABLE);
        filter.addDataType("*/*");
        Instrumentation.ActivityMonitor monitor =
                getInstrumentation()
                        .addMonitor(
                                filter,
                                new Instrumentation.ActivityResult(
                                        Activity.RESULT_CANCELED,
                                        new Intent().setData(ImportTestProvider.uri("move"))),
                                true);
        try {
            select("system-picker");
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            assertEquals("SOURCES", kind());
            assertEquals(before, BookRepository.obtain(activity).list().size());
            assertEquals(
                    0,
                    activity.getContentResolver()
                            .call(ImportTestProvider.uri("move"), "attempts", "move", null)
                            .getInt("count"));
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
    }

    public void testImportCannotReportPreviousReaderAsReady() throws Exception {
        fixture();
        File next = new File(activity.getCacheDir(), "ready-next.txt");
        try (OutputStream out = new FileOutputStream(next)) {
            out.write("new ready fixture".getBytes("UTF-8"));
        }
        final boolean[] stale = {false};
        ui(
                () -> {
                    activity.importFile(next);
                    stale[0] = activity.readingReady();
                });
        assertFalse("Import must not expose the old reader as ready", stale[0]);
    }

    private void picker(String id) throws Exception {
        activity.getContentResolver().call(ImportTestProvider.uri(id), "seed", id, null);
        Intent result =
                new Intent()
                        .setData(ImportTestProvider.uri(id))
                        .addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        IntentFilter filter = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
        filter.addCategory(Intent.CATEGORY_OPENABLE);
        filter.addDataType("*/*");
        Instrumentation.ActivityMonitor monitor =
                getInstrumentation()
                        .addMonitor(
                                filter,
                                new Instrumentation.ActivityResult(Activity.RESULT_OK, result),
                                true);
        try {
            select("system-picker");
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            await(() -> "IMPORT_MODE".equals(kind()), 5000);
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
    }

    public void testTxtAndCoverOfferTheSameImportModes() throws Exception {
        press(KeyEvent.KEYCODE_F2);
        picker("move");
        assertNotNull(find("item:copy-import"));
        assertNotNull(find("item:move-import"));
        press(KeyEvent.KEYCODE_F4);
        press(KeyEvent.KEYCODE_F4);
        fixture();
        press(KeyEvent.KEYCODE_F4);
        await(() -> selected() != null, 10000);
        press(KeyEvent.KEYCODE_F1);
        select("cover");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("SOURCES", kind());
        picker("image");
        assertNotNull(find("item:copy-import"));
        assertNotNull(find("item:move-import"));
    }

    public void testReadOnlyProviderCannotOfferMove() throws Exception {
        press(KeyEvent.KEYCODE_F2);
        picker("read-only");
        assertNotNull(find("item:copy-import"));
        assertNull(find("item:move-import"));
    }

    public void testPickerReturnKeepsOriginalCoverTarget() throws Exception {
        fixture();
        String a = activity.getSharedPreferences("reader", 0).getString("lastBook", "");
        File bfile = new File(activity.getCacheDir(), "another-cover-target.txt");
        try (OutputStream out = new FileOutputStream(bfile)) {
            out.write(("second target " + System.nanoTime()).getBytes("UTF-8"));
        }
        ui(() -> activity.importFile(bfile));
        await(this::ready, 15000);
        String b = activity.getSharedPreferences("reader", 0).getString("lastBook", "");
        press(KeyEvent.KEYCODE_F4);
        await(() -> selected() != null, 10000);
        for (int i = 0; i < 50; i++) {
            final boolean[] found = {false};
            ui(
                    () -> {
                        ListView list = (ListView) find("list:LIBRARY");
                        LibraryAdapter.Item item =
                                (LibraryAdapter.Item)
                                        list.getAdapter().getItem(list.getSelectedItemPosition());
                        found[0] = item.book != null && a.equals(item.book.id);
                    });
            if (found[0]) break;
            press(KeyEvent.KEYCODE_DPAD_DOWN);
        }
        press(KeyEvent.KEYCODE_F1);
        select("cover");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("SOURCES", kind());
        picker("image");
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        CoverStore store = new CoverStore(activity);
        await(
                () -> {
                    try {
                        return "LIBRARY".equals(kind()) && store.get(a) != null;
                    } catch (IOException e) {
                        return false;
                    }
                },
                15000);
        assertNull(store.get(b));
    }
}

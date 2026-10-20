package com.sh02l.reader;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.KeyEvent;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

public final class SharedStoragePermissionTest extends NativeTestBase {
    private static final String READ = Manifest.permission.READ_EXTERNAL_STORAGE,
            WRITE = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    private boolean granted(String permission) {
        return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void grant(String permission) throws Exception {
        try (ParcelFileDescriptor command =
                        getInstrumentation()
                                .getUiAutomation()
                                .executeShellCommand(
                                        "pm grant "
                                                + activity.getPackageName()
                                                + " "
                                                + permission);
                InputStream output = new FileInputStream(command.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (output.read(buffer) != -1) {}
        }
        assertTrue("Test permission was not granted: " + permission, granted(permission));
    }

    public void testSharedBrowserRequestsMissingPermissions() {
        boolean read = granted(READ), write = Build.VERSION.SDK_INT > 28 || granted(WRITE);
        List<String> requested = new ArrayList<>();
        Instrumentation.ActivityMonitor monitor =
                new Instrumentation.ActivityMonitor() {
                    @Override
                    public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                        if (!"android.content.pm.action.REQUEST_PERMISSIONS"
                                .equals(intent.getAction())) return null;
                        String[] names =
                                intent.getStringArrayExtra(
                                        "android.content.pm.extra.REQUEST_PERMISSIONS_NAMES");
                        requested.addAll(Arrays.asList(names));
                        int[] results = new int[names.length];
                        Arrays.fill(results, PackageManager.PERMISSION_DENIED);
                        return new Instrumentation.ActivityResult(
                                Activity.RESULT_OK,
                                new Intent()
                                        .putExtra(
                                                "android.content.pm.extra.REQUEST_PERMISSIONS_NAMES",
                                                names)
                                        .putExtra(
                                                "android.content.pm.extra.REQUEST_PERMISSIONS_RESULTS",
                                                results));
                    }
                };
        getInstrumentation().addMonitor(monitor);
        try {
            press(KeyEvent.KEYCODE_F2);
            select("shared");
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            if (!read)
                assertTrue(
                        "Shared browsing must request missing read access",
                        requested.contains(READ));
            if (!write)
                assertTrue(
                        "Read-only upgrades must request write access for moving shared files",
                        requested.contains(WRITE));
            if (read && write) {
                assertTrue(requested.isEmpty());
                assertEquals("FILES", kind());
            }
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
    }

    public void testDeniedWriteKeepsGrantedReadAccess() throws Exception {
        grant(READ);
        press(KeyEvent.KEYCODE_F2);
        // Arrange the pending permission callback independently of an already granted write
        // permission.
        ui(
                () -> {
                    try {
                        Field request = MainActivity.class.getDeclaredField("importRequest");
                        request.setAccessible(true);
                        Field pending =
                                MainActivity.class.getDeclaredField("storagePermissionRequest");
                        pending.setAccessible(true);
                        pending.set(activity, request.get(activity));
                        activity.onRequestPermissionsResult(
                                1,
                                new String[] {WRITE},
                                new int[] {PackageManager.PERMISSION_DENIED});
                    } catch (ReflectiveOperationException error) {
                        throw new AssertionError(error);
                    }
                });
        assertEquals(
                "Denying write must keep readable shared folders available for copying",
                "FILES",
                kind());
    }

    public void testLegacyWriteGrantEnablesVerifiedMoveFromBooks() throws Exception {
        if (Build.VERSION.SDK_INT > 28) return;
        grant(READ);
        grant(WRITE);
        File directory =
                new File(
                        Environment.getExternalStorageDirectory(),
                        "Books/SH02LReader-test-" + UUID.randomUUID());
        assertTrue(directory.mkdirs());
        File source = new File(directory, "shared-move.txt");
        String text = "Shared storage move " + UUID.randomUUID();
        try {
            try (OutputStream output = new FileOutputStream(source)) {
                output.write(text.getBytes("UTF-8"));
            }
            press(KeyEvent.KEYCODE_F2);
            select("shared");
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            assertEquals("FILES", kind());
            ui(
                    () -> {
                        try {
                            Method files =
                                    MainActivity.class.getDeclaredMethod("files", File.class);
                            files.setAccessible(true);
                            files.invoke(activity, directory);
                        } catch (ReflectiveOperationException error) {
                            throw new AssertionError(error);
                        }
                    });
            await(() -> find("item:file") != null, 5000);
            select("file");
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            await(() -> "IMPORT_MODE".equals(kind()), 5000);
            assertNotNull("Writable Books source must offer move", find("item:move-import"));
            select("move-import");
            press(KeyEvent.KEYCODE_DPAD_CENTER);
            await(this::ready, 15000);
            assertFalse("Verified move must remove the selected shared source", source.exists());
            final String[] imported = {null};
            ui(() -> imported[0] = activity.readingText());
            assertEquals(text, imported[0]);
        } finally {
            source.delete();
            directory.delete();
        }
    }
}

package com.sh02l.reader;

import android.os.Debug;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import org.json.JSONObject;

import java.io.*;
import java.util.Arrays;

/** One bounded usability/memory sample, not a throughput benchmark or P95 claim. */
public final class M3ReadingSampleTest extends NativeTestBase {
    private File report;

    public void testRepresentativeReadingSamples() throws Exception {
        report = new File(activity.getExternalFilesDir(null), "m3-samples.jsonl");
        try (FileOutputStream out = new FileOutputStream(report)) {}
        File source = new File(activity.getCacheDir(), "m3-10mib.txt");
        byte[] block = new byte[1024];
        Arrays.fill(block, (byte) 'x');
        byte[] prefix = "抽查中文，普通正文与 English。\n".getBytes("UTF-8");
        System.arraycopy(prefix, 0, block, 0, prefix.length);
        block[block.length - 1] = '\n';
        try (FileOutputStream out = new FileOutputStream(source)) {
            out.write("第一章 轻量抽查\n".getBytes("UTF-8"));
            for (int i = 0; i < 10240; i++) out.write(block);
        }
        ui(() -> activity.importFile(source));
        await(this::ready, 30000);
        sample("TXT", "ready", 0, 0);
        turns("TXT");
        SystemClock.sleep(10000);
        sample("TXT", "idle10s", 0, 0);
        press(KeyEvent.KEYCODE_F4);
        awaitLibrary();
        long listStarted = SystemClock.elapsedRealtime();
        for (int i = 0; i < 20; i++) press(KeyEvent.KEYCODE_DPAD_DOWN);
        sample("LIST", "20moves", SystemClock.elapsedRealtime() - listStarted, 0);
        File epub = EpubFixtures.illustrated(activity.getCacheDir(), false);
        ui(() -> activity.importFile(epub));
        await(this::ready, 20000);
        ui(() -> activity.seekTo(activity.reading().document.locationAt(0)));
        await(this::ready, 10000);
        sample("EPUB", "ready", 0, 0);
        turns("EPUB");
        SystemClock.sleep(10000);
        sample("EPUB", "idle10s", 0, 0);
        // Keep evidence from the actual renderer lifetime, before tearDown destroys its WebView.
        try (android.os.ParcelFileDescriptor descriptor =
                        getInstrumentation()
                                .getUiAutomation()
                                .executeShellCommand(
                                        "dumpsys activity services com.android.webview");
                InputStream in =
                        new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor);
                OutputStream out =
                        new FileOutputStream(
                                new File(
                                        activity.getExternalFilesDir(null),
                                        "m3-webview-services.txt"))) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
    }

    private void turns(String format) throws Exception {
        long started = SystemClock.elapsedRealtime(), maximum = 0;
        for (int i = 0; i < 20; i++) {
            long turn = SystemClock.elapsedRealtime();
            press(KeyEvent.KEYCODE_DPAD_DOWN);
            await(this::ready, 10000);
            maximum = Math.max(maximum, SystemClock.elapsedRealtime() - turn);
        }
        sample(format, "20turns", SystemClock.elapsedRealtime() - started, maximum);
    }

    private void sample(String format, String phase, long elapsed, long maximum) throws Exception {
        Debug.MemoryInfo memory = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memory);
        JSONObject row = new JSONObject();
        row.put("format", format);
        row.put("phase", phase);
        row.put("pid", android.os.Process.myPid());
        row.put("mainPssKiB", memory.getTotalPss());
        row.put("elapsedMs", elapsed);
        row.put("maximumObservedWaitMs", maximum);
        row.put(
                "note",
                "debug + instrumentation; key-to-ready wait includes test polling, not drawing"
                    + " P95");
        try (FileOutputStream out = new FileOutputStream(report, true)) {
            out.write((row.toString() + "\n").getBytes("UTF-8"));
        }
        Log.i("M3Sample", row.toString());
    }
}

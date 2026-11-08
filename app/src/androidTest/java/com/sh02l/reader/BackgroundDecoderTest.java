package com.sh02l.reader;

import android.graphics.*;
import android.media.ExifInterface;
import android.test.InstrumentationTestCase;

import java.io.*;

@SuppressWarnings("deprecation")
public final class BackgroundDecoderTest extends InstrumentationTestCase {
    private File file(String name) {
        return new File(getInstrumentation().getTargetContext().getCacheDir(), name);
    }

    public void testScreenDetailIsNotLostToCoverSampling() throws Exception {
        File file = file("background-detail.png");
        Bitmap source = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(source);
        Paint paint = new Paint();
        for (int x = 0; x < 720; x += 2) {
            paint.setColor(x % 4 == 0 ? Color.WHITE : Color.BLACK);
            canvas.drawRect(x, 0, x + 2, 1280, paint);
        }
        try (OutputStream out = new FileOutputStream(file)) {
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        Bitmap expected = Bitmap.createScaledBitmap(source, 540, 960, true);
        source.recycle();
        Bitmap actual = BackgroundDecoder.decode(file, 540, 960, false, 0);
        Bitmap old = CoverDecoder.decode(file, 540, 960);
        double error = error(expected, actual), oldError = error(expected, old);
        assertTrue(
                "Tile decode lost source detail: " + error + " vs old " + oldError,
                error < 5 && error < oldError / 3);
        assertTrue(actual.getAllocationByteCount() <= 2 * 1024 * 1024);
        expected.recycle();
        actual.recycle();
        old.recycle();
        assertTrue(file.delete());
    }

    private static double error(Bitmap expected, Bitmap actual) {
        long error = 0;
        for (int y = 0; y < actual.getHeight(); y += 3)
            for (int x = 0; x < actual.getWidth(); x++)
                error +=
                        Math.abs(
                                Color.red(expected.getPixel(x, y))
                                        - Color.red(actual.getPixel(x, y)));
        return error / (double) (actual.getWidth() * ((actual.getHeight() + 2) / 3));
    }

    public void testTilesPreserveAlphaAndCropPosition() throws Exception {
        File file = file("background-alpha.png");
        Bitmap source = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(source);
        Paint paint = new Paint();
        paint.setColor(0x80ff0000);
        canvas.drawRect(0, 0, 800, 800, paint);
        paint.setColor(0x800000ff);
        canvas.drawRect(800, 0, 1600, 800, paint);
        try (OutputStream out = new FileOutputStream(file)) {
            source.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        source.recycle();
        Bitmap fit = BackgroundDecoder.decode(file, 540, 960, true, 0);
        assertEquals(0, Color.alpha(fit.getPixel(270, 20)));
        for (int x = 0; x < 540; x++) assertEquals(128, Color.alpha(fit.getPixel(x, 480)));
        fit.recycle();
        Bitmap left = BackgroundDecoder.decode(file, 540, 960, false, 3);
        assertEquals(0x80ff0000, left.getPixel(270, 480));
        left.recycle();
        Bitmap right = BackgroundDecoder.decode(file, 540, 960, false, 4);
        assertEquals(0x800000ff, right.getPixel(270, 480));
        right.recycle();
        assertTrue(file.delete());
    }

    public void testExifCancellationAndOutputLimit() throws Exception {
        File file = file("background-orientation.jpg");
        Bitmap source = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(source);
        Paint p = new Paint();
        p.setColor(Color.RED);
        canvas.drawRect(0, 0, 720, 640, p);
        p.setColor(Color.BLUE);
        canvas.drawRect(0, 640, 720, 1280, p);
        try (OutputStream out = new FileOutputStream(file)) {
            source.compress(Bitmap.CompressFormat.JPEG, 95, out);
        }
        source.recycle();
        ExifInterface exif = new ExifInterface(file.getPath());
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, "6");
        exif.saveAttributes();
        Bitmap actual = BackgroundDecoder.decode(file, 540, 960, true, 0);
        assertTrue(Color.blue(actual.getPixel(20, 480)) > 240);
        assertTrue(Color.red(actual.getPixel(520, 480)) > 240);
        actual.recycle();
        try {
            BackgroundDecoder.decode(file, 2000, 3000, false, 0);
            fail("Oversize accepted");
        } catch (IOException expected) {
        }
        Thread.currentThread().interrupt();
        try {
            BackgroundDecoder.decode(file, 540, 960, false, 0);
            fail("Cancellation ignored");
        } catch (InterruptedIOException expected) {
        } finally {
            Thread.interrupted();
            file.delete();
        }
    }
}

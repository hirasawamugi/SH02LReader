package com.sh02l.reader;

import android.graphics.*;

import java.io.*;

/** Preserve viewport detail using one bounded tile at a time; never retain the full source. */
final class BackgroundDecoder {
    private static final int TILE = 512;
    private static final long BUDGET = 4L * 1024 * 1024;

    static Bitmap decode(File source, int width, int height, boolean fit, int position)
            throws IOException {
        long outputBytes = (long) width * height * 4;
        if (width < 1 || height < 1 || outputBytes > BUDGET / 2)
            throw new IOException("Background output exceeds bitmap budget");
        CoverDecoder.Info info = CoverDecoder.inspect(source);
        boolean swap = info.orientation >= 5;
        float sx = width / (float) (swap ? info.height : info.width);
        float sy = height / (float) (swap ? info.width : info.height);
        float scale = fit ? Math.min(sx, sy) : Math.max(sx, sy);
        int sample = 1;
        // Sampling is limited by screen detail, not by the size of the whole original.
        while (sample * 2 * scale <= 1 && sample < (1 << 20)) sample *= 2;
        Matrix transform =
                ImageTransform.create(
                        info.width, info.height, width, height, info.orientation, fit, position);
        Matrix inverse = new Matrix();
        if (!transform.invert(inverse)) throw new IOException("Invalid background transform");
        RectF visible = new RectF(0, 0, width, height);
        inverse.mapRect(visible);
        if (!visible.intersect(0, 0, info.width, info.height))
            throw new IOException("Empty background crop");
        BitmapRegionDecoder decoder = null;
        Bitmap output = null;
        boolean complete = false;
        try {
            decoder = BitmapRegionDecoder.newInstance(source.getAbsolutePath(), false);
            if (decoder == null) throw new IOException("Background decode failed");
            output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);
            canvas.concat(transform);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inScaled = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            int step = TILE * sample, border = 2 * sample;
            int startX = (int) (visible.left / step) * step;
            int startY = (int) (visible.top / step) * step;
            for (int y = startY; y < visible.bottom; y += step) {
                for (int x = startX; x < visible.right; x += step) {
                    if (Thread.currentThread().isInterrupted())
                        throw new InterruptedIOException("Background cancelled");
                    Rect inner =
                            new Rect(
                                    x,
                                    y,
                                    Math.min(info.width, x + step),
                                    Math.min(info.height, y + step));
                    Rect region =
                            new Rect(
                                    Math.max(0, inner.left - border),
                                    Math.max(0, inner.top - border),
                                    Math.min(info.width, inner.right + border),
                                    Math.min(info.height, inner.bottom + border));
                    Bitmap tile = decoder.decodeRegion(region, options);
                    if (tile == null) throw new IOException("Background tile decode failed");
                    try {
                        if (tile.getAllocationByteCount() + outputBytes > BUDGET)
                            throw new IOException("Background working budget exceeded");
                        int save = canvas.save();
                        // The overlap supplies filtering pixels; clipping prevents seams and
                        // double blending of transparent pixels at tile boundaries.
                        canvas.clipRect(inner);
                        canvas.drawBitmap(tile, null, new RectF(region), paint);
                        canvas.restoreToCount(save);
                    } finally {
                        tile.recycle();
                    }
                }
            }
            complete = true;
            return output;
        } catch (OutOfMemoryError error) {
            throw new IOException("Not enough memory for background", error);
        } finally {
            if (decoder != null) decoder.recycle();
            if (!complete && output != null) output.recycle();
        }
    }
}

package com.sh02l.reader;

import android.graphics.Matrix;
import android.graphics.RectF;

/** EXIF orientation followed by the user's viewport crop, shared by static images. */
final class ImageTransform {
    static Matrix create(
            int width,
            int height,
            int outputWidth,
            int outputHeight,
            int orientation,
            boolean fit,
            int position) {
        float[][] rotations = {
            {1, 0, 0, 1}, {-1, 0, 0, 1}, {-1, 0, 0, -1}, {1, 0, 0, -1},
            {0, 1, 1, 0}, {0, -1, 1, 0}, {0, -1, -1, 0}, {0, 1, -1, 0}
        };
        float[] r = rotations[orientation - 1];
        Matrix matrix = new Matrix();
        matrix.setValues(new float[] {r[0], r[1], 0, r[2], r[3], 0, 0, 0, 1});
        RectF rect = new RectF(0, 0, width, height);
        matrix.mapRect(rect);
        matrix.postTranslate(-rect.left, -rect.top);
        float scale =
                fit
                        ? Math.min(outputWidth / rect.width(), outputHeight / rect.height())
                        : Math.max(outputWidth / rect.width(), outputHeight / rect.height());
        matrix.postScale(scale, scale);
        float x = position == 3 ? 0 : position == 4 ? 1 : .5f;
        float y = position == 1 ? 0 : position == 2 ? 1 : .5f;
        matrix.postTranslate(
                (outputWidth - rect.width() * scale) * x,
                (outputHeight - rect.height() * scale) * y);
        return matrix;
    }
}

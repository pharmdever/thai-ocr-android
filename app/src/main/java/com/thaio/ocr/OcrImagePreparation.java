package com.thaio.ocr;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.File;
import java.io.IOException;

public final class OcrImagePreparation {
    private OcrImagePreparation() {}

    /** Returns a separate bitmap; the caller still owns the original. */
    public static Bitmap prepare(Bitmap source) {
        int width = source.getWidth(), height = source.getHeight();
        int[] row = new int[width];
        int darkPixels = 0, samples = 0;
        int step = Math.max(1, Math.max(width, height) / 300);
        for (int y = 0; y < height; y += step) {
            source.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x += step) {
                if (PixelPreparation.brightness(row[x]) < 96) darkPixels++;
                samples++;
            }
        }
        boolean dark = darkPixels > samples * .6;
        int[] histogram = new int[256];
        for (int y = 0; y < height; y += step) {
            source.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x += step) histogram[PixelPreparation.inkValue(row[x], dark)]++;
        }
        int low = PixelPreparation.percentile(histogram, samples, .01);
        int high = PixelPreparation.percentile(histogram, samples, .99);
        int border = Math.max(12, Math.min(width, height) / 50);
        Bitmap result = Bitmap.createBitmap(width + border * 2, height + border * 2, Bitmap.Config.ARGB_8888);
        result.eraseColor(Color.WHITE);
        for (int y = 0; y < height; y++) {
            source.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) row[x] = PixelPreparation.normalize(row[x], dark, low, high);
            result.setPixels(row, 0, width, border, border + y, width, 1);
        }
        return result;
    }

    /** Applies camera EXIF rotation/mirroring; returns source when no transform is needed. */
    public static Bitmap orient(Bitmap source, File file) {
        try {
            int orientation = new ExifInterface(file.getPath()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(-90); break;
                default: return source;
            }
            return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        } catch (IOException e) { return source; }
    }
}

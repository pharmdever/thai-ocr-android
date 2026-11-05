package com.thaio.ocr;

/** Pixel-only operations shared by Android and the desktop checks. */
public final class PixelPreparation {
    private PixelPreparation() {}
    /** Background polarity per horizontal band, so light and dark panels can coexist. */
    public static boolean[] darkRows(int[] darkCounts, int samplesPerRow, int radius) {
        boolean[] result = new boolean[darkCounts.length];
        long[] prefix = new long[darkCounts.length + 1];
        for (int i = 0; i < darkCounts.length; i++) prefix[i + 1] = prefix[i] + darkCounts[i];
        for (int y = 0; y < result.length; y++) {
            int top = Math.max(0, y - radius), bottom = Math.min(result.length, y + radius + 1);
            result[y] = prefix[bottom] - prefix[top] > .6 * samplesPerRow * (bottom - top);
        }
        return result;
    }
    public static int flatten(int color) {
        int alpha = color >>> 24;
        int r = (((color >> 16) & 255) * alpha + 255 * (255 - alpha)) / 255;
        int g = (((color >> 8) & 255) * alpha + 255 * (255 - alpha)) / 255;
        int b = ((color & 255) * alpha + 255 * (255 - alpha)) / 255;
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }
    public static int brightness(int color) {
        int c = flatten(color);
        return (299 * ((c >> 16) & 255) + 587 * ((c >> 8) & 255) + 114 * (c & 255)) / 1000;
    }
    public static int inkValue(int color, boolean dark) {
        int c = flatten(color);
        if (!dark) return brightness(c);
        // Max channel preserves blue and red lettering on dark backgrounds.
        return 255 - Math.max((c >> 16) & 255, Math.max((c >> 8) & 255, c & 255));
    }
    public static int percentile(int[] histogram, int samples, double fraction) {
        int target = Math.max(1, (int) Math.ceil(samples * fraction));
        int total = 0;
        for (int i = 0; i < histogram.length; i++) {
            total += histogram[i];
            if (total >= target) return i;
        }
        return 255;
    }
    public static int normalize(int color, boolean dark, int low, int high) {
        int value = inkValue(color, dark);
        if (high - low >= 40) value = Math.max(0, Math.min(255, (value - low) * 255 / (high - low)));
        return 0xff000000 | (value << 16) | (value << 8) | value;
    }
}

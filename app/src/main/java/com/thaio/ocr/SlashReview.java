package com.thaio.ocr;

/** Conservative review of an isolated slash: requires a visual diagonal and unique token context. */
public final class SlashReview {
    private SlashReview() {}

    public static boolean isForwardSlash(int[] pixels, int width, int height) {
        if (width < 2 || height < 6 || pixels.length != width * height) return false;
        int left = width, right = -1, top = height, bottom = -1;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (PixelPreparation.brightness(pixels[y * width + x]) >= 160) continue;
            left = Math.min(left, x); right = Math.max(right, x);
            top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        int inkHeight = bottom - top + 1, inkWidth = right - left + 1;
        if (inkHeight < 6 || inkWidth < inkHeight * .20) return false;
        long upperX = 0, lowerX = 0; int upperCount = 0, lowerCount = 0;
        for (int y = top; y <= bottom; y++) {
            double fraction = (y - top) / (double)Math.max(1, inkHeight - 1);
            for (int x = left; x <= right; x++) {
                if (PixelPreparation.brightness(pixels[y * width + x]) >= 160) continue;
                if (fraction >= .15 && fraction <= .40) { upperX += x; upperCount++; }
                if (fraction >= .60 && fraction <= .85) { lowerX += x; lowerCount++; }
            }
        }
        if (upperCount == 0 || lowerCount == 0) return false;
        double shift = upperX / (double)upperCount - lowerX / (double)lowerCount;
        // '/' tilts to the right at the top. Vertical I/l/1 and '\\' do not pass.
        return shift >= inkHeight * .10;
    }

    public static String repair(String original, String printed, boolean[] verifiedSlashes) {
        String[] before = original.trim().split("\\s+");
        String[] after = printed.trim().split("\\s+");
        boolean[] replace = new boolean[after.length];
        int slashIndex = 0;
        for (int i = 0; i < before.length; i++) {
            if (!before[i].equals("/")) continue;
            boolean verified = slashIndex < verifiedSlashes.length && verifiedSlashes[slashIndex];
            slashIndex++;
            if (!verified) continue;
            int candidate = -1, matches = 0;
            for (int j = 0; j < after.length; j++) {
                if (!(after[j].equals("I") || after[j].equals("l") || after[j].equals("1") || after[j].equals("|"))) continue;
                boolean left = i == 0 ? j == 0 : j > 0 && before[i - 1].equals(after[j - 1]);
                boolean right = i == before.length - 1 ? j == after.length - 1 : j + 1 < after.length && before[i + 1].equals(after[j + 1]);
                if (left && right) { candidate = j; matches++; }
            }
            if (matches == 1) replace[candidate] = true;
        }
        java.util.regex.Matcher tokens = java.util.regex.Pattern.compile("\\S+|\\s+").matcher(printed);
        StringBuilder result = new StringBuilder(); int index = 0;
        while (tokens.find()) {
            String token = tokens.group();
            if (!token.trim().isEmpty() && replace[index++]) token = "/";
            result.append(token);
        }
        return result.toString();
    }
}

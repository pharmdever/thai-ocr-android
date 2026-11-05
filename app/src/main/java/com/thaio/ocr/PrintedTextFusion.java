package com.thaio.ocr;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps reliable Thai spans while taking Latin letters and numbers from the printed-text model. */
public final class PrintedTextFusion {
    private static final Pattern THAI = Pattern.compile("[\\u0E00-\\u0E7F]+(?:[ \\t]+[\\u0E00-\\u0E7F]+)*");
    private PrintedTextFusion() {}
    public static String merge(String original, String printed, float originalConfidence) {
        if (printed.trim().isEmpty()) return original;
        if (originalConfidence < 85) return printed;
        List<String> thai = new ArrayList<>();
        Matcher source = THAI.matcher(original);
        while (source.find()) thai.add(source.group());
        List<int[]> regions = new ArrayList<>();
        Matcher target = THAI.matcher(printed);
        while (target.find()) regions.add(new int[]{target.start(), target.end()});
        // A script disagreement (e.g. an English unit read as Thai) must not reinsert that error.
        if (thai.size() != regions.size()) return printed;
        StringBuilder result = new StringBuilder(printed);
        for (int i = regions.size() - 1; i >= 0; i--) {
            int[] region = regions.get(i);
            String replacement = thai.get(i);
            String current = printed.substring(region[0], region[1]);
            // Do not keep an isolated Thai character that the second model reads as Latin.
            if (replacement.split("[ \\t]+").length != current.split("[ \\t]+").length) continue;
            result.replace(region[0], region[1], replacement);
        }
        return result.toString();
    }
}

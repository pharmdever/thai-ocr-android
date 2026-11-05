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
        StringBuilder result = new StringBuilder(printed);
        for (int i = thai.size() == regions.size() ? regions.size() - 1 : -1; i >= 0; i--) {
            int[] region = regions.get(i);
            String replacement = thai.get(i);
            String current = printed.substring(region[0], region[1]);
            // Do not keep an isolated Thai character that the second model reads as Latin.
            if (replacement.split("[ \\t]+").length != current.split("[ \\t]+").length) continue;
            String oldThai = canonicalThai(replacement), newThai = canonicalThai(current);
            // Keep the new reading when consonants disagree or it restores missing marks.
            // The older engine may only supply characters missing from the new reading;
            // it must not erase newly detected vowels/tones or swap a new consonant.
            boolean sameEndpoints = !newThai.isEmpty() && oldThai.charAt(0) == newThai.charAt(0)
                && oldThai.charAt(oldThai.length() - 1) == newThai.charAt(newThai.length() - 1);
            boolean leadingVowel = oldThai.length() == newThai.length() + 1 && "เแโไใ".indexOf(oldThai.charAt(0)) >= 0
                && oldThai.substring(1).equals(newThai);
            if (oldThai.length() > newThai.length() && (sameEndpoints || leadingVowel) && isSubsequence(newThai, oldThai)) {
                result.replace(region[0], region[1], oldThai);
            }
        }
        return repairIntrusiveLatin(original, result.toString());
    }

    private static String canonicalThai(String text) {
        return text.replace("\u0E4D\u0E32", "\u0E33");
    }

    private static boolean isSubsequence(String shorter, String longer) {
        int position = 0;
        for (int i = 0; i < longer.length() && position < shorter.length(); i++) {
            if (longer.charAt(i) == shorter.charAt(position)) position++;
        }
        return position == shorter.length();
    }

    private static String repairIntrusiveLatin(String original, String printed) {
        String[] before = original.split("[ \\t]+");
        String[] after = printed.split("[ \\t]+");
        if (before.length != after.length) return printed;
        StringBuilder result = new StringBuilder();
        Matcher tokens = Pattern.compile("\\S+|\\s+").matcher(printed);
        int index = 0;
        while (tokens.find()) {
            String token = tokens.group();
            if (!token.trim().isEmpty()) {
                String old = canonicalThai(before[index++]);
                if (old.matches("[\\u0E00-\\u0E7F]+") && old.length() == token.length()) {
                    int differences = 0; boolean intrusive = false;
                    for (int i = 0; i < old.length(); i++) if (old.charAt(i) != token.charAt(i)) {
                        char c = token.charAt(i); differences++;
                        intrusive = i > 0 && i < old.length() - 1 && ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
                    }
                    if (differences == 1 && intrusive) token = old;
                }
            }
            result.append(token);
        }
        return result.toString();
    }
}

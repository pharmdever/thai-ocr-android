import com.thaio.ocr.SlashReview;
import java.util.Arrays;

public final class SlashReviewCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        int width = 16, height = 24;
        int[] slash = new int[width * height]; Arrays.fill(slash, 0xffffffff);
        int[] vertical = slash.clone(), backslash = slash.clone();
        for (int y = 2; y < height - 2; y++) {
            int x = 12 - (y - 2) / 2;
            slash[y * width + x] = slash[y * width + x + 1] = 0xff000000;
            vertical[y * width + 7] = vertical[y * width + 8] = 0xff000000;
            backslash[y * width + (y - 2) / 2 + 2] = 0xff000000;
        }
        check(SlashReview.isForwardSlash(slash, width, height), "Diagonal slash must be recognized");
        check(!SlashReview.isForwardSlash(vertical, width, height), "Vertical I/l must not be treated as a slash");
        check(!SlashReview.isForwardSlash(backslash, width, height), "Backslash must not become forward slash");
        check(SlashReview.repair("Alpha / Beta", "Alpha I Beta", new boolean[]{true}).equals("Alpha / Beta"), "Repair only with visual confirmation and matching context");
        check(SlashReview.repair("Alpha / Beta", "Alpha I Beta", new boolean[]{false}).equals("Alpha I Beta"), "No visual confirmation means no replacement");
        check(SlashReview.repair("Alpha I Beta", "Alpha I Beta", new boolean[]{true}).equals("Alpha I Beta"), "Real I must stay unchanged");
        check(SlashReview.repair("Alpha / Beta", "Other I Beta", new boolean[]{true}).equals("Other I Beta"), "Conflicting context must not be guessed");
        check(SlashReview.repair("Alpha / Beta", "Alpha I Beta Alpha I Beta", new boolean[]{true}).equals("Alpha I Beta Alpha I Beta"), "Ambiguous repeated context must not be changed");
        check(SlashReview.repair("Alpha /", "Alpha I", new boolean[]{true}).equals("Alpha /"), "Slash at a wrapped line end must be supported");
        check(SlashReview.repair("Alpha / Beta", "Alpha / Beta", new boolean[]{true}).equals("Alpha / Beta"), "Correctly read slash must stay unchanged");
        System.out.println("10 slash review checks passed.");
    }
}

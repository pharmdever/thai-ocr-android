import com.thaio.ocr.CtcDecoder;
import com.thaio.ocr.PrintedTextFusion;

public final class PrintedRecognitionCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        String[] alphabet = {"", "A", "ก", " "};
        float[][] probabilities = {{0,1,0,0},{0,1,0,0},{1,0,0,0},{0,1,0,0},{0,0,1,0}};
        check(CtcDecoder.decode(probabilities, alphabet).text.equals("AAก"), "CTC must collapse repeats while respecting blank boundaries");
        check(CtcDecoder.decode(new float[][]{{1,0,0,0}}, alphabet).text.isEmpty(), "Blank must not create text");
        boolean mismatch = false;
        try { CtcDecoder.decode(new float[][]{{0,1}}, alphabet); } catch (IllegalArgumentException e) { mismatch = true; }
        check(mismatch, "Dictionary mismatch must be rejected");
        check(PrintedTextFusion.merge("ข้อความ 123-4 56", "ข้อความ 123-456", 95).equals("ข้อความ 123-456"), "Numbers must come from the new visual recognition");
        check(PrintedTextFusion.merge("ข้อความ ก label", "ข้อความ F label", 95).equals("ข้อความ F label"), "Do not reinsert a Thai character in place of a Latin letter");
        check(PrintedTextFusion.merge("Label 7ทาย", "Label 7ml", 95).equals("Label 7ml"), "A unit recognized as Thai must not be restored");
        check(PrintedTextFusion.merge("ข้อความ 2 units", "ขอความ 2 units", 95).equals("ข้อความ 2 units"), "Keep reliable Thai when script groups agree");
        check(PrintedTextFusion.merge("ผิด", "ถูก", 50).equals("ถูก"), "Low confidence source must not override the printed model");
        System.out.println("8 printed recognition checks passed.");
    }
}

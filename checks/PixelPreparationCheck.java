import com.thaio.ocr.PixelPreparation;

public final class PixelPreparationCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(PixelPreparation.normalize(0xff000000, true, 0, 255) == 0xffffffff, "Black background must become white");
        check(PixelPreparation.normalize(0xffffffff, true, 0, 255) == 0xff000000, "White text must become black");
        check((PixelPreparation.normalize(0xff0044ff, true, 0, 255) & 255) < 50, "Blue text must survive inversion");
        check((PixelPreparation.normalize(0xffff0000, true, 0, 255) & 255) < 50, "Red marks must be retained");
        check(PixelPreparation.normalize(0xffffffff, false, 0, 255) == 0xffffffff, "White paper must stay white");
        check((PixelPreparation.normalize(0xff0000ff, false, 0, 255) & 255) < 100, "Blue ink on white must remain dark");
        check(PixelPreparation.flatten(0x00000000) == 0xffffffff, "Transparent PNG background must be white");
        check(PixelPreparation.normalize(0xff808080, false, 128, 128) == 0xff808080, "Uniform image must not divide by zero");
        int[] histogram = new int[256]; histogram[10] = 10; histogram[240] = 90;
        check(PixelPreparation.percentile(histogram, 100, .01) == 10, "Lower percentile");
        check(PixelPreparation.percentile(histogram, 100, .99) == 240, "Upper percentile");
        System.out.println("10 image preparation checks passed.");
    }
}

package com.thaio.ocr;

public final class CtcDecoder {
    public static final class Text {
        public final String text;
        public final float confidence;
        Text(String text, float confidence) { this.text = text; this.confidence = confidence; }
    }
    private CtcDecoder() {}
    public static Text decode(float[][] probabilities, String[] alphabet) {
        StringBuilder text = new StringBuilder();
        int previous = -1, count = 0; float confidence = 0;
        for (float[] timestep : probabilities) {
            if (timestep.length != alphabet.length) throw new IllegalArgumentException("Model alphabet mismatch");
            int index = 0;
            for (int i = 1; i < timestep.length; i++) if (timestep[i] > timestep[index]) index = i;
            if (index != 0 && index != previous) {
                text.append(alphabet[index]); confidence += timestep[index]; count++;
            }
            previous = index;
        }
        return new Text(text.toString().trim(), count == 0 ? 0 : confidence / count);
    }
}

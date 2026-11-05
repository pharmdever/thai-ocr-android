package com.thaio.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.json.JSONArray;
import java.io.*;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Offline Thai/Latin text-line recognition with PP-OCRv5. No word replacement dictionary. */
public final class PrintedTextRecognizer implements AutoCloseable {
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String[] alphabet;

    public PrintedTextRecognizer(Context context) throws Exception {
        File directory = new File(context.getFilesDir(), "printed-model-1d4adbb");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("สร้างพื้นที่โมเดลไม่ได้");
        File model = new File(directory, "inference.onnx");
        if (!model.isFile() || model.length() < 1000000) {
            File temporary = new File(directory, "inference.tmp");
            try (InputStream in = context.getAssets().open("printed/inference.onnx");
                 OutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[32768]; int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            if (!temporary.renameTo(model)) throw new IOException("ติดตั้งโมเดลข้อความพิมพ์ไม่ได้");
        }
        try (InputStream in = context.getAssets().open("printed/characters.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
            JSONArray chars = new JSONArray(bytes.toString(StandardCharsets.UTF_8.name()));
            alphabet = new String[chars.length()];
            for (int i = 0; i < chars.length(); i++) alphabet[i] = chars.getString(i);
        }
        environment = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(2);
            options.setInterOpNumThreads(1);
            session = environment.createSession(model.getAbsolutePath(), options);
        }
    }

    public CtcDecoder.Text read(Bitmap image, Rect region) throws OrtException {
        int padding = 4;
        int left = Math.max(0, region.left - padding), top = Math.max(0, region.top - padding);
        int right = Math.min(image.getWidth(), region.right + padding), bottom = Math.min(image.getHeight(), region.bottom + padding);
        if (right <= left || bottom <= top) return new CtcDecoder.Text("", 0);
        Bitmap crop = Bitmap.createBitmap(image, left, top, right - left, bottom - top);
        int resizedWidth = Math.min(3200, Math.max(1, (int)Math.ceil(48.0 * crop.getWidth() / crop.getHeight())));
        int inputWidth = Math.max(320, resizedWidth);
        Bitmap resized = Bitmap.createScaledBitmap(crop, resizedWidth, 48, true);
        try {
            float[] values = new float[3 * 48 * inputWidth];
            int[] row = new int[resizedWidth];
            for (int y = 0; y < 48; y++) {
                resized.getPixels(row, 0, resizedWidth, 0, y, resizedWidth, 1);
                for (int x = 0; x < resizedWidth; x++) {
                    int color = row[x], offset = y * inputWidth + x;
                    // Model expects BGR, CHW, pixels normalized to [-1, 1]; padding stays zero.
                    values[offset] = (color & 255) / 127.5f - 1;
                    values[48 * inputWidth + offset] = ((color >> 8) & 255) / 127.5f - 1;
                    values[96 * inputWidth + offset] = ((color >> 16) & 255) / 127.5f - 1;
                }
            }
            try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), new long[]{1, 3, 48, inputWidth});
                 OrtSession.Result result = session.run(Collections.singletonMap("x", tensor))) {
                float[][][] probabilities = (float[][][])result.get(0).getValue();
                return CtcDecoder.decode(probabilities[0], alphabet);
            }
        } finally {
            if (resized != crop) resized.recycle();
            if (crop != image) crop.recycle();
        }
    }
    @Override public void close() throws OrtException { session.close(); }
}

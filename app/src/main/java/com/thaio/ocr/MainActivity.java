package com.thaio.ocr;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import cz.adaptech.tesseract4android.TessBaseAPI;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private EditText output;
    private TextView status;
    private ProgressBar progress;
    private Button choose, copy, save;
    private boolean busy;
    private String pendingExport = "";
    private static final int PICK = 10, SAVE = 11;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad, pad, pad);
        body.setBackgroundColor(Color.rgb(245, 248, 250));
        scroll.addView(body);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        TextView title = new TextView(this);
        title.setText("อ่านดี OCR"); title.setTextSize(30); title.setTextColor(Color.rgb(18, 83, 89));
        body.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("แปลงภาพและ PDF เป็นข้อความ\nภาษาไทย + English • ใช้งานออฟไลน์");
        subtitle.setTextSize(16); body.addView(subtitle);
        choose = button(body, "เลือกภาพหรือ PDF");
        choose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png", "application/pdf"});
            startActivityForResult(intent, PICK);
        });
        status = new TextView(this);
        status.setText("เลือกไฟล์เพื่อเริ่ม • PDF สูงสุด 30 หน้า • ขนาดไม่เกิน 50 MB");
        status.setPadding(0, 16, 0, 16); body.addView(status);
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE); body.addView(progress);
        output = new EditText(this);
        output.setGravity(android.view.Gravity.TOP);
        output.setHint("ข้อความที่อ่านได้จะแสดงที่นี่ และแก้ไขได้");
        output.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        output.setMinLines(12); output.setTextSize(18); body.addView(output);
        copy = button(body, "คัดลอกข้อความ");
        copy.setOnClickListener(v -> {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(
                ClipData.newPlainText("OCR", output.getText().toString()));
            Toast.makeText(this, "คัดลอกแล้ว", Toast.LENGTH_SHORT).show();
        });
        save = button(body, "บันทึกเป็นไฟล์ .txt");
        save.setOnClickListener(v -> {
            pendingExport = output.getText().toString();
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "ocr-result.txt"); startActivityForResult(intent, SAVE);
        });
        TextView note = new TextView(this);
        note.setText("ไฟล์ประมวลผลบนเครื่อง ไม่มีการส่งเอกสารขึ้นเซิร์ฟเวอร์\nโปรดตรวจข้อความก่อนนำไปใช้ โดยเฉพาะตัวเลขและชื่อ");
        note.setTextSize(13); body.addView(note);
    }

    private Button button(LinearLayout parent, String label) {
        Button button = new Button(this); button.setText(label); parent.addView(button); return button;
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == PICK && !busy) {
            busy = true; choose.setEnabled(false); save.setEnabled(false); copy.setEnabled(false);
            output.setEnabled(false); progress.setVisibility(View.VISIBLE);
            status.setText("กำลังเตรียมไฟล์และชุดภาษา…");
            executor.execute(() -> recognize(uri));
        } else if (request == SAVE) {
            try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                if (stream == null) throw new IOException();
                stream.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "บันทึกเรียบร้อย", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { status.setText("บันทึกไม่ได้ กรุณาเลือกตำแหน่งใหม่"); }
            pendingExport = "";
        }
    }

    private void updateStatus(String text) {
        runOnUiThread(() -> { if (!closed) status.setText(text); });
    }

    private void models() throws IOException {
        File directory = new File(getFilesDir(), "tessdata");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("สร้างพื้นที่ชุดภาษาไม่ได้");
        for (String language : new String[]{"tha", "eng"}) {
            File target = new File(directory, language + ".traineddata");
            if (target.isFile() && target.length() > 100000) continue;
            File temp = new File(directory, language + ".tmp");
            try (InputStream source = getAssets().open("tessdata/" + language + ".traineddata");
                 OutputStream destination = new FileOutputStream(temp)) {
                byte[] buffer = new byte[32768]; int n;
                while ((n = source.read(buffer)) != -1) destination.write(buffer, 0, n);
            }
            if (!temp.renameTo(target)) throw new IOException("ติดตั้งชุดภาษาไม่สำเร็จ");
        }
    }

    private void recognize(Uri uri) {
        TessBaseAPI engine = new TessBaseAPI();
        File temp = null;
        try {
            models();
            if (!engine.init(getFilesDir().getAbsolutePath(), "tha+eng", TessBaseAPI.OEM_LSTM_ONLY))
                throw new IOException("เปิดระบบ OCR ไม่สำเร็จ");
            temp = File.createTempFile("document-", ".bin", getCacheDir());
            try (InputStream input = getContentResolver().openInputStream(uri);
                 OutputStream file = new FileOutputStream(temp)) {
                if (input == null) throw new IOException("เปิดไฟล์ไม่ได้");
                byte[] buffer = new byte[32768]; int n; long total = 0;
                while ((n = input.read(buffer)) != -1) {
                    if (closed) return;
                    total += n;
                    if (total > 50L * 1024 * 1024) throw new IOException("ไฟล์ใหญ่เกิน 50 MB");
                    file.write(buffer, 0, n);
                }
            }
            boolean pdf;
            try (InputStream header = new FileInputStream(temp)) {
                byte[] magic = new byte[5];
                pdf = header.read(magic) == 5 && new String(magic, StandardCharsets.US_ASCII).equals("%PDF-");
            }
            StringBuilder text = new StringBuilder();
            if (pdf) {
                try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY);
                     PdfRenderer renderer = new PdfRenderer(fd)) {
                    int count = renderer.getPageCount();
                    if (count > 30) throw new IOException("PDF เกิน 30 หน้า กรุณาแบ่งไฟล์ก่อน");
                    for (int i = 0; i < count; i++) {
                        if (closed) return;
                        updateStatus("กำลังอ่านหน้า " + (i + 1) + " / " + count);
                        try (PdfRenderer.Page page = renderer.openPage(i)) {
                            float scale = Math.min(3f, 2400f / Math.max(page.getWidth(), page.getHeight()));
                            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, (int)(page.getWidth() * scale)),
                                Math.max(1, (int)(page.getHeight() * scale)), Bitmap.Config.ARGB_8888);
                            try {
                                bitmap.eraseColor(Color.WHITE);
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                                text.append("--- หน้า ").append(i + 1).append(" ---\n").append(read(engine, bitmap)).append("\n\n");
                            } finally { bitmap.recycle(); }
                        }
                    }
                }
            } else {
                updateStatus("กำลังอ่านข้อความจากภาพ…");
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(temp.getPath(), options);
                if (options.outWidth <= 0) throw new IOException("รองรับเฉพาะภาพ JPG, PNG และ PDF ที่ไม่ล็อกรหัสผ่าน");
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2400) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                Bitmap bitmap = BitmapFactory.decodeFile(temp.getPath(), options);
                if (bitmap == null) throw new IOException("อ่านรูปไม่ได้");
                try { text.append(read(engine, bitmap)); } finally { bitmap.recycle(); }
            }
            String result = text.toString().trim();
            runOnUiThread(() -> {
                if (closed) return;
                output.setText(result);
                status.setText(result.isEmpty() ? "ไม่พบข้อความ ลองใช้ภาพที่ชัดและวางตรง" : "อ่านเสร็จแล้ว ตรวจและแก้ไขข้อความได้ด้านล่าง");
            });
        } catch (Exception | OutOfMemoryError e) {
            updateStatus("อ่านไม่สำเร็จ: " + (e instanceof OutOfMemoryError ? "หน่วยความจำไม่พอ ลองไฟล์เล็กลง" :
                "โปรดใช้ JPG, PNG หรือ PDF ไม่ล็อกรหัสผ่าน ขนาดไม่เกิน 50 MB / 30 หน้า"));
        } finally {
            engine.recycle();
            if (temp != null) temp.delete();
            runOnUiThread(() -> {
                busy = false;
                if (!closed) {
                    choose.setEnabled(true); save.setEnabled(true); copy.setEnabled(true);
                    output.setEnabled(true); progress.setVisibility(View.GONE);
                }
            });
        }
    }

    private String read(TessBaseAPI engine, Bitmap bitmap) {
        engine.setImage(bitmap);
        String text = engine.getUTF8Text(); engine.clear(); return text == null ? "" : text;
    }

    @Override protected void onDestroy() {
        closed = true; executor.shutdown(); super.onDestroy();
    }
}

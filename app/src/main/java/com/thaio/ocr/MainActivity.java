package com.thaio.ocr;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.widget.*;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.googlecode.tesseract.android.ResultIterator;
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private EditText output;
    private TextView status;
    private ProgressBar progress;
    private Button choose, copy, save;
    private Button retry;
    private CheckBox detailed, enhance;
    private Spinner language, layout;
    private ImageView preview;
    private Bitmap previewBitmap;
    private Uri lastUri;
    private int uncertainWords;
    private int recognizedCharacters;
    private boolean busy;
    private String pendingExport = "";
    private static final int PICK = 10, SAVE = 11;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(245, 248, 250));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
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
        detailed = new CheckBox(this); detailed.setText("โหมดข้อความพิมพ์ (แนะนำสำหรับไทย + English)");
        detailed.setChecked(true); body.addView(detailed);
        enhance = new CheckBox(this); enhance.setText("ปรับภาพอัตโนมัติ รวมถึงภาพพื้นดำ / ตัวอักษรสี");
        enhance.setChecked(true); body.addView(enhance);
        language = new Spinner(this);
        language.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"ภาษา: ไทย + English", "ภาษา: ไทย", "ภาษา: English"}));
        body.addView(language);
        layout = new Spinner(this);
        layout.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"รูปแบบ: อัตโนมัติ", "รูปแบบ: ข้อความกระจาย", "รูปแบบ: บรรทัดเดียว"}));
        body.addView(layout);
        choose = button(body, "เลือกภาพหรือ PDF");
        choose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png", "application/pdf"});
            startActivityForResult(intent, PICK);
        });
        retry = button(body, "อ่านไฟล์เดิมอีกครั้งด้วยตัวเลือกด้านบน");
        retry.setEnabled(false);
        retry.setOnClickListener(v -> { if (lastUri != null && !busy) startRecognition(lastUri); });
        status = new TextView(this);
        status.setText("เลือกไฟล์เพื่อเริ่ม • PDF สูงสุด 30 หน้า • ขนาดไม่เกิน 50 MB");
        status.setPadding(0, 16, 0, 16); body.addView(status);
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE); body.addView(progress);
        TextView previewLabel = new TextView(this);
        previewLabel.setText("ภาพต้นฉบับสำหรับตรวจเทียบ (PDF แสดงหน้าที่อ่านล่าสุด)");
        body.addView(previewLabel);
        preview = new ImageView(this); preview.setAdjustViewBounds(true);
        preview.setMaxHeight((int)(230 * getResources().getDisplayMetrics().density));
        preview.setContentDescription("ภาพต้นฉบับ"); body.addView(preview);
        TextView confidenceNote = new TextView(this);
        confidenceNote.setText("พื้นเหลือง = คำที่ระบบไม่มั่นใจ คำที่ไม่เป็นสีเหลืองก็อาจอ่านผิดได้");
        confidenceNote.setTextSize(13); body.addView(confidenceNote);
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
            lastUri = uri;
            startRecognition(uri);
        } else if (request == SAVE) {
            try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                if (stream == null) throw new IOException();
                stream.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "บันทึกเรียบร้อย", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { status.setText("บันทึกไม่ได้ กรุณาเลือกตำแหน่งใหม่"); }
            pendingExport = "";
        }
    }

    private void startRecognition(Uri uri) {
        boolean quality = detailed.isChecked(), prepare = enhance.isChecked();
        String langs = new String[]{"tha+eng", "tha", "eng"}[language.getSelectedItemPosition()];
        int pageMode = new int[]{TessBaseAPI.PageSegMode.PSM_AUTO,
            TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT, TessBaseAPI.PageSegMode.PSM_SINGLE_LINE}[layout.getSelectedItemPosition()];
        busy = true; choose.setEnabled(false); save.setEnabled(false); copy.setEnabled(false);
        retry.setEnabled(false); detailed.setEnabled(false); enhance.setEnabled(false);
        language.setEnabled(false); layout.setEnabled(false);
        output.setEnabled(false); progress.setVisibility(View.VISIBLE);
        status.setText("กำลังเตรียมไฟล์และชุดภาษา…");
        executor.execute(() -> recognize(uri, quality, prepare, langs, pageMode));
    }

    private void updateStatus(String text) {
        runOnUiThread(() -> { if (!closed) status.setText(text); });
    }

    private File models(boolean quality) throws IOException {
        String model = quality ? "best" : "fast";
        // A new directory prevents upgrades from silently reusing the previous fast models.
        File root = new File(getFilesDir(), "models-v2/" + model);
        File directory = new File(root, "tessdata");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("สร้างพื้นที่ชุดภาษาไม่ได้");
        for (String language : new String[]{"tha", "eng"}) {
            File target = new File(directory, language + ".traineddata");
            if (target.isFile() && target.length() > 100000) continue;
            File temp = new File(directory, language + ".tmp");
            try (InputStream source = getAssets().open("models/" + model + "/tessdata/" + language + ".traineddata");
                 OutputStream destination = new FileOutputStream(temp)) {
                byte[] buffer = new byte[32768]; int n;
                while ((n = source.read(buffer)) != -1) destination.write(buffer, 0, n);
            }
            if (!temp.renameTo(target)) throw new IOException("ติดตั้งชุดภาษาไม่สำเร็จ");
        }
        return root;
    }

    private void recognize(Uri uri, boolean quality, boolean prepare, String langs, int pageMode) {
        TessBaseAPI engine = new TessBaseAPI();
        PrintedTextRecognizer printed = null;
        File temp = null;
        try {
            uncertainWords = 0; recognizedCharacters = 0;
            File modelRoot = models(quality);
            if (!engine.init(modelRoot.getAbsolutePath(), langs, TessBaseAPI.OEM_LSTM_ONLY))
                throw new IOException("เปิดระบบ OCR ไม่สำเร็จ");
            engine.setPageSegMode(pageMode);
            if (quality && langs.equals("tha+eng")) {
                updateStatus("กำลังเตรียมระบบอ่านข้อความพิมพ์…");
                printed = new PrintedTextRecognizer(this);
            }
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
            SpannableStringBuilder text = new SpannableStringBuilder();
            if (pdf) {
                try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY);
                     PdfRenderer renderer = new PdfRenderer(fd)) {
                    int count = renderer.getPageCount();
                    if (count > 30) throw new IOException("PDF เกิน 30 หน้า กรุณาแบ่งไฟล์ก่อน");
                    for (int i = 0; i < count; i++) {
                        if (closed) return;
                        updateStatus("กำลังอ่านหน้า " + (i + 1) + " / " + count);
                        try (PdfRenderer.Page page = renderer.openPage(i)) {
                            float scale = Math.min(4f, (quality ? 3000f : 2400f) / Math.max(page.getWidth(), page.getHeight()));
                            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, (int)(page.getWidth() * scale)),
                                Math.max(1, (int)(page.getHeight() * scale)), Bitmap.Config.ARGB_8888);
                            try {
                                bitmap.eraseColor(Color.WHITE);
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
                                text.append("--- หน้า ").append(String.valueOf(i + 1)).append(" ---\n").append(read(engine, bitmap, prepare, printed)).append("\n\n");
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
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > (quality ? 3200 : 2400)) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                Bitmap bitmap = BitmapFactory.decodeFile(temp.getPath(), options);
                if (bitmap == null) throw new IOException("อ่านรูปไม่ได้");
                Bitmap oriented = OcrImagePreparation.orient(bitmap, temp);
                if (oriented != bitmap) bitmap.recycle();
                try { text.append(read(engine, oriented, prepare, printed)); } finally { oriented.recycle(); }
            }
            CharSequence result = text;
            runOnUiThread(() -> {
                if (closed) return;
                output.setText(result);
                status.setText(recognizedCharacters == 0 ? "ไม่พบข้อความ ลองใช้ภาพที่ชัดและวางตรง" :
                    "อ่านเสร็จแล้ว • พบคำที่ควรตรวจ " + uncertainWords + " จุด • โปรดตรวจชื่อและตัวเลขกับต้นฉบับ");
            });
        } catch (Exception | OutOfMemoryError e) {
            updateStatus("อ่านไม่สำเร็จ: " + (e instanceof OutOfMemoryError ? "หน่วยความจำไม่พอ ลองไฟล์เล็กลง" :
                "ลองไฟล์ JPG, PNG หรือ PDF ไม่ล็อกรหัสผ่าน หากเครื่องไม่รองรับให้ปิดโหมดข้อความพิมพ์แล้วอ่านซ้ำ"));
        } finally {
            if (printed != null) {
                try { printed.close(); } catch (Exception ignored) { }
            }
            engine.recycle();
            if (temp != null) temp.delete();
            runOnUiThread(() -> {
                busy = false;
                if (!closed) {
                    choose.setEnabled(true); save.setEnabled(true); copy.setEnabled(true);
                    output.setEnabled(true); progress.setVisibility(View.GONE);
                    retry.setEnabled(lastUri != null); detailed.setEnabled(true); enhance.setEnabled(true);
                    language.setEnabled(true); layout.setEnabled(true);
                }
            });
        }
    }

    private CharSequence read(TessBaseAPI engine, Bitmap bitmap, boolean prepare, PrintedTextRecognizer printed) throws Exception {
        if (closed) return "";
        showPreview(bitmap);
        Bitmap processed = prepare ? OcrImagePreparation.prepare(bitmap) : bitmap;
        try {
            engine.setImage(processed);
            String text = engine.getUTF8Text();
            if (text == null) text = "";
            if (printed != null) return readPrinted(engine, processed, printed, text);
            recognizedCharacters += text.trim().length();
            SpannableString marked = new SpannableString(text);
            ResultIterator iterator = engine.getResultIterator();
            if (iterator != null) {
                try {
                    iterator.begin(); int cursor = 0;
                    do {
                        String word = iterator.getUTF8Text(PageIteratorLevel.RIL_WORD);
                        if (word == null || word.trim().isEmpty()) continue;
                        word = word.trim();
                        int index = text.indexOf(word, cursor);
                        if (index < 0) continue;
                        if (iterator.confidence(PageIteratorLevel.RIL_WORD) < 75) {
                            marked.setSpan(new BackgroundColorSpan(0xffffe59a), index, index + word.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            uncertainWords++;
                        }
                        cursor = index + word.length();
                    } while (iterator.next(PageIteratorLevel.RIL_WORD));
                } finally { iterator.delete(); }
            }
            // Script changes after a digit and split hyphenated numbers can have misleadingly
            // high OCR confidence. Flag them for review without rewriting any values.
            Matcher suspicious = Pattern.compile("[0-9]+[\\u0E00-\\u0E7F]+|[0-9]+(?:-[0-9]+)+[ \\t]+[0-9]+(?:-[0-9]+)*").matcher(text);
            while (suspicious.find()) {
                marked.setSpan(new BackgroundColorSpan(0xffffe59a), suspicious.start(), suspicious.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                uncertainWords++;
            }
            return marked;
        } finally {
            engine.clear();
            if (processed != bitmap) processed.recycle();
        }
    }

    private static final class TextLine {
        final Rect bounds;
        final String original;
        final float confidence;
        final List<Rect> slashBoxes = new ArrayList<>();
        TextLine(Rect bounds, String original, float confidence) {
            this.bounds = bounds; this.original = original; this.confidence = confidence;
        }
    }

    private CharSequence readPrinted(TessBaseAPI engine, Bitmap image, PrintedTextRecognizer printed, String fallback) throws Exception {
        List<TextLine> lines = new ArrayList<>();
        ResultIterator iterator = engine.getResultIterator();
        if (iterator != null) {
            try {
                iterator.begin();
                do {
                    String original = iterator.getUTF8Text(PageIteratorLevel.RIL_TEXTLINE);
                    Rect box = iterator.getBoundingRect(PageIteratorLevel.RIL_TEXTLINE);
                    if (original == null || original.trim().isEmpty() || box == null || box.width() < 3 || box.height() < 4) continue;
                    lines.add(new TextLine(new Rect(box), original.trim(), iterator.confidence(PageIteratorLevel.RIL_TEXTLINE)));
                } while (iterator.next(PageIteratorLevel.RIL_TEXTLINE));
            } finally { iterator.delete(); }
        }
        iterator = engine.getResultIterator();
        if (iterator != null) {
            try {
                iterator.begin();
                do {
                    String word = iterator.getUTF8Text(PageIteratorLevel.RIL_WORD);
                    if (word == null || !word.trim().equals("/")) continue;
                    Rect box = iterator.getBoundingRect(PageIteratorLevel.RIL_WORD);
                    if (box == null) continue;
                    for (TextLine line : lines) {
                        if (line.bounds.contains(box.centerX(), box.centerY())
                            && Pattern.compile("(^|\\s)/(\\s|$)").matcher(line.original).find()) {
                            line.slashBoxes.add(new Rect(box)); break;
                        }
                    }
                } while (iterator.next(PageIteratorLevel.RIL_WORD));
            } finally { iterator.delete(); }
        }
        if (lines.isEmpty()) lines.add(new TextLine(new Rect(0, 0, image.getWidth(), image.getHeight()), fallback, 0));
        SpannableStringBuilder result = new SpannableStringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (closed) break;
            updateStatus("กำลังอ่านข้อความพิมพ์ บรรทัด " + (i + 1) + " / " + lines.size());
            TextLine line = lines.get(i);
            CtcDecoder.Text prediction = printed.read(image, line.bounds);
            String text = PrintedTextFusion.merge(line.original, prediction.text, line.confidence);
            boolean[] slashEvidence = new boolean[line.slashBoxes.size()];
            for (int j = 0; j < slashEvidence.length; j++) slashEvidence[j] = visuallyForwardSlash(image, line.slashBoxes.get(j));
            text = SlashReview.repair(line.original, text, slashEvidence);
            SpannableString marked = new SpannableString(text);
            // Model scores indicate uncertainty, not the probability that the full line is correct.
            if (prediction.confidence < .90f || prediction.text.trim().isEmpty()) {
                marked.setSpan(new BackgroundColorSpan(0xffffe59a), 0, marked.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                uncertainWords++;
            }
            Matcher suspicious = Pattern.compile("[0-9]+[\\u0E00-\\u0E7F]+|[0-9]+(?:-[0-9]+)+[ \\t]+[0-9]+(?:-[0-9]+)*").matcher(text);
            while (suspicious.find()) {
                marked.setSpan(new BackgroundColorSpan(0xffffe59a), suspicious.start(), suspicious.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                uncertainWords++;
            }
            if (result.length() > 0) result.append("\n");
            result.append(marked);
            recognizedCharacters += text.trim().length();
        }
        return result;
    }

    private boolean visuallyForwardSlash(Bitmap image, Rect box) {
        Rect clipped = new Rect(box);
        if (!clipped.intersect(0, 0, image.getWidth(), image.getHeight()) || clipped.width() < 2 || clipped.height() < 6) return false;
        // Punctuation should be small; reject unexpected large regions instead of classifying them.
        if ((long)clipped.width() * clipped.height() > 100000) return false;
        int[] pixels = new int[clipped.width() * clipped.height()];
        image.getPixels(pixels, 0, clipped.width(), clipped.left, clipped.top, clipped.width(), clipped.height());
        return SlashReview.isForwardSlash(pixels, clipped.width(), clipped.height());
    }

    private void showPreview(Bitmap source) {
        float ratio = Math.min(1f, 900f / Math.max(source.getWidth(), source.getHeight()));
        Bitmap small = Bitmap.createScaledBitmap(source, Math.max(1, (int)(source.getWidth() * ratio)),
            Math.max(1, (int)(source.getHeight() * ratio)), true);
        // createScaledBitmap can return the source; never let the UI own the OCR bitmap.
        Bitmap thumbnail = small == source ? source.copy(Bitmap.Config.ARGB_8888, false) : small;
        runOnUiThread(() -> {
            if (closed) { thumbnail.recycle(); return; }
            Bitmap previous = previewBitmap;
            previewBitmap = thumbnail; preview.setImageBitmap(thumbnail);
            if (previous != null) previous.recycle();
        });
    }

    @Override protected void onDestroy() {
        closed = true; executor.shutdown();
        preview.setImageDrawable(null);
        if (previewBitmap != null) { previewBitmap.recycle(); previewBitmap = null; }
        super.onDestroy();
    }
}

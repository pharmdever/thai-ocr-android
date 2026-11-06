package com.thaio.ocr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
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
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.widget.*;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import org.json.JSONArray;
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
import java.util.UUID;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import static com.thaio.ocr.AppStyle.*;

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
    private final ExecutorService storageExecutor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autosave = () -> saveCurrent(false);
    private final Runnable searchLibrary = () -> refreshLibrary();
    private HistoryStore history;
    private SharedPreferences preferences;
    private EditText noteTitle, search;
    private TextView saveState, characters, sourceName, heroHeading, heroSubtitle, libraryCount, settingsSummary;
    private LinearLayout readContent, libraryContent, libraryList, editorCard, previewCard, advanced, emptyResult;
    private ScrollView readScroll;
    private Button saveToLibrary, navRead, navLibrary, loadMore;
    private String currentNoteId, sourceLabel = "", pendingSourceLabel = "";
    private long currentCreated;
    private boolean suppressEdits, dirty, libraryVisible;
    private int editRevision, queryGeneration, loadGeneration, libraryLimit = 40;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        history = new HistoryStore(this);
        preferences = getSharedPreferences("read-dee", MODE_PRIVATE);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        buildInterface();
        String previous = state == null ? preferences.getString("last_note", null) : state.getString("noteId");
        if (previous != null) openNote(previous, false);
    }

    private void buildInterface() {
        LinearLayout root = column(this); root.setBackgroundColor(BACKGROUND);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom()); return insets;
        });
        setContentView(root);
        LinearLayout header = row(this); header.setPadding(dp(this,20),dp(this,18),dp(this,20),dp(this,12));
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_scan); logo.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        logo.setPadding(dp(this,12),dp(this,12),dp(this,12),dp(this,12)); logo.setBackground(rounded(this,TEAL,15,0));
        header.addView(logo,new LinearLayout.LayoutParams(dp(this,48),dp(this,48)));
        LinearLayout brand = column(this); brand.setPadding(dp(this,12),0,0,0);
        brand.addView(text(this,"อ่านดี",24,INK,true)); brand.addView(text(this,"OCR ภาษาไทยและอังกฤษ",12,MUTED,false));
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView offline = text(this,"ออฟไลน์",12,TEAL,true); offline.setPadding(dp(this,12),dp(this,7),dp(this,12),dp(this,7));
        offline.setBackground(rounded(this,LIGHT,20,0)); header.addView(offline); root.addView(header);
        android.widget.FrameLayout pages = new android.widget.FrameLayout(this);
        root.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
        readScroll = new ScrollView(this); readScroll.setFillViewport(true); readScroll.setClipToPadding(false);
        readContent = column(this); readContent.setPadding(dp(this,20),dp(this,8),dp(this,20),dp(this,24)); readScroll.addView(readContent);
        pages.addView(readScroll,new android.widget.FrameLayout.LayoutParams(-1,-1));
        ScrollView libraryScroll = new ScrollView(this); libraryScroll.setFillViewport(true);
        libraryContent = column(this); libraryContent.setPadding(dp(this,20),dp(this,8),dp(this,20),dp(this,24)); libraryScroll.addView(libraryContent);
        libraryScroll.setVisibility(View.GONE); libraryScroll.setTag("library-page"); pages.addView(libraryScroll,new android.widget.FrameLayout.LayoutParams(-1,-1));

        LinearLayout upload = card(this);
        upload.addView(text(this,"เปลี่ยนเอกสารให้พร้อมใช้งาน",12,TEAL,true)); gap(upload,8);
        heroHeading = text(this,"อ่านข้อความง่าย ๆ\nเก็บไว้ใช้ต่อได้ทุกเมื่อ",25,INK,true); upload.addView(heroHeading);
        gap(upload,6); heroSubtitle = text(this,"เลือกภาพหรือ PDF แล้วอ่าน แก้ไข และบันทึกไว้ในเครื่อง",14,MUTED,false); upload.addView(heroSubtitle);
        sourceName = text(this,"",14,INK,true); sourceName.setMaxLines(2); sourceName.setEllipsize(TextUtils.TruncateAt.END); sourceName.setVisibility(View.GONE); upload.addView(sourceName);
        gap(upload,16); choose = AppStyle.button(this,"เลือกภาพหรือ PDF",true); withIcon(choose,R.drawable.ic_document,Color.WHITE);
        upload.addView(choose,new LinearLayout.LayoutParams(-1,-2)); choose.setOnClickListener(v -> pickFile());
        gap(upload,10); upload.addView(text(this,"JPG, PNG หรือ PDF • ไม่เกิน 50 MB / 30 หน้า",12,MUTED,false));
        readContent.addView(upload); gap(readContent,14);

        LinearLayout options = card(this);
        Button settings = AppStyle.button(this,"ตัวเลือกการอ่าน  ⌄",false); options.addView(settings,new LinearLayout.LayoutParams(-1,-2));
        settingsSummary = text(this,"",12,MUTED,false); gap(options,8); options.addView(settingsSummary);
        advanced = column(this); advanced.setVisibility(View.GONE); gap(advanced,12);
        detailed = new CheckBox(this); detailed.setText("โหมดข้อความพิมพ์ไทย–อังกฤษ"); detailed.setTextColor(INK); detailed.setTextSize(14); detailed.setChecked(preferences.getBoolean("quality",true)); advanced.addView(detailed);
        enhance = new CheckBox(this); enhance.setText("ปรับภาพพื้นดำและตัวอักษรสีอัตโนมัติ"); enhance.setTextColor(INK); enhance.setTextSize(14); enhance.setChecked(preferences.getBoolean("enhance",true)); advanced.addView(enhance);
        language = new Spinner(this); language.setContentDescription("ภาษาที่ต้องการอ่าน"); language.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"ไทย + English","ไทย","English"}));
        language.setSelection(Math.max(0,Math.min(2,preferences.getInt("language",0))));
        advanced.addView(language,new LinearLayout.LayoutParams(-1,dp(this,50)));
        layout = new Spinner(this); layout.setContentDescription("รูปแบบเอกสาร"); layout.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"รูปแบบอัตโนมัติ","ข้อความกระจาย","บรรทัดเดียว"}));
        layout.setSelection(Math.max(0,Math.min(2,preferences.getInt("layout",0)))); advanced.addView(layout,new LinearLayout.LayoutParams(-1,dp(this,50)));
        gap(advanced,6); retry = AppStyle.button(this,"อ่านไฟล์เดิมอีกครั้ง",false); retry.setEnabled(false); advanced.addView(retry,new LinearLayout.LayoutParams(-1,-2));
        retry.setOnClickListener(v -> { if(lastUri != null && !busy) { saveCurrent(false); startRecognition(lastUri); } });
        options.addView(advanced); settings.setOnClickListener(v -> { boolean open=advanced.getVisibility()!=View.VISIBLE; advanced.setVisibility(open?View.VISIBLE:View.GONE); settings.setText(open?"ตัวเลือกการอ่าน  ⌃":"ตัวเลือกการอ่าน  ⌄"); });
        detailed.setOnCheckedChangeListener((b,c) -> persistSettings()); enhance.setOnCheckedChangeListener((b,c) -> persistSettings());
        AdapterView.OnItemSelectedListener selection = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p,View v,int position,long id) { persistSettings(); }
            public void onNothingSelected(AdapterView<?> p) { }
        };
        language.setOnItemSelectedListener(selection); layout.setOnItemSelectedListener(selection); persistSettings();
        readContent.addView(options); gap(readContent,14);
        LinearLayout statusRow = row(this); statusRow.setPadding(dp(this,4),dp(this,2),dp(this,4),dp(this,12));
        progress = new ProgressBar(this); progress.setVisibility(View.GONE); statusRow.addView(progress,new LinearLayout.LayoutParams(dp(this,26),dp(this,26)));
        status = text(this,"พร้อมอ่านเอกสาร • ผลลัพธ์บันทึกอัตโนมัติในเครื่อง",13,MUTED,false); status.setPadding(dp(this,8),0,0,0); statusRow.addView(status,new LinearLayout.LayoutParams(0,-2,1)); readContent.addView(statusRow);
        emptyResult = card(this); emptyResult.setGravity(android.view.Gravity.CENTER); gap(emptyResult,10);
        ImageView emptyIcon = new ImageView(this); emptyIcon.setImageResource(R.drawable.ic_document); emptyResult.addView(emptyIcon,new LinearLayout.LayoutParams(dp(this,36),dp(this,36)));
        gap(emptyResult,12); emptyResult.addView(text(this,"ข้อความของคุณเริ่มต้นที่นี่",16,INK,true));
        gap(emptyResult,4); TextView emptyHelp=text(this,"เมื่ออ่านเสร็จ จะตรวจแก้และเปิดดูย้อนหลังได้",13,MUTED,false); emptyHelp.setGravity(android.view.Gravity.CENTER); emptyResult.addView(emptyHelp); gap(emptyResult,10); readContent.addView(emptyResult);
        previewCard = card(this); previewCard.setVisibility(View.GONE);
        LinearLayout previewHeader=row(this); previewHeader.addView(text(this,"ภาพต้นฉบับ",15,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        Button toggle=AppStyle.button(this,"ซ่อน",false); previewHeader.addView(toggle); previewCard.addView(previewHeader); gap(previewCard,10);
        preview = new ImageView(this); preview.setAdjustViewBounds(true); preview.setMaxHeight(dp(this,230)); preview.setContentDescription("ภาพต้นฉบับสำหรับตรวจเทียบ"); previewCard.addView(preview,new LinearLayout.LayoutParams(-1,-2));
        toggle.setOnClickListener(v -> { boolean visible=preview.getVisibility()==View.VISIBLE; preview.setVisibility(visible?View.GONE:View.VISIBLE); toggle.setText(visible?"แสดง":"ซ่อน"); });
        TextView previewHelp=text(this,"PDF แสดงหน้าที่อ่านล่าสุด • คลังเก็บเฉพาะข้อความ",11,MUTED,false); gap(previewCard,8); previewCard.addView(previewHelp); readContent.addView(previewCard); gap(readContent,14);
        editorCard=card(this); editorCard.setVisibility(View.GONE);
        LinearLayout editorHeader=row(this); editorHeader.addView(text(this,"ข้อความที่อ่านได้",17,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        characters=text(this,"0 ตัวอักษร",11,MUTED,false); editorHeader.addView(characters); editorCard.addView(editorHeader); gap(editorCard,12);
        noteTitle=new EditText(this); noteTitle.setId(R.id.note_title); noteTitle.setHint("ตั้งชื่อบันทึก"); noteTitle.setTextSize(17); noteTitle.setTextColor(INK); noteTitle.setSingleLine(true); noteTitle.setPadding(dp(this,12),dp(this,12),dp(this,12),dp(this,12));
        noteTitle.setBackground(rounded(this,BACKGROUND,12,0)); editorCard.addView(noteTitle,new LinearLayout.LayoutParams(-1,-2)); gap(editorCard,8);
        saveState=text(this,"ข้อความจะบันทึกอัตโนมัติ",11,MUTED,false); editorCard.addView(saveState); gap(editorCard,12);
        output=new EditText(this); output.setId(R.id.note_body); output.setGravity(android.view.Gravity.TOP); output.setHint("ข้อความที่อ่านได้จะแสดงที่นี่ แก้ไขได้ตามต้องการ");
        output.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); output.setMinLines(5); output.setTextSize(17); output.setTextColor(INK); output.setLineSpacing(dp(this,4),1); output.setPadding(0,0,0,dp(this,8)); output.setBackgroundColor(Color.TRANSPARENT);
        editorCard.addView(output,new LinearLayout.LayoutParams(-1,-2));
        TextView reviewHelp=text(this,"พื้นเหลืองคือจุดที่ควรตรวจ • ตรวจชื่อและตัวเลขกับต้นฉบับเสมอ",11,MUTED,false); editorCard.addView(reviewHelp); gap(editorCard,16);
        LinearLayout actions=row(this); saveToLibrary=AppStyle.button(this,"บันทึก",true); saveToLibrary.setId(R.id.save_note); withIcon(saveToLibrary,R.drawable.ic_save,Color.WHITE);
        LinearLayout.LayoutParams primary=new LinearLayout.LayoutParams(0,-2,1); primary.setMarginEnd(dp(this,8)); actions.addView(saveToLibrary,primary);
        copy=AppStyle.button(this,"คัดลอก",false); actions.addView(copy,new LinearLayout.LayoutParams(0,-2,1)); editorCard.addView(actions); gap(editorCard,8);
        save=AppStyle.button(this,"ส่งออกเป็นไฟล์ .txt",false); editorCard.addView(save,new LinearLayout.LayoutParams(-1,-2));
        saveToLibrary.setOnClickListener(v -> saveCurrent(true)); copy.setOnClickListener(v -> {
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("OCR",output.getText().toString())); toast("คัดลอกข้อความแล้ว");
        });
        save.setOnClickListener(v -> exportText(output.getText().toString(),HistoryStore.normalizedTitle(noteTitle.getText().toString(),output.getText().toString())));
        readContent.addView(editorCard); gap(readContent,16); TextView privacy=text(this,"เอกสารและข้อความอยู่ในเครื่องของคุณ",12,MUTED,false); privacy.setGravity(android.view.Gravity.CENTER); readContent.addView(privacy);
        TextWatcher editing=new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { if(!suppressEdits) scheduleSave(); }
            public void afterTextChanged(Editable e) { }
        }; noteTitle.addTextChangedListener(editing); output.addTextChangedListener(editing);

        LinearLayout libraryHeading=row(this); libraryHeading.addView(text(this,"คลังข้อความ",26,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        Button fresh=AppStyle.button(this,"+ เอกสารใหม่",false); fresh.setOnClickListener(v -> { saveCurrent(false); clearEditor(); switchTab(false); choose.performClick(); }); libraryHeading.addView(fresh); libraryContent.addView(libraryHeading);
        libraryCount=text(this,"บันทึกที่เก็บไว้ในเครื่อง",13,MUTED,false); gap(libraryContent,6); libraryContent.addView(libraryCount); gap(libraryContent,18);
        search=new EditText(this); search.setId(R.id.library_search); search.setSingleLine(true); search.setTextSize(15); search.setTextColor(INK); search.setHint("ค้นหาชื่อหรือข้อความในบันทึก"); search.setPadding(dp(this,16),dp(this,14),dp(this,16),dp(this,14)); search.setBackground(rounded(this,Color.WHITE,14,LINE)); libraryContent.addView(search,new LinearLayout.LayoutParams(-1,-2)); gap(libraryContent,16);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { libraryLimit=40; handler.removeCallbacks(searchLibrary); handler.postDelayed(searchLibrary,200); }
            public void afterTextChanged(Editable e) { }
        });
        libraryList=column(this); libraryList.setId(R.id.library_list); libraryContent.addView(libraryList);
        loadMore=AppStyle.button(this,"โหลดบันทึกเพิ่มเติม",false); loadMore.setVisibility(View.GONE); loadMore.setOnClickListener(v -> { libraryLimit+=40; refreshLibrary(); }); libraryContent.addView(loadMore,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout bottom=row(this); bottom.setPadding(dp(this,20),dp(this,10),dp(this,20),dp(this,10)); bottom.setBackgroundColor(Color.WHITE);
        navRead=AppStyle.button(this,"อ่านข้อความ",false); navRead.setId(R.id.nav_read); withIcon(navRead,R.drawable.ic_scan,TEAL);
        navLibrary=AppStyle.button(this,"คลังข้อความ",false); navLibrary.setId(R.id.nav_library); withIcon(navLibrary,R.drawable.ic_library,TEAL);
        LinearLayout.LayoutParams nav=new LinearLayout.LayoutParams(0,-2,1); nav.setMarginEnd(dp(this,10)); bottom.addView(navRead,nav); bottom.addView(navLibrary,new LinearLayout.LayoutParams(0,-2,1)); root.addView(bottom);
        navRead.setOnClickListener(v -> switchTab(false)); navLibrary.setOnClickListener(v -> switchTab(true)); switchTab(false);
    }

    private void withIcon(Button button,int resource,int color) {
        Drawable icon=getDrawable(resource).mutate(); icon.setTint(color); icon.setBounds(0,0,dp(this,20),dp(this,20));
        button.setCompoundDrawables(icon,null,null,null); button.setCompoundDrawablePadding(dp(this,8));
    }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_SHORT).show(); }
    private void persistSettings() {
        if(language==null||layout==null) return;
        preferences.edit().putBoolean("quality",detailed.isChecked()).putBoolean("enhance",enhance.isChecked()).putInt("language",language.getSelectedItemPosition()).putInt("layout",layout.getSelectedItemPosition()).apply();
        settingsSummary.setText(String.valueOf(language.getSelectedItem())+" • "+String.valueOf(layout.getSelectedItem()));
    }
    private void pickFile() {
        saveCurrent(false);
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/jpeg","image/png","application/pdf"}); startActivityForResult(intent,PICK);
    }

    private void switchTab(boolean library) {
        if(busy) return;
        if(library) saveCurrent(false);
        libraryVisible=library;
        readScroll.setVisibility(library?View.GONE:View.VISIBLE);
        ((View)libraryContent.getParent()).setVisibility(library?View.VISIBLE:View.GONE);
        navRead.setBackgroundTintList(android.content.res.ColorStateList.valueOf(library?Color.WHITE:LIGHT));
        navLibrary.setBackgroundTintList(android.content.res.ColorStateList.valueOf(library?LIGHT:Color.WHITE));
        navRead.setSelected(!library); navLibrary.setSelected(library);
        if(library) refreshLibrary();
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(output.getWindowToken(),0);
    }
    private String formattedDate(long timestamp) { return new SimpleDateFormat("d MMM yyyy • HH:mm",Locale.getDefault()).format(new Date(timestamp)); }
    private void updateCharacters() { characters.setText(java.text.NumberFormat.getIntegerInstance().format(output.getText().length())+" ตัวอักษร"); }
    private void scheduleSave() {
        dirty=true; editRevision++; updateCharacters(); saveState.setText("กำลังบันทึกการแก้ไข…"); saveState.setTextColor(MUTED);
        handler.removeCallbacks(autosave); handler.postDelayed(autosave,700);
    }
    private String highlights() {
        JSONArray spans=new JSONArray(); Editable body=output.getText();
        for(BackgroundColorSpan span:body.getSpans(0,body.length(),BackgroundColorSpan.class)) {
            int start=body.getSpanStart(span),end=body.getSpanEnd(span);
            if(start>=0&&end>start&&end<=body.length()) spans.put(new JSONArray().put(start).put(end).put(span.getBackgroundColor()));
        }
        return spans.toString();
    }
    private CharSequence restoreHighlights(String body,String saved) {
        SpannableString result=new SpannableString(body);
        try {
            JSONArray spans=new JSONArray(saved);
            for(int i=0;i<spans.length();i++) {
                JSONArray span=spans.getJSONArray(i); int start=span.getInt(0),end=span.getInt(1);
                if(start>=0&&end>start&&end<=body.length()) result.setSpan(new BackgroundColorSpan(span.getInt(2)),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch(Exception ignored) { }
        return result;
    }
    private void saveCurrent(boolean manual) {
        handler.removeCallbacks(autosave);
        if(history==null||output==null||suppressEdits||closed) return;
        String body=output.getText().toString();
        if(currentNoteId==null&&body.trim().isEmpty()) { if(manual) toast("ยังไม่มีข้อความให้บันทึก"); return; }
        if(!dirty&&currentNoteId!=null) { if(manual) toast("บันทึกไว้ในเครื่องแล้ว"); return; }
        if(currentNoteId==null) { currentNoteId=UUID.randomUUID().toString(); currentCreated=System.currentTimeMillis(); }
        HistoryStore.Note note=new HistoryStore.Note(); note.id=currentNoteId; note.title=HistoryStore.normalizedTitle(noteTitle.getText().toString(),body);
        note.body=body; note.source=sourceLabel; note.created=currentCreated; note.updated=System.currentTimeMillis(); note.reviewCount=uncertainWords; note.highlights=highlights();
        int revision=editRevision; saveState.setText("กำลังบันทึก…"); saveState.setTextColor(MUTED);
        preferences.edit().putString("last_note",note.id).apply();
        storageExecutor.execute(() -> {
            try {
                history.save(note);
                runOnUiThread(() -> {
                    if(closed) return;
                    if(note.id.equals(currentNoteId)&&revision==editRevision) {
                        dirty=false; saveState.setText("บันทึกในเครื่องแล้ว • "+new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(note.updated))); saveState.setTextColor(TEAL);
                        if(noteTitle.getText().toString().trim().isEmpty()) { suppressEdits=true; noteTitle.setText(note.title); suppressEdits=false; }
                    }
                    if(manual) toast("บันทึกในคลังข้อความแล้ว");
                    if(libraryVisible) refreshLibrary();
                });
            } catch(Exception e) {
                runOnUiThread(() -> { if(!closed&&note.id.equals(currentNoteId)) { saveState.setText("บันทึกไม่สำเร็จ กรุณาลองอีกครั้ง"); saveState.setTextColor(Color.rgb(170,59,48)); if(manual) toast("บันทึกไม่สำเร็จ"); } });
            }
        });
    }
    private void openNote(String id,boolean announce) {
        saveCurrent(false); int generation=++loadGeneration;
        storageExecutor.execute(() -> {
            try {
                HistoryStore.Note note=history.get(id);
                runOnUiThread(() -> {
                    if(closed||generation!=loadGeneration) return;
                    if(note==null) { preferences.edit().remove("last_note").apply(); if(announce) toast("ไม่พบบันทึกนี้"); return; }
                    handler.removeCallbacks(autosave); suppressEdits=true;
                    currentNoteId=note.id; currentCreated=note.created; sourceLabel=note.source; uncertainWords=note.reviewCount;
                    noteTitle.setText(note.title); output.setText(restoreHighlights(note.body,note.highlights)); dirty=false; editRevision++;
                    suppressEdits=false; clearPreview(); lastUri=null; retry.setEnabled(false);
                    showEditor(); updateCharacters(); sourceName.setText(sourceLabel.isEmpty()?"ข้อความจากคลัง":sourceLabel); sourceName.setVisibility(View.VISIBLE);
                    saveState.setText("บันทึกในเครื่อง • "+formattedDate(note.updated)); saveState.setTextColor(TEAL);
                    status.setText("เปิดจากคลังข้อความ • แก้ไขแล้วบันทึกให้อัตโนมัติ"); preferences.edit().putString("last_note",id).apply();
                    switchTab(false); readScroll.post(() -> readScroll.scrollTo(0,Math.max(0,editorCard.getTop()-dp(this,8))));
                });
            } catch(Exception e) { runOnUiThread(() -> { if(!closed) toast("เปิดบันทึกไม่สำเร็จ"); }); }
        });
    }
    private void showEditor() {
        emptyResult.setVisibility(View.GONE); editorCard.setVisibility(View.VISIBLE);
        heroHeading.setVisibility(View.GONE); heroSubtitle.setVisibility(View.GONE); choose.setText("เลือกไฟล์ใหม่");
    }
    private void clearPreview() {
        preview.setImageDrawable(null); if(previewBitmap!=null) { previewBitmap.recycle(); previewBitmap=null; }
        previewCard.setVisibility(View.GONE);
    }
    private void clearEditor() {
        handler.removeCallbacks(autosave); ++loadGeneration; suppressEdits=true;
        currentNoteId=null; sourceLabel=""; pendingSourceLabel=""; currentCreated=0; dirty=false; editRevision++; uncertainWords=0;
        noteTitle.setText(""); output.setText(""); suppressEdits=false; lastUri=null; retry.setEnabled(false); clearPreview();
        sourceName.setVisibility(View.GONE); heroHeading.setVisibility(View.VISIBLE); heroSubtitle.setVisibility(View.VISIBLE);
        choose.setText("เลือกภาพหรือ PDF"); editorCard.setVisibility(View.GONE); emptyResult.setVisibility(View.VISIBLE);
        status.setText("พร้อมอ่านเอกสาร • ผลลัพธ์บันทึกอัตโนมัติในเครื่อง"); preferences.edit().remove("last_note").apply(); updateCharacters();
    }
    private String displayName(Uri uri) {
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(c!=null&&c.moveToFirst()) { String name=c.getString(0); if(name!=null&&!name.trim().isEmpty()) return name.replace('\n',' '); }
        } catch(Exception ignored) { }
        return "เอกสาร OCR";
    }
    private void acceptRecognition(CharSequence result) {
        ++loadGeneration; handler.removeCallbacks(autosave); suppressEdits=true;
        currentNoteId=null; currentCreated=System.currentTimeMillis(); sourceLabel=pendingSourceLabel;
        String title=sourceLabel.replaceFirst("(?i)\\.(pdf|png|jpe?g)$","");
        noteTitle.setText(title); output.setText(result); suppressEdits=false; editRevision++; dirty=recognizedCharacters>0;
        showEditor(); sourceName.setText(sourceLabel); sourceName.setVisibility(View.VISIBLE); updateCharacters();
        if(recognizedCharacters>0) saveCurrent(false); else saveState.setText("ไม่พบข้อความที่บันทึกได้");
        readScroll.post(() -> readScroll.smoothScrollTo(0,Math.max(0,editorCard.getTop()-dp(this,8))));
    }
    private void exportText(String body,String title) {
        if(body.trim().isEmpty()) { toast("ยังไม่มีข้อความให้ส่งออก"); return; }
        saveCurrent(false); pendingExport=body;
        String filename=title.replaceAll("[\\\\/:*?\"<>|]","_"); if(filename.length()>80) filename=filename.substring(0,80);
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE,filename+".txt"); startActivityForResult(intent,SAVE);
    }
    private void refreshLibrary() {
        if(closed) return;
        String query=search.getText().toString(); int limit=libraryLimit,generation=++queryGeneration;
        storageExecutor.execute(() -> {
            try {
                List<HistoryStore.Summary> notes=history.list(query,limit); int count=history.count(query);
                runOnUiThread(() -> { if(!closed&&generation==queryGeneration) renderLibrary(notes,count,query); });
            } catch(Exception e) { runOnUiThread(() -> { if(!closed) { libraryCount.setText("เปิดคลังข้อความไม่ได้ กรุณาลองอีกครั้ง"); } }); }
        });
    }
    private void renderLibrary(List<HistoryStore.Summary> notes,int count,String query) {
        libraryCount.setText(query.trim().isEmpty()?count+" บันทึก • เก็บไว้ในเครื่อง": "พบ "+count+" บันทึก");
        libraryList.removeAllViews(); loadMore.setVisibility(count>notes.size()?View.VISIBLE:View.GONE);
        if(notes.isEmpty()) {
            LinearLayout empty=card(this); empty.setGravity(android.view.Gravity.CENTER); gap(empty,16);
            ImageView icon=new ImageView(this); icon.setImageResource(R.drawable.ic_library); empty.addView(icon,new LinearLayout.LayoutParams(dp(this,40),dp(this,40))); gap(empty,14);
            empty.addView(text(this,query.trim().isEmpty()?"คลังข้อความยังว่างอยู่":"ไม่พบบันทึกที่ค้นหา",17,INK,true)); gap(empty,6);
            TextView help=text(this,query.trim().isEmpty()?"ผล OCR จะบันทึกที่นี่อัตโนมัติ\nเปิดกลับมาอ่านและแก้ไขได้ทุกเมื่อ":"ลองค้นหาด้วยชื่อหรือข้อความคำอื่น",13,MUTED,false); help.setGravity(android.view.Gravity.CENTER); empty.addView(help); gap(empty,14);
            libraryList.addView(empty); return;
        }
        for(HistoryStore.Summary note:notes) {
            LinearLayout card=card(this); LinearLayout heading=row(this);
            TextView title=text(this,note.title,17,INK,true); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END); heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            Button menu=AppStyle.button(this,"⋮",false); menu.setTextSize(23); menu.setContentDescription("เมนูบันทึก "+note.title); heading.addView(menu,new LinearLayout.LayoutParams(dp(this,46),dp(this,48))); card.addView(heading);
            gap(card,6); TextView snippet=text(this,note.snippet.isEmpty()?"ยังไม่มีข้อความ":note.snippet.replace('\n',' '),14,MUTED,false); snippet.setMaxLines(3); snippet.setEllipsize(TextUtils.TruncateAt.END); card.addView(snippet);
            gap(card,12); card.addView(text(this,formattedDate(note.updated)+"  •  "+java.text.NumberFormat.getIntegerInstance().format(note.characters)+" ตัวอักษร",11,MUTED,false));
            card.setOnClickListener(v -> openNote(note.id,true)); card.setContentDescription("เปิดบันทึก "+note.title);
            menu.setOnClickListener(v -> {
                PopupMenu popup=new PopupMenu(this,menu); popup.getMenu().add(0,1,0,"เปิดบันทึก"); popup.getMenu().add(0,2,1,"เปลี่ยนชื่อ"); popup.getMenu().add(0,3,2,"ส่งออก .txt"); popup.getMenu().add(0,4,3,"ลบบันทึก");
                popup.setOnMenuItemClickListener(item -> { if(item.getItemId()==1) openNote(note.id,true); else if(item.getItemId()==2) renameNote(note); else if(item.getItemId()==3) exportSaved(note.id); else deleteNote(note); return true; }); popup.show();
            });
            libraryList.addView(card); gap(libraryList,12);
        }
    }
    private void renameNote(HistoryStore.Summary note) {
        EditText name=new EditText(this); name.setSingleLine(true); name.setText(note.title); name.setSelectAllOnFocus(true);
        LinearLayout box=column(this); box.setPadding(dp(this,24),dp(this,8),dp(this,24),0); box.addView(name);
        new AlertDialog.Builder(this).setTitle("เปลี่ยนชื่อบันทึก").setView(box).setNegativeButton("ยกเลิก",null).setPositiveButton("บันทึก",(d,w) -> {
            String value=name.getText().toString().trim(); if(value.isEmpty()) { toast("กรุณาใส่ชื่อบันทึก"); return; }
            storageExecutor.execute(() -> { try { history.rename(note.id,value); runOnUiThread(() -> {
                if(closed) return; if(note.id.equals(currentNoteId)) { suppressEdits=true; noteTitle.setText(value); suppressEdits=false; }
                refreshLibrary(); toast("เปลี่ยนชื่อแล้ว");
            }); } catch(Exception e) { runOnUiThread(() -> { if(!closed) toast("เปลี่ยนชื่อไม่สำเร็จ"); }); } });
        }).show();
    }
    private void deleteNote(HistoryStore.Summary note) {
        new AlertDialog.Builder(this).setTitle("ลบบันทึกนี้?").setMessage("“"+note.title+"” จะถูกลบจากเครื่อง และเรียกคืนไม่ได้").setNegativeButton("เก็บไว้",null).setPositiveButton("ลบบันทึก",(d,w) -> {
            storageExecutor.execute(() -> { try { history.delete(note.id); runOnUiThread(() -> {
                if(closed) return; if(note.id.equals(currentNoteId)) clearEditor(); refreshLibrary(); toast("ลบบันทึกแล้ว");
            }); } catch(Exception e) { runOnUiThread(() -> { if(!closed) toast("ลบบันทึกไม่สำเร็จ"); }); } });
        }).show();
    }
    private void exportSaved(String id) {
        storageExecutor.execute(() -> { try { HistoryStore.Note note=history.get(id); runOnUiThread(() -> { if(!closed&&note!=null) exportText(note.body,note.title); }); }
            catch(Exception e) { runOnUiThread(() -> { if(!closed) toast("เปิดบันทึกไม่สำเร็จ"); }); } });
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
        saveCurrent(false);
        ++loadGeneration;
        pendingSourceLabel = displayName(uri);
        boolean quality = detailed.isChecked(), prepare = enhance.isChecked();
        String langs = new String[]{"tha+eng", "tha", "eng"}[language.getSelectedItemPosition()];
        int pageMode = new int[]{TessBaseAPI.PageSegMode.PSM_AUTO,
            TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT, TessBaseAPI.PageSegMode.PSM_SINGLE_LINE}[layout.getSelectedItemPosition()];
        busy = true; choose.setEnabled(false); save.setEnabled(false); copy.setEnabled(false);
        saveToLibrary.setEnabled(false); noteTitle.setEnabled(false); navRead.setEnabled(false); navLibrary.setEnabled(false);
        emptyResult.setVisibility(View.GONE); clearPreview();
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
                acceptRecognition(result);
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
                    saveToLibrary.setEnabled(true); noteTitle.setEnabled(true); navRead.setEnabled(true); navLibrary.setEnabled(true);
                    if(editorCard.getVisibility()!=View.VISIBLE) emptyResult.setVisibility(View.VISIBLE);
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
            preview.setVisibility(View.VISIBLE); previewCard.setVisibility(View.VISIBLE);
            if (previous != null) previous.recycle();
        });
    }

    @Override protected void onPause() {
        saveCurrent(false);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        saveCurrent(false); state.putString("noteId",currentNoteId);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        closed = true; executor.shutdown();
        handler.removeCallbacks(autosave); handler.removeCallbacks(searchLibrary);
        storageExecutor.execute(() -> history.close()); storageExecutor.shutdown();
        preview.setImageDrawable(null);
        if (previewBitmap != null) { previewBitmap.recycle(); previewBitmap = null; }
        super.onDestroy();
    }
}

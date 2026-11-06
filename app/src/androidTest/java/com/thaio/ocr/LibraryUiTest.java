package com.thaio.ocr;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LibraryUiTest {
    private final Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
    private void await(BooleanSupplier check) throws Exception {
        long deadline=System.currentTimeMillis()+12000;
        while(System.currentTimeMillis()<deadline) {
            AtomicBoolean done=new AtomicBoolean(); instrument.runOnMainSync(() -> done.set(check.getAsBoolean()));
            if(done.get()) return; Thread.sleep(100);
        }
        fail("UI did not reach expected state");
    }
    private void screenshot(Context c,String name) throws Exception {
        instrument.waitForIdleSync(); Thread.sleep(400);
        Bitmap bitmap=instrument.getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        File folder=c.getExternalFilesDir("screenshots"); assertNotNull(folder); folder.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); }
        bitmap.recycle();
    }
    private Activity launch(Context c) {
        Activity a=instrument.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); instrument.waitForIdleSync(); return a;
    }
    @Test public void libraryOpensEditsSavesAndRestoresAfterActivityRestart() throws Exception {
        Context c=instrument.getTargetContext(); HistoryStore store=new HistoryStore(c);
        String first="ui-fixture-first",second="ui-fixture-second"; Activity[] active={null};
        try {
            c.getSharedPreferences("read-dee",0).edit().remove("last_note").commit();
            HistoryStore.Note note=new HistoryStore.Note(); note.id=first; note.title="บันทึกการประชุม";
            note.body="ประชุมทีมวันจันทร์\nจำนวน 12 รายการ\nยอดรวม 1,250.50 บาท\nตรวจสอบข้อมูลก่อนส่ง"; note.source="บันทึกประชุม.png"; note.created=System.currentTimeMillis()-7200000; note.updated=System.currentTimeMillis(); note.highlights="[]"; store.save(note);
            HistoryStore.Note other=new HistoryStore.Note(); other.id=second; other.title="รายการสำหรับสัปดาห์นี้";
            other.body="ซื้อหนังสือ 2 เล่ม\nจัดเก็บไฟล์สำคัญ\nทบทวนบันทึกก่อนเริ่มงาน"; other.source="รายการ.pdf"; other.created=note.created; other.updated=note.updated-3600000; other.highlights="[]"; store.save(other);
            active[0]=launch(c); screenshot(c,"01-home");
            instrument.runOnMainSync(() -> active[0].findViewById(R.id.nav_library).performClick());
            await(() -> ((LinearLayout)active[0].findViewById(R.id.library_list)).getChildCount()>=4);
            screenshot(c,"02-library");
            instrument.runOnMainSync(() -> ((LinearLayout)active[0].findViewById(R.id.library_list)).getChildAt(0).performClick());
            await(() -> ((EditText)active[0].findViewById(R.id.note_title)).getText().toString().equals("บันทึกการประชุม"));
            screenshot(c,"03-editor");
            instrument.runOnMainSync(() -> {
                ((EditText)active[0].findViewById(R.id.note_title)).setText("บันทึกที่แก้ไขแล้ว");
                ((EditText)active[0].findViewById(R.id.note_body)).setText("แก้ไขและบันทึกแล้ว 1,250.50 บาท");
            });
            long until=System.currentTimeMillis()+12000;
            while(System.currentTimeMillis()<until&&!store.get(first).body.equals("แก้ไขและบันทึกแล้ว 1,250.50 บาท")) Thread.sleep(100);
            assertEquals("แก้ไขและบันทึกแล้ว 1,250.50 บาท",store.get(first).body);
            assertEquals("บันทึกที่แก้ไขแล้ว",store.get(first).title);
            instrument.runOnMainSync(() -> active[0].findViewById(R.id.save_note).performClick());
            instrument.runOnMainSync(() -> active[0].finish()); instrument.waitForIdleSync();
            active[0]=launch(c);
            await(() -> ((EditText)active[0].findViewById(R.id.note_body)).getText().toString().equals("แก้ไขและบันทึกแล้ว 1,250.50 บาท"));
            assertEquals(2,store.count(""));
            instrument.runOnMainSync(() -> active[0].findViewById(R.id.nav_library).performClick());
            await(() -> ((LinearLayout)active[0].findViewById(R.id.library_list)).getChildCount()>=4);
            instrument.runOnMainSync(() -> ((EditText)active[0].findViewById(R.id.library_search)).setText("1,250.50"));
            await(() -> ((LinearLayout)active[0].findViewById(R.id.library_list)).getChildCount()==2);
        } finally {
            if(active[0]!=null) instrument.runOnMainSync(() -> active[0].finish());
            store.delete(first); store.delete(second); store.close();
        }
    }
}

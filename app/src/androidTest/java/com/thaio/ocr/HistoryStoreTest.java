package com.thaio.ocr;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HistoryStoreTest {
    private HistoryStore.Note note(String id,String title,String body,long time) {
        HistoryStore.Note n=new HistoryStore.Note(); n.id=id; n.title=title; n.body=body; n.source="ตัวอย่าง.png";
        n.created=time; n.updated=time; n.reviewCount=1; n.highlights="[[0,3,-6758]]"; return n;
    }
    @Test public void persistsThaiEditsAcrossReopenWithoutDuplicating() {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext(); String db="history-test-"+UUID.randomUUID()+".db";
        HistoryStore store=new HistoryStore(c,db);
        try {
            HistoryStore.Note n=note("one","ทดสอบภาษาไทย","ข้อความไทย 1mg 113-122-129",100); store.save(n); store.close();
            store=new HistoryStore(c,db); HistoryStore.Note saved=store.get("one");
            assertEquals(n.body,saved.body); assertEquals(n.highlights,saved.highlights); assertEquals(100,saved.created);
            saved.body="แก้ไขข้อความแล้ว 0.5mg"; saved.updated=200; store.save(saved);
            assertEquals(1,store.count("")); assertEquals(saved.body,store.get("one").body); assertEquals(100,store.get("one").created);
            store.rename("one","ชื่อใหม่"); assertEquals("ชื่อใหม่",store.get("one").title); assertEquals(saved.body,store.get("one").body);
            saved=store.get("one"); saved.body=""; saved.updated=300; store.save(saved);
            assertEquals("",store.get("one").body);
            store.delete("one"); assertNull(store.get("one")); assertEquals(0,store.count(""));
        } finally { store.close(); c.deleteDatabase(db); }
    }
    @Test public void searchesLiteralSymbolsAndOrdersUpdatedNotes() {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext(); String db="history-test-"+UUID.randomUUID()+".db";
        HistoryStore store=new HistoryStore(c,db);
        try {
            store.save(note("a","บันทึกแรก","UREA 10% และเลข 001234",100));
            store.save(note("b","บันทึกสอง","Alpha_Beta / ข้อมูลใหม่",200));
            assertEquals(1,store.count("%")); assertEquals(1,store.count("_"));
            assertEquals("a",store.list("001234",40).get(0).id); assertEquals("b",store.list("alpha_beta",40).get(0).id);
            assertEquals("b",store.list("",1).get(0).id);
            HistoryStore.Note first=store.get("a"); first.updated=300; store.save(first);
            assertEquals("a",store.list("",1).get(0).id);
        } finally { store.close(); c.deleteDatabase(db); }
    }
}

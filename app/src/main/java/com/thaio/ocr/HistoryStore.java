package com.thaio.ocr;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** App-private, durable text library. Images are not copied into the library. */
public final class HistoryStore extends SQLiteOpenHelper {
    public static final class Note {
        public String id, title, body, source, highlights;
        public long created, updated;
        public int reviewCount;
    }
    public static final class Summary {
        public String id, title, snippet, source;
        public long updated;
        public int characters;
    }
    public HistoryStore(Context context) { this(context, "text-library.db"); }
    public HistoryStore(Context context, String name) { super(context, name, null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, source TEXT NOT NULL, created INTEGER NOT NULL, updated INTEGER NOT NULL, reviews INTEGER NOT NULL DEFAULT 0, highlights TEXT NOT NULL DEFAULT '[]')");
        db.execSQL("CREATE INDEX notes_updated ON notes(updated DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    public synchronized void save(Note note) {
        if (note.id == null || note.body == null) throw new IllegalArgumentException("Invalid note");
        ContentValues values = new ContentValues();
        values.put("title", normalizedTitle(note.title, note.body));
        values.put("body", note.body); values.put("source", note.source == null ? "" : note.source);
        values.put("updated", note.updated); values.put("reviews", note.reviewCount);
        values.put("highlights", note.highlights == null ? "[]" : note.highlights);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (db.update("notes", values, "id=?", new String[]{note.id}) == 0) {
                values.put("id", note.id); values.put("created", note.created);
                db.insertOrThrow("notes", null, values);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public static String normalizedTitle(String title, String body) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) {
            value = body == null ? "" : body.trim().split("\\R", 2)[0].trim();
            if (value.isEmpty()) value = "บันทึกข้อความ";
        }
        return value.length() > 100 ? value.substring(0, 100) : value;
    }
    public synchronized Note get(String id) {
        try (Cursor c = getReadableDatabase().query("notes", null, "id=?", new String[]{id}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            Note note = new Note(); note.id = id;
            note.title = c.getString(c.getColumnIndexOrThrow("title"));
            note.body = c.getString(c.getColumnIndexOrThrow("body"));
            note.source = c.getString(c.getColumnIndexOrThrow("source"));
            note.created = c.getLong(c.getColumnIndexOrThrow("created"));
            note.updated = c.getLong(c.getColumnIndexOrThrow("updated"));
            note.reviewCount = c.getInt(c.getColumnIndexOrThrow("reviews"));
            note.highlights = c.getString(c.getColumnIndexOrThrow("highlights"));
            return note;
        }
    }
    private String filter() { return "(instr(lower(title),lower(?))>0 OR instr(lower(body),lower(?))>0)"; }
    public synchronized List<Summary> list(String query, int limit) {
        List<Summary> result = new ArrayList<>();
        String search = query == null ? "" : query.trim();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,title,substr(body,1,170),source,updated,length(body) FROM notes WHERE " + filter() + " ORDER BY updated DESC,id LIMIT ?", new String[]{search, search, String.valueOf(Math.max(1, limit))})) {
            while (c.moveToNext()) {
                Summary summary = new Summary(); summary.id = c.getString(0); summary.title = c.getString(1);
                summary.snippet = c.getString(2); summary.source = c.getString(3);
                summary.updated = c.getLong(4); summary.characters = c.getInt(5); result.add(summary);
            }
        }
        return result;
    }
    public synchronized int count(String query) {
        String search = query == null ? "" : query.trim();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT count(*) FROM notes WHERE " + filter(), new String[]{search, search})) {
            c.moveToFirst(); return c.getInt(0);
        }
    }
    public synchronized void rename(String id, String title) {
        Note note = get(id);
        if (note == null) return;
        ContentValues values = new ContentValues(); values.put("title", normalizedTitle(title, note.body));
        values.put("updated", System.currentTimeMillis());
        getWritableDatabase().update("notes", values, "id=?", new String[]{id});
    }
    public synchronized void delete(String id) { getWritableDatabase().delete("notes", "id=?", new String[]{id}); }
}

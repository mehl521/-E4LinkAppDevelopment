package com.empatica.sample;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;

public class HeartRateDBHelper extends SQLiteOpenHelper {

    // Database Version
    private static final int DATABASE_VERSION = 1;

    // Database Name
    private static final String DATABASE_NAME = "HeartRateData";

    // Table name
    private static final String TABLE_HEART_RATE = "HeartRate";

    // Table Columns
    private static final String KEY_ID = "id";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_HEART_RATE = "heartRate";

    public HeartRateDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Creating Table
    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_HEART_RATE_TABLE = "CREATE TABLE " + TABLE_HEART_RATE + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + KEY_TIMESTAMP + " REAL,"
                + KEY_HEART_RATE + " REAL" + ")";
        db.execSQL(CREATE_HEART_RATE_TABLE);
    }

    // Upgrading Database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HEART_RATE);
        // Create tables again
        onCreate(db);
    }

    public void saveHeartRate(double timestamp, float heartRate) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            db.beginTransaction();

            ContentValues values = new ContentValues();
            values.put(KEY_TIMESTAMP, timestamp);
            values.put(KEY_HEART_RATE, heartRate);

            // Inserting Row
            long result = db.insert(TABLE_HEART_RATE, null, values);
            if (result == -1) {
                Log.e("HeartRateDBHelper", "Failed to insert heart rate data");
            } else {
                Log.d("HeartRateDBHelper", "Heart rate data inserted successfully");
            }

            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e("HeartRateDBHelper", "Error while trying to insert heart rate data", e);
        } finally {
            if (db != null) {
                db.endTransaction();
                db.close(); // Closing database connection
            }
        }
    }

    public List<Entry> getHeartRateData() {
        List<Entry> heartRateEntries = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            String selectQuery = "SELECT * FROM " + TABLE_HEART_RATE;
            db = this.getReadableDatabase();
            cursor = db.rawQuery(selectQuery, null);

            if (cursor.moveToFirst()) {
                do {
                    @SuppressLint("Range") float time = cursor.getFloat(cursor.getColumnIndex(KEY_TIMESTAMP));
                    @SuppressLint("Range") float heartRate = cursor.getFloat(cursor.getColumnIndex(KEY_HEART_RATE));

                    Log.d("HeartRateDBHelper", "Time: " + time + " Heart Rate: " + heartRate);

                    heartRateEntries.add(new Entry(time, heartRate));
                } while (cursor.moveToNext());
            } else {
                Log.d("HeartRateDBHelper", "No heart rate data found");
            }
        } catch (Exception e) {
            Log.e("HeartRateDBHelper", "Error while trying to fetch heart rate data", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null && db.isOpen()) {
                db.close();
            }
        }

        return heartRateEntries;
    }
}

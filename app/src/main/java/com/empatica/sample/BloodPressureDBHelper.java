package com.empatica.sample;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.List;

public class BloodPressureDBHelper extends SQLiteOpenHelper {

    // Database Version
    private static final int DATABASE_VERSION = 1;

    // Database Name
    private static final String DATABASE_NAME = "BloodPressureData";

    // Table name
    private static final String TABLE_BLOOD_PRESSURE = "BloodPressure";

    // Table Columns
    private static final String KEY_ID = "id";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_SYSTOLIC_BP = "systolicBP";
    private static final String KEY_DIASTOLIC_BP = "diastolicBP";

    public BloodPressureDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    // Creating Table
    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_BLOOD_PRESSURE_TABLE = "CREATE TABLE " + TABLE_BLOOD_PRESSURE + "("
                + KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KEY_TIMESTAMP + " REAL,"
                + KEY_SYSTOLIC_BP + " REAL,"
                + KEY_DIASTOLIC_BP + " REAL" + ")";
        db.execSQL(CREATE_BLOOD_PRESSURE_TABLE);
    }

    // Upgrading Database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BLOOD_PRESSURE);
        // Create tables again
        onCreate(db);
    }

    public void saveBloodPressure(double timestamp, double systolicBP, double diastolicBP) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            db.beginTransaction();

            ContentValues values = new ContentValues();
            values.put(KEY_TIMESTAMP, timestamp);
            values.put(KEY_SYSTOLIC_BP, systolicBP);
            values.put(KEY_DIASTOLIC_BP, diastolicBP);

            // Inserting Row
            long result = db.insert(TABLE_BLOOD_PRESSURE, null, values);
            if (result == -1) {
                // Handle failure
            } else {
                // Handle success
            }

            db.setTransactionSuccessful();
        } finally {
            if (db != null) {
                db.endTransaction();
                db.close(); // Closing database connection
            }
        }
    }

    public List<BloodPressureEntry> getBloodPressureData() {
        List<BloodPressureEntry> bpEntries = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            String selectQuery = "SELECT * FROM " + TABLE_BLOOD_PRESSURE;
            db = this.getReadableDatabase();
            cursor = db.rawQuery(selectQuery, null);

            if (cursor.moveToFirst()) {
                do {
                    @SuppressLint("Range") double timestamp = cursor.getDouble(cursor.getColumnIndex(KEY_TIMESTAMP));
                    @SuppressLint("Range") double systolicBP = cursor.getDouble(cursor.getColumnIndex(KEY_SYSTOLIC_BP));
                    @SuppressLint("Range") double diastolicBP = cursor.getDouble(cursor.getColumnIndex(KEY_DIASTOLIC_BP));

                    bpEntries.add(new BloodPressureEntry(timestamp, systolicBP, diastolicBP));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null && db.isOpen()) {
                db.close();
            }
        }

        return bpEntries;
    }

    public class BloodPressureEntry {
        private double timestamp;
        private double systolicBP;
        private double diastolicBP;

        public BloodPressureEntry(double timestamp, double systolicBP, double diastolicBP) {
            this.timestamp = timestamp;
            this.systolicBP = systolicBP;
            this.diastolicBP = diastolicBP;
        }

        public double getTimestamp() {
            return timestamp;
        }

        public double getSystolicBP() {
            return systolicBP;
        }

        public double getDiastolicBP() {
            return diastolicBP;
        }
    }
}

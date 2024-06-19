package com.empatica.sample;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import okio.Okio;
import okio.Source;

public class FileHandler {

    private static final String TAG = "FileHandler";

    public Source getFileSource(String filePath) {
        File file = new File(filePath);
        try {
            if (file.exists()) {
                InputStream inputStream = new FileInputStream(file);
                return Okio.source(inputStream);
            } else {
                // Log an error or notify the user
                Log.e(TAG, "File not found: " + filePath);
                // Optionally, create the file or take other actions
                if (createFile(file)) {
                    InputStream inputStream = new FileInputStream(file);
                    return Okio.source(inputStream);
                } else {
                    return null;
                }
            }
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Error opening file: " + e.getMessage());
            // Handle the error
            return null;
        }
    }

    private boolean createFile(File file) {
        try {
            boolean isFileCreated = file.createNewFile();
            if (isFileCreated) {
                Log.i(TAG, "File created: " + file.getAbsolutePath());
                // Optionally, write some default content to the file
                return true;
            } else {
                Log.e(TAG, "Failed to create file: " + file.getAbsolutePath());
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error creating file: " + e.getMessage());
            return false;
        }
    }
}

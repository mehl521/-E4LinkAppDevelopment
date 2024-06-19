package com.empatica.sample;

import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class HeartRateCalculator {
    protected List<Float> bvpDataList = new ArrayList<>();
    private float heartRate = 0.0f;
    private boolean ready = false;

    // Constants for signal processing
    private static final double LOW_HR_FREQ = 1.0; // ~60 BPM
    private static final double HIGH_HR_FREQ = 2.5; // ~150 BPM
    private static final double SAMPLING_RATE = 250.0; // Assuming 250Hz
    private static final int BUFFER_SIZE = 512; // Increased buffer size for better accuracy

    // Butterworth filter instance for heart rate
    private ButterworthFilter hrFilter = new ButterworthFilter(LOW_HR_FREQ, HIGH_HR_FREQ, SAMPLING_RATE, 2);

    // Receives BVP data and processes it to calculate heart rate.
    public void didReceiveBVP(float bvp, double timestamp) {
        bvpDataList.add(bvp);

        // Process data when buffer reaches the specified size
        if (bvpDataList.size() >= BUFFER_SIZE) {
            calculateHeartRate();
            ready = true;
            bvpDataList.clear();
        }
    }

    //Calculates the heart rate from the buffered BVP data.
    private void calculateHeartRate() {
        // Ensure sufficient data for processing
        if (bvpDataList.size() >= BUFFER_SIZE) {
            // Apply Butterworth filter
            double[] filteredData;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                filteredData = hrFilter.filter(bvpDataList.stream().mapToDouble(Float::doubleValue).toArray());
            } else {
                filteredData = new double[bvpDataList.size()];
                for (int i = 0; i < bvpDataList.size(); i++) {
                    filteredData[i] = bvpDataList.get(i);
                }
                filteredData = hrFilter.filter(filteredData);
            }

            // Detect peaks in the filtered data
            List<Integer> peakIndices = detectPeaks(filteredData);

            // Calculate heart rate based on the detected peaks
            heartRate = calculateHeartRateFromPeaks(peakIndices);
        }
    }

    private List<Integer> detectPeaks(double[] input) {
        List<Integer> peaks = new ArrayList<>();
        for (int i = 1; i < input.length - 1; i++) {
            if (input[i] > input[i - 1] && input[i] > input[i + 1] && input[i] > 0) { // Added threshold to reduce noise
                peaks.add(i);
            }
        }
        return peaks;
    }

    private float calculateHeartRateFromPeaks(List<Integer> peaks) {
        if (peaks.size() < 2) {
            Log.d("HeartRateCalculator", "Not enough peaks to calculate heart rate");
            return 0.0f; // Not enough peaks to calculate heart rate
        }

        float totalDiff = 0.0f;
        for (int i = 1; i < peaks.size(); i++) {
            totalDiff += (peaks.get(i) - peaks.get(i - 1));
        }
        float avgDiff = totalDiff / (peaks.size() - 1);

        if (avgDiff == 0.0f) {
            Log.d("HeartRateCalculator", "Average Difference is zero, cannot calculate heart rate");
            return 0.0f; // Avoid division by zero
        }

        return (float) (SAMPLING_RATE * 60 / avgDiff); // Return heart rate in beats per minute
    }

    public boolean isReady() {
        return ready;
    }


    //Retrieves the calculated heart rate.
    public float getHeartRate() {
        ready = false; // Reset the ready flag after reading the heart rate
        return heartRate;
    }
}

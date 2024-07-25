package com.empatica.sample;

import android.content.Context;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BloodPressureCalculator {
    private static final int BUFFER_SIZE = 64 * 20;
    private double[] bvpDataBuffer = new double[BUFFER_SIZE];
    private int bufferIndex = 0;
    private ButterworthFilter butterworthFilter;
    private boolean ready = false;
    private final Lock bufferLock = new ReentrantLock();

    private double latestSystolicBP = 0.0;
    private double latestDiastolicBP = 0.0;
    private int userAge = 0;

    public BloodPressureCalculator(Context context) {
        double lowCut = 0.8;
        double highCut = 4.4;
        double fs = 64.0; // Sampling frequency
        int order = 4;
        butterworthFilter = new ButterworthFilter(lowCut, highCut, fs, order);
    }

    public void setUserAge(int age) {
        this.userAge = age;
    }

    // Method to receive BVP (Blood Volume Pulse) data
    public void didReceiveBVP(float bvp) {
        bufferLock.lock();
        try {
            bvpDataBuffer[bufferIndex++] = bvp;
            if (bufferIndex >= BUFFER_SIZE) {
                calculateBloodPressure();
                ready = true;
                // Shift the buffer to retain continuity
                System.arraycopy(bvpDataBuffer, BUFFER_SIZE / 2, bvpDataBuffer, 0, BUFFER_SIZE / 2);
                bufferIndex = BUFFER_SIZE / 2;
            }
        } catch (Exception e) {
            Log.e("BloodPressureCalculator", "Error receiving BVP data", e);
        } finally {
            bufferLock.unlock();
        }
    }

    private void calculateBloodPressure() {
        try {
            // Apply Butterworth band-pass filter to the data
            double[] filteredData = butterworthFilter.filter(Arrays.copyOf(bvpDataBuffer, BUFFER_SIZE));

            // Extract Points of Interest (PINs)
            double[] pins = extractPins(filteredData);

            // Calculate Systolic and Diastolic BP for "All ages"
            double systolicBP_AllAges = -0.41 * (pins[0] + pins[1]) + 115.61;
            double diastolicBP_AllAges = 0.75 * (pins[0] + pins[1]) + 74.66;

            // Calculate Systolic and Diastolic BP for "20-40" age range
            double systolicBP_20_40 = 0.80 * (pins[0] + pins[1]) + 105.79;
            double diastolicBP_20_40 = 0.17 * (pins[0] + pins[1]) + 76.60;

            if (userAge >= 20 && userAge <= 40) {
                latestSystolicBP = systolicBP_20_40;
                latestDiastolicBP = diastolicBP_20_40;
            } else {
                latestSystolicBP = systolicBP_AllAges;
                latestDiastolicBP = diastolicBP_AllAges;
            }

            Log.d("BloodPressureCalculator", String.format("Systolic BP = %.2f, Diastolic BP = %.2f", latestSystolicBP, latestDiastolicBP));

        } catch (Exception e) {
            Log.e("BloodPressureCalculator", "Error calculating blood pressure", e);
            latestSystolicBP = 0.0;
            latestDiastolicBP = 0.0;
        }
    }

  /* x = med(sp+dp)  
  // Method to extract Points of Interest (PINs) from the BVP signal
    private double extractPins(double[] ppgSignal) {
        // Lists to store systolic and diastolic points
        List<Double> systolicPoints = new ArrayList<>();
        List<Double> diastolicPoints = new ArrayList<>();

        // Iterate over the signal to find systolic and diastolic points
        for (int i = 1; i < ppgSignal.length - 1; i++) {
            // Identify systolic points (local maxima)
            if (ppgSignal[i] > ppgSignal[i - 1] && ppgSignal[i] > ppgSignal[i + 1]) {
                systolicPoints.add(ppgSignal[i]);
            }
            // Identify diastolic points (local minima)
            if (ppgSignal[i] < ppgSignal[i - 1] && ppgSignal[i] < ppgSignal[i + 1]) {
                diastolicPoints.add(ppgSignal[i]);
            }
        }

        // Ensure equal number of points by truncating the longer list
        int size = Math.min(systolicPoints.size(), diastolicPoints.size());
        systolicPoints = systolicPoints.subList(0, size);
        diastolicPoints = diastolicPoints.subList(0, size);

        // Combine systolicPoints and diastolicPoints into a single list
        List<Double> combinedPoints = new ArrayList<>(systolicPoints);
        combinedPoints.addAll(diastolicPoints);

        // Calculate and return the median of the combined list
        return median(combinedPoints);
    }

    // Utility method to calculate the median of a list of numbers
    private double median(List<Double> data) {
        // Get the size of the list
        int size = data.size();

        // If the list is empty, return 0.0
        if (size == 0) return 0.0;

        // Sort the list to arrange the values in ascending order
        data.sort(Double::compareTo);

        // Calculate the median
        if (size % 2 == 0) {
            // If even number of elements, return the average of the two middle elements
            return (data.get(size / 2 - 1) + data.get(size / 2)) / 2.0;
        } else {
            // If odd number of elements, return the middle element
            return data.get(size / 2);
        }
    }
*/

//x = med(sp)+med(dp) 
    // Method to extract Points of Interest (PINs) from the BVP signal
    private double[] extractPins(double[] ppgSignal) {
        // Lists to store systolic and diastolic points
        List<Double> systolicPoints = new ArrayList<>();
        List<Double> diastolicPoints = new ArrayList<>();

        // Iterate over the signal to find systolic and diastolic points
        for (int i = 1; i < ppgSignal.length - 1; i++) {
            // Identify systolic points (local maxima)
            if (ppgSignal[i] > ppgSignal[i - 1] && ppgSignal[i] > ppgSignal[i + 1]) {
                systolicPoints.add(ppgSignal[i]);
            }
            // Identify diastolic points (local minima)
            if (ppgSignal[i] < ppgSignal[i - 1] && ppgSignal[i] < ppgSignal[i + 1]) {
                diastolicPoints.add(ppgSignal[i]);
            }
        }

        // Calculate and return the medians of the systolic and diastolic points
        return new double[]{median(systolicPoints), median(diastolicPoints)};
    }

    // Utility method to calculate the median of a list of numbers
    private double median(List<Double> data) {
        // Get the size of the list
        int size = data.size();

        // If the list is empty, return 0.0
        if (size == 0) return 0.0;

        // Sort the list to arrange the values in ascending order
        data.sort(Double::compareTo);

        // Calculate the median
        if (size % 2 == 0) {
            // If even number of elements, return the average of the two middle elements
            return (data.get(size / 2 - 1) + data.get(size / 2)) / 2.0;
        } else {
            // If odd number of elements, return the middle element
            return data.get(size / 2);
        }
    }



    // Method to check if the calculator is ready for the next calculation
    public boolean isReady() {
        return ready;
    }

    public double getSystolicBloodPressure() {
        return latestSystolicBP;
    }

    public double getDiastolicBloodPressure() {
        return latestDiastolicBP;
    }
}

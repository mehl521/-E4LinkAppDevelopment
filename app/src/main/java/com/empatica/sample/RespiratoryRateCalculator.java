package com.empatica.sample;

import android.os.Build;
import android.util.Log;

import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RespiratoryRateCalculator {
    // Buffer size to store BVP data, here 1280 samples (64 samples per second for 20 seconds)
    private static final int BUFFER_SIZE = 64 * 20;
    private double[] bvpDataBuffer = new double[BUFFER_SIZE];
    private int bufferIndex = 0;
    private final FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
    private float respiratoryRate = 0.0f;
    private boolean ready = false;
    private final Lock bufferLock = new ReentrantLock();

    // Constants for signal processing
    private static final double LOW_RR_FREQ = 0.1;   // Lower bound of respiratory rate frequency (0.1 Hz ~ 6 breaths per minute)
    private static final double HIGH_RR_FREQ = 0.5;  // Upper bound of respiratory rate frequency (0.5 Hz ~ 30 breaths per minute)
    private static final double SAMPLING_RATE = 64.0; // Assuming BVP data is sampled at 64Hz

    // Butterworth filter instance for respiratory rate
    private ButterworthFilter rrFilter = new ButterworthFilter(LOW_RR_FREQ, HIGH_RR_FREQ, SAMPLING_RATE, 2);

    // Method to receive BVP data
    public void didReceiveBVP(float bvp) {
        bufferLock.lock();
        try {
            bvpDataBuffer[bufferIndex++] = bvp; // Add new BVP value to buffer
            if (bufferIndex >= BUFFER_SIZE) { // If buffer is full
                calculateRespiratoryRate(); // Calculate respiratory rate
                ready = true;
                // Shift the buffer to retain continuity
                System.arraycopy(bvpDataBuffer, BUFFER_SIZE / 2, bvpDataBuffer, 0, BUFFER_SIZE / 2);
                bufferIndex = BUFFER_SIZE / 2;
            }
        } catch (Exception e) {
            Log.e("RespiRateCalculator", "Error receiving BVP data", e);
        } finally {
            bufferLock.unlock();
        }
    }

    // Method to calculate the respiratory rate from buffered BVP data
    private void calculateRespiratoryRate() {
        try {
            // Apply Butterworth band-pass filter to the data
            double[] filteredData = rrFilter.filter(Arrays.copyOf(bvpDataBuffer, BUFFER_SIZE));

            // Detect peaks and troughs in the filtered data
            List<Integer> peakIndices = detectPeaks(filteredData);
            List<Integer> troughIndices = detectTroughs(filteredData);
            peakIndices = filterPeaksAndTroughs(filteredData, peakIndices, true);
            troughIndices = filterPeaksAndTroughs(filteredData, troughIndices, false);

            // Feature extraction
            double am = calculateAmplitudeModulation(filteredData, peakIndices, troughIndices);
            double bw = calculateBaselineWander(filteredData, peakIndices, troughIndices);
            double fm = calculateFrequencyModulation(peakIndices);

            Log.d("RespiRateCalculator", "AM: " + am + ", BW: " + bw + ", FM: " + fm);

            // Estimate respiratory rate using count-orig method
            float countOrigRR = countOrigMethod(filteredData, peakIndices);

            // Fuse features to calculate final respiratory rate
            respiratoryRate = fuseFeatures(am, bw, fm, countOrigRR);

        } catch (Exception e) {
            Log.e("RespiRateCalculator", "Error calculating respiratory rate", e);
            respiratoryRate = 0.0f;
        }
    }

    // Method to detect peaks in the data
    private List<Integer> detectPeaks(double[] data) {
        List<Integer> peakIndices = new ArrayList<>();
        for (int i = 1; i < data.length - 1; i++) {
            if (data[i] > data[i - 1] && data[i] > data[i + 1]) {
                peakIndices.add(i);
            }
        }
        return peakIndices;
    }

    // Method to detect troughs in the data
    private List<Integer> detectTroughs(double[] data) {
        List<Integer> troughIndices = new ArrayList<>();
        for (int i = 1; i < data.length - 1; i++) {
            if (data[i] < data[i - 1] && data[i] < data[i + 1]) {
                troughIndices.add(i);
            }
        }
        return troughIndices;
    }

    // Filters peaks and troughs based on criteria.
    private List<Integer> filterPeaksAndTroughs(double[] data, List<Integer> indices, boolean isPeak) {
        // Calculate the mean of the data
        double mean = Arrays.stream(data).average().orElse(0.0);

        // Define the minimum interval between consecutive peaks or troughs (0.4 seconds)
        double minInterval = 0.4 * SAMPLING_RATE;

        // Initialize the list to store filtered indices
        List<Integer> filteredIndices = new ArrayList<>();

        // Iterate over all detected indices (peaks or troughs)
        for (int i = 0; i < indices.size(); i++) {
            // Check if the current index is a valid peak or trough
            // For peaks, the data value at the index should be greater than the mean
            // For troughs, the data value at the index should be less than the mean
            if ((isPeak && data[indices.get(i)] > mean) || (!isPeak && data[indices.get(i)] < mean)) {
                // Check if the list of filtered indices is empty or if the current index is sufficiently
                // far from the last added index
                if (filteredIndices.isEmpty() || indices.get(i) - filteredIndices.get(filteredIndices.size() - 1) > minInterval) {
                    // Add the current index to the list of filtered indices
                    filteredIndices.add(indices.get(i));
                }
            }
        }

        // Return the filtered indices
        return filteredIndices;
    }

    // Method to calculate amplitude modulation from the peaks and troughs
    private double calculateAmplitudeModulation(double[] data, List<Integer> peaks, List<Integer> troughs) {
        double amSum = 0.0;
        int count = 0;
        for (int i = 0; i < Math.min(peaks.size(), troughs.size()); i++) {
            amSum += Math.abs(data[peaks.get(i)] - data[troughs.get(i)]);
            count++;
        }
        return count == 0 ? 0 : amSum / count;
    }

    // Method to calculate baseline wander from the peaks and troughs
    private double calculateBaselineWander(double[] data, List<Integer> peaks, List<Integer> troughs) {
        double bwSum = 0.0;
        int count = 0;
        for (int i = 0; i < Math.min(peaks.size(), troughs.size()); i++) {
            bwSum += (data[peaks.get(i)] + data[troughs.get(i)]) / 2.0;
            count++;
        }
        return count == 0 ? 0 : bwSum / count;
    }

    // Method to calculate frequency modulation from the peaks
    private double calculateFrequencyModulation(List<Integer> peaks) {
        if (peaks.size() < 2) return 0;
        double[] intervals = new double[peaks.size() - 1];
        for (int i = 1; i < peaks.size(); i++) {
            intervals[i - 1] = peaks.get(i) - peaks.get(i - 1);
        }
        double meanInterval = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            meanInterval = Arrays.stream(intervals).average().orElse(0.0);
        }
        double variance = 0.0;
        for (double interval : intervals) {
            variance += Math.pow(interval - meanInterval, 2);
        }
        variance /= intervals.length;
        return Math.sqrt(variance);
    }

    // Count-orig method for respiratory rate estimation
    private float countOrigMethod(double[] data, List<Integer> peaks) {
        // Define threshold as 0.2 times the 75th percentile of peak values
        double threshold = 0.2 * percentile(peaks);
        int validBreaths = 0;

        for (int i = 1; i < peaks.size(); i++) {
            if (data[peaks.get(i)] > threshold && data[peaks.get(i - 1)] > threshold) {
                validBreaths++;
            }
        }

        double durationInMinutes = (double) data.length / SAMPLING_RATE / 60.0;
        return (float) (validBreaths / durationInMinutes);
    }

    // Helper method to calculate the nth percentile
    private double percentile(List<Integer> peaks) {
        double[] values = new double[peaks.size()];
        for (int i = 0; i < peaks.size(); i++) {
            values[i] = bvpDataBuffer[peaks.get(i)];
        }
        Arrays.sort(values);
        int index = (int) Math.ceil(75 / 100.0 * values.length);
        return values[Math.min(index, values.length - 1)];
    }

    /**
     * Fuses multiple features to calculate the final respiratory rate.
     *
     * @param am    amplitude modulation
     * @param bw    baseline wander
     * @param fm    frequency modulation
     * @param countOrigRR respiratory rate from count-orig method
     * @return the fused respiratory rate
     */
    private float fuseFeatures(double am, double bw, double fm, float countOrigRR) {
        // Example weights for each feature
        double weightAM = 0.5;
        double weightBW = 0.2;
        double weightFM = 0.2;
        double weightCountOrig = 0.1;

        // Weighted fusion
        return (float) ((weightAM * am) + (weightBW * bw) + (weightFM * fm) + (weightCountOrig * countOrigRR));
    }

    // Method to check if the respiratory rate is ready
    public boolean isReady() {
        return ready;
    }

    // Method to get the respiratory rate
    public float getRespiratoryRate() {
        ready = false;
        return respiratoryRate;
    }
}

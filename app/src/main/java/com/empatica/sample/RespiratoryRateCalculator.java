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
    private static final int BUFFER_SIZE = 64 * 20;
    private double[] bvpDataBuffer = new double[BUFFER_SIZE];
    private int bufferIndex = 0;
    private final FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
    private float respiratoryRate = 0.0f;
    private boolean ready = false;
    private final Lock bufferLock = new ReentrantLock();

    // Constants for signal processing
    private static final double LOW_RR_FREQ = 0.1;   // ~6 breaths per minute
    private static final double HIGH_RR_FREQ = 0.5;  // ~30 breaths per minute
    private static final double SAMPLING_RATE = 64.0; // Assuming 64Hz

    // Butterworth filter instance for respiratory rate
    private ButterworthFilter rrFilter = new ButterworthFilter(LOW_RR_FREQ, HIGH_RR_FREQ, SAMPLING_RATE, 2);

    public void didReceiveBVP(float bvp) {
        bufferLock.lock();
        try {
            bvpDataBuffer[bufferIndex++] = bvp;
            if (bufferIndex >= BUFFER_SIZE) {
                calculateRespiratoryRate();
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

    //Calculates the respiratory rate from the buffered BVP data.
    private void calculateRespiratoryRate() {
        try {
            // Apply Butterworth band-pass filter to the data
            double[] filteredData = rrFilter.filter(Arrays.copyOf(bvpDataBuffer, BUFFER_SIZE));

            // Peak and Trough Detection with criteria
            List<Integer> peakIndices = detectPeaks(filteredData);
            List<Integer> troughIndices = detectTroughs(filteredData);
            peakIndices = filterPeaksAndTroughs(filteredData, peakIndices, true);
            troughIndices = filterPeaksAndTroughs(filteredData, troughIndices, false);

            // Feature Extraction
            double am = calculateAmplitudeModulation(filteredData, peakIndices, troughIndices);
            double bw = calculateBaselineWander(filteredData, peakIndices, troughIndices);
            double fm = calculateFrequencyModulation(peakIndices);

            Log.d("RespiRateCalculator", "AM: " + am + ", BW: " + bw + ", FM: " + fm);

            // Respiratory Rate Estimation using Count-orig Method
            float countOrigRR = countOrigMethod(filteredData, peakIndices);

            // Fusion of features
            respiratoryRate = fuseFeatures(am, bw, fm, countOrigRR);

        } catch (Exception e) {
            Log.e("RespiRateCalculator", "Error calculating respiratory rate", e);
            respiratoryRate = 0.0f;
        }
    }

    // Detects peaks in the data.
    private List<Integer> detectPeaks(double[] data) {
        List<Integer> peakIndices = new ArrayList<>();
        for (int i = 1; i < data.length - 1; i++) {
            if (data[i] > data[i - 1] && data[i] > data[i + 1]) {
                peakIndices.add(i);
            }
        }
        return peakIndices;
    }

    // Detects troughs in the data.
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
        double mean = Arrays.stream(data).average().orElse(0.0);
        double minInterval = 0.4 * SAMPLING_RATE;

        List<Integer> filteredIndices = new ArrayList<>();
        for (int i = 0; i < indices.size(); i++) {
            if ((isPeak && data[indices.get(i)] > mean) || (!isPeak && data[indices.get(i)] < mean)) {
                if (filteredIndices.isEmpty() || indices.get(i) - filteredIndices.get(filteredIndices.size() - 1) > minInterval) {
                    filteredIndices.add(indices.get(i));
                }
            }
        }
        return filteredIndices;
    }

    //Calculates amplitude modulation from the peaks and troughs.
    private double calculateAmplitudeModulation(double[] data, List<Integer> peaks, List<Integer> troughs) {
        double amSum = 0.0;
        int count = 0;
        for (int i = 0; i < Math.min(peaks.size(), troughs.size()); i++) {
            amSum += Math.abs(data[peaks.get(i)] - data[troughs.get(i)]);
            count++;
        }
        return count == 0 ? 0 : amSum / count;
    }

    // Calculates baseline wander from the peaks and troughs.
    private double calculateBaselineWander(double[] data, List<Integer> peaks, List<Integer> troughs) {
        double bwSum = 0.0;
        int count = 0;
        for (int i = 0; i < Math.min(peaks.size(), troughs.size()); i++) {
            bwSum += (data[peaks.get(i)] + data[troughs.get(i)]) / 2.0;
            count++;
        }
        return count == 0 ? 0 : bwSum / count;
    }

    // Calculates frequency modulation from the peaks.
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

    // Count-orig method for respiratory rate estimation.
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

    public boolean isReady() {
        return ready;
    }

    public float getRespiratoryRate() {
        ready = false;
        return respiratoryRate;
    }
}

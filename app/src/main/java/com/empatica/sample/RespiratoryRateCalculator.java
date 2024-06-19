package com.empatica.sample;

import android.os.Build;
import android.util.Log;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RespiratoryRateCalculator {
    private static final int BUFFER_SIZE = 256;
    private double[] bvpDataBuffer = new double[BUFFER_SIZE];
    private int bufferIndex = 0;
    private FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
    private float respiratoryRate = 0.0f;
    private boolean ready = false;
    private final Lock bufferLock = new ReentrantLock();

    // Constants for signal processing
    private static final double LOW_RR_FREQ = 0.2;   // ~12 breaths per minute
    private static final double HIGH_RR_FREQ = 0.5;  // ~30 breaths per minute
    private static final double SAMPLING_RATE = 125.0; // Assuming 64Hz

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
            // Apply Butterworth filter to the data
            double[] filteredData = rrFilter.filter(Arrays.copyOf(bvpDataBuffer, BUFFER_SIZE));

            // Feature Extraction
            double am = calculateAmplitudeModulation(filteredData);
            double bw = calculateBaselineWander(filteredData);
            double fm = calculateFrequencyModulation(filteredData);

            Log.d("RespiRateCalculator", "AM: " + am + ", BW: " + bw + ", FM: " + fm);

            // Perform FFT on the filtered data
            Complex[] fftResult = transformer.transform(filteredData, TransformType.FORWARD);
            double[] magnitudes = new double[fftResult.length / 2];
            for (int i = 0; i < magnitudes.length; i++) {
                magnitudes[i] = fftResult[i].abs();
            }

            // Identify the dominant frequency within the respiratory rate range
            int lowIndex = (int) Math.round(LOW_RR_FREQ * filteredData.length / SAMPLING_RATE);
            int highIndex = (int) Math.round(HIGH_RR_FREQ * filteredData.length / SAMPLING_RATE);

            double maxMagnitude = 0;
            int maxIndex = -1;
            for (int i = lowIndex; i <= highIndex; i++) {
                if (i < magnitudes.length && magnitudes[i] > maxMagnitude) {
                    maxMagnitude = magnitudes[i];
                    maxIndex = i;
                }
            }

            // Calculate respiratory rate from the dominant frequency
            if (maxIndex != -1) {
                double dominantFreq = maxIndex * SAMPLING_RATE / (double) filteredData.length;
                float fftRR = (float) (dominantFreq * 60); // Convert to breaths per minute

                // Fusion of features
                respiratoryRate = fuseFeatures(am, bw, fm, fftRR);

                // Validate the calculated respiratory rate
                if (respiratoryRate < 12 || respiratoryRate > 50) {
                    Log.w("RespiRateCalculator", "Calculated respiratory rate out of realistic range: " + respiratoryRate);
                    respiratoryRate = 0.0f; // Set to 0 if the calculated rate is unrealistic
                }
            } else {
                Log.w("RespiRateCalculator", "No dominant frequency found in the respiratory rate range");
                respiratoryRate = 0.0f;
            }
        } catch (Exception e) {
            Log.e("RespiRateCalculator", "Error calculating respiratory rate", e);
            respiratoryRate = 0.0f;
        }
    }

    //Calculates amplitude modulation from the data.
    private double calculateAmplitudeModulation(double[] data) {
        double max = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            max = Arrays.stream(data).max().orElse(0.0);
        }
        double min = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            min = Arrays.stream(data).min().orElse(0.0);
        }
        return max - min;
    }

    // Calculates baseline wander from the data.
    private double calculateBaselineWander(double[] data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Arrays.stream(data).average().orElse(0.0);
        }
        return 0;
    }

    // Calculates frequency modulation from the data.
    private double calculateFrequencyModulation(double[] data) {
        double mean;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            mean = Arrays.stream(data).average().orElse(0.0);
        } else {
            mean = 0;
        }
        double variance = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            variance = Arrays.stream(data).map(d -> Math.pow(d - mean, 2)).sum() / data.length;
        }
        return Math.sqrt(variance);
    }

    /**
     * Fuses multiple features to calculate the final respiratory rate.
     *
     * @param am    amplitude modulation
     * @param bw    baseline wander
     * @param fm    frequency modulation
     * @param fftRR respiratory rate from FFT
     * @return the fused respiratory rate
     */
    private float fuseFeatures(double am, double bw, double fm, float fftRR) {
        // Example weights for each feature
        double weightAM = 0.3;
        double weightBW = 0.2;
        double weightFM = 0.2;
        double weightFFT = 0.3;

        // Weighted fusion
        return (float) ((weightAM * am) + (weightBW * bw) + (weightFM * fm) + (weightFFT * fftRR));
    }


    public boolean isReady() {
        return ready;
    }

    public float getRespiratoryRate() {
        ready = false;
        return respiratoryRate;
    }
}

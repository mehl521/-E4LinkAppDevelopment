package com.empatica.sample;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

public class BloodPressureCalculator {
    private List<Float> bvpDataList = new ArrayList<>();
    private List<Long> peakTimestamps = new ArrayList<>();
    private boolean ready = false;
    private final int minPeakDistance = 300;  // Minimum distance between peaks in milliseconds
    private final float peakThreshold = 0.5f;  // Minimum amplitude difference for a peak

    public BloodPressureCalculator() {
        // Constructor: Initialize any necessary components if needed
    }

    // Method to receive BVP (Blood Volume Pulse) data
    public synchronized void didReceiveBVP(float bvp) {
        bvpDataList.add(bvp);
        long timestamp = System.currentTimeMillis();
        peakDetection(bvp, timestamp);

        // If we have enough data, extract features and clear the list
        if (bvpDataList.size() >= 64) {
            double[] features = extractFeatures(bvpDataList);
            bvpDataList.clear();
            ready = true;

            // Debug log to see features
            System.out.println("Extracted features: " + java.util.Arrays.toString(features));

            // Print the value of PTT
            double ptt = features[5];
            System.out.println("Pulse Transit Time (PTT): " + ptt + " seconds");
        }
    }

    // Method to detect peaks in the BVP data
    private void peakDetection(float bvp, long timestamp) {
        int size = bvpDataList.size();
        // Ensure we have at least 3 points to compare
        if (size > 2) {
            float prevBvp = bvpDataList.get(size - 2);
            float prevPrevBvp = bvpDataList.get(size - 3);

            // Detect a peak
            if (prevBvp > prevPrevBvp && prevBvp > bvp) {
                // Only add the peak if sufficient time has passed since the last peak
                if (peakTimestamps.isEmpty() || (timestamp - peakTimestamps.get(peakTimestamps.size() - 1) > minPeakDistance)) {
                    if (Math.abs(prevBvp - prevPrevBvp) > peakThreshold && Math.abs(prevBvp - bvp) > peakThreshold) {
                        peakTimestamps.add(timestamp);
                        System.out.println("Peak detected at timestamp: " + timestamp);
                    }
                }
            }
        }

        // Keep only the last two peak timestamps
        if (peakTimestamps.size() > 2) {
            peakTimestamps.remove(0);
        }
    }

    // Method to extract features from the BVP data
    private double[] extractFeatures(List<Float> bvpDataList) {
        List<Float> smoothedData = smoothSignal(bvpDataList);
        double mean = calculateMean(smoothedData);
        double stdDev = calculateStandardDeviation(smoothedData, mean);
        double max = calculateMax(smoothedData);
        double min = calculateMin(smoothedData);
        double pulseAmplitude = max - min;
        double pulseWidthVariability = calculatePulseWidthVariability(smoothedData);
        double pulseRateVariability = calculatePulseRateVariability(smoothedData);
        double ptt = calculatePTT();
        double hr = calculateHeartRate(smoothedData); // Heart Rate as additional feature

        // Additional features
        double skewness = calculateSkewness(smoothedData, mean);
        double kurtosis = calculateKurtosis(smoothedData, mean);
        double[] frequencyFeatures = calculateFrequencyFeatures(smoothedData);

        // Combine all features into a single array
        double[] features = new double[10 + frequencyFeatures.length];
        features[0] = mean;
        features[1] = stdDev;
        features[2] = pulseAmplitude;
        features[3] = pulseWidthVariability;
        features[4] = pulseRateVariability;
        features[5] = ptt;
        features[6] = hr;
        features[7] = skewness;
        features[8] = kurtosis;
        System.arraycopy(frequencyFeatures, 0, features, 9, frequencyFeatures.length);

        return features;
    }


    // Method to calculate skewness
    private double calculateSkewness(List<Float> data, double mean) {
        double sum = 0.0;
        int n = data.size();
        for (float value : data) {
            sum += Math.pow(value - mean, 3);
        }
        return sum / (n * Math.pow(calculateStandardDeviation(data, mean), 3));
    }

    // Method to calculate kurtosis
    private double calculateKurtosis(List<Float> data, double mean) {
        double sum = 0.0;
        int n = data.size();
        for (float value : data) {
            sum += Math.pow(value - mean, 4);
        }
        return sum / (n * Math.pow(calculateStandardDeviation(data, mean), 4)) - 3;
    }

    // Method to calculate frequency features (example)
    // Method to calculate frequency features (example)
    private double[] calculateFrequencyFeatures(List<Float> data) {
        // Convert List<Float> to double[]
        double[] bvpArray = new double[data.size()];
        for (int i = 0; i < data.size(); i++) {
            bvpArray[i] = data.get(i);
        }

        // Ensure the length is a power of two
        int n = data.size();
        int powerOfTwoLength = Integer.highestOneBit(n) << 1; // Next power of two

        // Zero pad the array if necessary
        double[] paddedBvpArray = new double[powerOfTwoLength];
        System.arraycopy(bvpArray, 0, paddedBvpArray, 0, n);

        // Perform FFT
        FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] result = fft.transform(paddedBvpArray, TransformType.FORWARD);

        // Extract power spectrum
        double[] powerSpectrum = new double[result.length / 2];
        for (int i = 0; i < powerSpectrum.length; i++) {
            powerSpectrum[i] = result[i].abs();
        }

        // Extract features from power spectrum (e.g., power in different frequency bands)
        double lowFreqPower = 0;
        double midFreqPower = 0;
        double highFreqPower = 0;

        for (int i = 0; i < powerSpectrum.length; i++) {
            if (i < powerSpectrum.length / 3) {
                lowFreqPower += powerSpectrum[i];
            } else if (i < 2 * powerSpectrum.length / 3) {
                midFreqPower += powerSpectrum[i];
            } else {
                highFreqPower += powerSpectrum[i];
            }
        }

        return new double[]{lowFreqPower, midFreqPower, highFreqPower};
    }


    // Method to calculate Pulse Transit Time (PTT)
    private double calculatePTT() {
        if (peakTimestamps.size() < 2) {
            return 0.0;
        }
        long interval = peakTimestamps.get(1) - peakTimestamps.get(0);
        return interval / 1000.0;  // Convert milliseconds to seconds
    }

    // Method to calculate Heart Rate
    private double calculateHeartRate(List<Float> smoothedData) {
        int peakCount = peakTimestamps.size();
        if (peakCount < 2) {
            return 0.0;
        }
        long firstPeak = peakTimestamps.get(0);
        long lastPeak = peakTimestamps.get(peakCount - 1);
        double duration = (lastPeak - firstPeak) / 1000.0; // in seconds
        return (peakCount - 1) * 60 / duration; // beats per minute
    }

    // Method to get the systolic blood pressure
    public synchronized double getSystolicBloodPressure() {
        if (bvpDataList.isEmpty()) {
            return 0.0;
        }
        double[] features = extractFeatures(bvpDataList);
        return calculateSystolicBP(features);
    }

    // Method to get the diastolic blood pressure
    public synchronized double getDiastolicBloodPressure() {
        if (bvpDataList.isEmpty()) {
            return 0.0;
        }
        double[] features = extractFeatures(bvpDataList);
        return calculateDiastolicBP(features);
    }

    // Improved empirical formula to estimate systolic blood pressure
    private double calculateSystolicBP(double[] features) {
        double pulseAmplitude = features[2];
        double pulseWidthVariability = features[3];
        double pulseRateVariability = features[4];
        double ptt = features[5];
        double hr = features[6];

        return 110 + (0.6 * pulseAmplitude) - (0.4 * pulseWidthVariability) + (0.3 * pulseRateVariability) - (0.5 * ptt) + (0.2 * hr);
    }

    // Improved empirical formula to estimate diastolic blood pressure
    private double calculateDiastolicBP(double[] features) {
        double pulseAmplitude = features[2];
        double pulseWidthVariability = features[3];
        double pulseRateVariability = features[4];
        double ptt = features[5];
        double hr = features[6];

        return 70 + (0.4 * pulseAmplitude) - (0.3 * pulseWidthVariability) + (0.2 * pulseRateVariability) - (0.3 * ptt) + (0.1 * hr);
    }

    // Utility methods for statistical calculations
    private double calculateMean(List<? extends Number> data) {
        double sum = 0.0;
        for (Number value : data) {
            sum += value.doubleValue();
        }
        return sum / data.size();
    }

    private double calculateStandardDeviation(List<? extends Number> data, double mean) {
        double sum = 0.0;
        for (Number value : data) {
            sum += Math.pow(value.doubleValue() - mean, 2);
        }
        return Math.sqrt(sum / data.size());
    }

    private double calculateMax(List<? extends Number> data) {
        double max = Double.NEGATIVE_INFINITY;
        for (Number value : data) {
            if (value.doubleValue() > max) {
                max = value.doubleValue();
            }
        }
        return max;
    }

    private double calculateMin(List<? extends Number> data) {
        double min = Double.POSITIVE_INFINITY;
        for (Number value : data) {
            if (value.doubleValue() < min) {
                min = value.doubleValue();
            }
        }
        return min;
    }

    // Method to calculate Pulse Width Variability
    private double calculatePulseWidthVariability(List<Float> data) {
        List<Double> ibiList = new ArrayList<>();
        for (int i = 1; i < data.size(); i++) {
            ibiList.add((double) (data.get(i) - data.get(i - 1)));
        }
        return calculateStandardDeviation(ibiList, calculateMean(ibiList));
    }

    // Method to calculate Pulse Rate Variability
    private double calculatePulseRateVariability(List<Float> data) {
        List<Double> peakIntervals = new ArrayList<>();
        for (int i = 1; i < data.size(); i++) {
            peakIntervals.add((double) (data.get(i) - data.get(i - 1)));
        }
        return calculateStandardDeviation(peakIntervals, calculateMean(peakIntervals));
    }

    // Method to smooth the BVP signal using a moving average
    private List<Float> smoothSignal(List<Float> data) {
        List<Float> smoothedData = new ArrayList<>();
        int windowSize = 5;  // Adjust window size as necessary
        for (int i = 0; i < data.size(); i++) {
            if (i < windowSize / 2 || i >= data.size() - windowSize / 2) {
                smoothedData.add(data.get(i));  // Add the edge values directly
            } else {
                float sum = 0;
                for (int j = -windowSize / 2; j <= windowSize / 2; j++) {
                    sum += data.get(i + j);
                }
                smoothedData.add(sum / windowSize);
            }
        }
        return smoothedData;
    }

    // Method to check if the calculator is ready for the next calculation
    public boolean isReady() {
        return ready;
    }

    public static void main(String[] args) {
        // Example usage
        BloodPressureCalculator calculator = new BloodPressureCalculator();
        List<Float> sampleData = new ArrayList<>();
        // Populate sampleData with BVP values
        double[] frequencyFeatures = calculator.calculateFrequencyFeatures(sampleData);
        for (double feature : frequencyFeatures) {
            System.out.println("Frequency Feature: " + feature);
        }
    }

    // Method to reset the ready flag
    public void resetReadyFlag() {
        ready = false;
    }
}

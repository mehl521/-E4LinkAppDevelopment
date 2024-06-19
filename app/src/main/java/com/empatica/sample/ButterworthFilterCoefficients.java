package com.empatica.sample;

public class ButterworthFilterCoefficients {

    // Example coefficients for a Butterworth bandpass filter
    // These should be calculated for your specific LOW_FREQ, HIGH_FREQ, and SAMPLING_RATE
    private static final double[] A = {1.0, -1.8, 0.81};
    private static final double[] B = {0.1, 0.2, 0.1};

    public static double[] getA() {
        return A;
    }

    public static double[] getB() {
        return B;
    }
}

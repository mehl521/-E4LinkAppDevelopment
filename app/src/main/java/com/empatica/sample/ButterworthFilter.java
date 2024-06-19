package com.empatica.sample;

import uk.me.berndporr.iirj.Butterworth;

public class ButterworthFilter {

    private Butterworth butterworthFilter;
    private double[] z; // State variables

    public ButterworthFilter(double lowCut, double highCut, double fs, int order) {
        butterworthFilter = new Butterworth();
        butterworthFilter.bandPass(order, fs, (lowCut + highCut) / 2, (highCut - lowCut) / 2);
        z = new double[order]; // Initialize state variables based on filter order
    }

    public double[] filter(double[] data) {
        double[] output = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            output[i] = butterworthFilter.filter(data[i]);
        }
        return output;
    }
}

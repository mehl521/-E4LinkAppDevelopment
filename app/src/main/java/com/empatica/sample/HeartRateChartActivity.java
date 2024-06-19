package com.empatica.sample;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class HeartRateChartActivity extends AppCompatActivity {

    private static final String TAG = "HeartRateChartActivity";

    private LineChart hrChart;
    private LineChart respirationChart;
    private LineChart bpChart;

    private LineDataSet hrDataSet, respirationDataSet, bpSystolicDataSet, bpDiastolicDataSet;
    private LineData hrLineData, respirationLineData, bpLineData;

    private LinkedList<Entry> hrEntries, respirationEntries, bpSystolicEntries, bpDiastolicEntries;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateHRAndRespirationRunnable;
    private Runnable updateBPRunnable;
    private int hrAndRespirationUpdateInterval = 1000; // 1 second
    private int bpUpdateInterval = 3000; // 5 seconds
    private final int maxEntries = 3600; // 1 hour of data with 1-second interval

    private boolean isActivityVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_heart_rate_chart);

        // Retrieve data from intent
        List<Float> hrData = (List<Float>) getIntent().getSerializableExtra("HRData");
        List<Float> respirationData = (List<Float>) getIntent().getSerializableExtra("RespirationData");
        List<Double> bpSystolicData = (List<Double>) getIntent().getSerializableExtra("SystolicBPData");
        List<Double> bpDiastolicData = (List<Double>) getIntent().getSerializableExtra("DiastolicBPData");

        // Log retrieved data for debugging
        Log.d(TAG, "HR Data: " + hrData);
        Log.d(TAG, "Respiration Data: " + respirationData);
        Log.d(TAG, "BP Systolic Data: " + bpSystolicData);
        Log.d(TAG, "BP Diastolic Data: " + bpDiastolicData);

        hrChart = findViewById(R.id.hr_chart);
        respirationChart = findViewById(R.id.respiration_chart);
        bpChart = findViewById(R.id.bp_chart);

        Button backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(HeartRateChartActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // End the current activity and return to the main activity
        });

        setGraph(hrChart);
        setGraph(respirationChart);
        setGraph(bpChart);

        // Initialize data entries
        hrEntries = new LinkedList<>();
        respirationEntries = new LinkedList<>();
        bpSystolicEntries = new LinkedList<>();
        bpDiastolicEntries = new LinkedList<>();

        // Initialize data sets with different colors
        hrDataSet = new LineDataSet(hrEntries, "HR");
        hrDataSet.setColor(getResources().getColor(R.color.colorHR));
        hrDataSet.setLineWidth(2f);

        respirationDataSet = new LineDataSet(respirationEntries, "Respiration");
        respirationDataSet.setColor(getResources().getColor(R.color.colorRespiration));
        respirationDataSet.setLineWidth(2f);

        bpSystolicDataSet = new LineDataSet(bpSystolicEntries, "Systolic BP");
        bpSystolicDataSet.setColor(getResources().getColor(R.color.colorBPSystolic));
        bpSystolicDataSet.setLineWidth(2f);

        bpDiastolicDataSet = new LineDataSet(bpDiastolicEntries, "Diastolic BP");
        bpDiastolicDataSet.setColor(getResources().getColor(R.color.colorBPDiastolic));
        bpDiastolicDataSet.setLineWidth(2f);

        hrLineData = new LineData(hrDataSet);
        respirationLineData = new LineData(respirationDataSet);
        bpLineData = new LineData(bpSystolicDataSet, bpDiastolicDataSet);

        hrChart.setData(hrLineData);
        respirationChart.setData(respirationLineData);
        bpChart.setData(bpLineData);

        // Ensure initial data is shown
        addNewHRAndRespirationData(hrData, respirationData);
        addNewBPData(bpSystolicData, bpDiastolicData);

        // Set up the update mechanism for HR and respiration data
        updateHRAndRespirationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isActivityVisible) {
                    addNewHRAndRespirationData(hrData, respirationData);
                    handler.postDelayed(this, hrAndRespirationUpdateInterval);
                }
            }
        };

        // Set up the update mechanism for BP data
        updateBPRunnable = new Runnable() {
            @Override
            public void run() {
                if (isActivityVisible) {
                    addNewBPData(bpSystolicData, bpDiastolicData);
                    handler.postDelayed(this, bpUpdateInterval);
                }
            }
        };
    }

    private void setGraph(LineChart chart) {
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(true);

        Description desc = new Description();
        desc.setText("");
        chart.setDescription(desc);
    }

    private void addNewHRAndRespirationData(List<Float> hrData, List<Float> respirationData) {
        if (hrData == null || respirationData == null) {
            Log.e(TAG, "HR or Respiration data lists are null, cannot add new data");
            return;
        }

        int newIndex = hrEntries.size();

        if (newIndex < hrData.size() && newIndex < respirationData.size()) {
            hrEntries.add(new Entry(newIndex, hrData.get(newIndex)));
            respirationEntries.add(new Entry(newIndex, respirationData.get(newIndex)));

            if (hrEntries.size() > maxEntries) {
                hrEntries.removeFirst();
                respirationEntries.removeFirst();
                updateIndexes(hrEntries, respirationEntries);
            }

            hrDataSet.notifyDataSetChanged();
            respirationDataSet.notifyDataSetChanged();

            hrLineData.notifyDataChanged();
            respirationLineData.notifyDataChanged();
            hrChart.notifyDataSetChanged();
            respirationChart.notifyDataSetChanged();

            hrChart.invalidate();
            respirationChart.invalidate();

            hrChart.setVisibleXRangeMaximum(10);
            respirationChart.setVisibleXRangeMaximum(10);
            hrChart.moveViewToX(hrLineData.getEntryCount());
            respirationChart.moveViewToX(respirationLineData.getEntryCount());
        }
    }


    private void addNewBPData(List<Double> bpSystolicData, List<Double> bpDiastolicData) {
        if (bpSystolicData == null || bpDiastolicData == null) {
            Log.e(TAG, "BP Systolic or Diastolic data lists are null, cannot add new data");
            return; // Exit early if any of the data lists are null
        }

        int newIndex = bpSystolicEntries.size();

        if (newIndex < bpSystolicData.size() && newIndex < bpDiastolicData.size()) {
            // Apply smoothing to BP data
            float smoothedSystolicBP = smoothData(bpSystolicData);
            float smoothedDiastolicBP = smoothData(bpDiastolicData);

            bpSystolicEntries.add(new Entry(newIndex, smoothedSystolicBP));
            bpDiastolicEntries.add(new Entry(newIndex, smoothedDiastolicBP));

            // Ensure the size of the data entries does not exceed the maximum limit
            if (bpSystolicEntries.size() > maxEntries) {
                bpSystolicEntries.removeFirst();
                bpDiastolicEntries.removeFirst();
                updateIndexes(bpSystolicEntries, bpDiastolicEntries);
            }

            // Notify the data set of the change
            bpSystolicDataSet.notifyDataSetChanged();
            bpDiastolicDataSet.notifyDataSetChanged();

            // Notify the chart data of the change and refresh the chart
            bpLineData.notifyDataChanged();
            bpChart.notifyDataSetChanged();

            // Invalidate the chart to redraw with new data
            bpChart.invalidate();

            // Scroll to the last entry
            bpChart.setVisibleXRangeMaximum(10);
            bpChart.moveViewToX(bpLineData.getEntryCount());
        }
    }

    private float smoothData(List<Double> data) {
        int size = data.size();
        if (size < 3) {
            return data.get(size - 1).floatValue();
        }

        double last = data.get(size - 1);
        double secondLast = data.get(size - 2);
        double thirdLast = data.get(size - 3);

        // Apply moving average smoothing
        return (float) ((last + secondLast + thirdLast) / 3);
    }


    private void updateIndexes(LinkedList<Entry>... entriesLists) {
        for (LinkedList<Entry> entries : entriesLists) {
            for (int i = 0; i < entries.size(); i++) {
                entries.get(i).setX(i);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        isActivityVisible = true;
        handler.post(updateHRAndRespirationRunnable);
        handler.post(updateBPRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        isActivityVisible = false;
        handler.removeCallbacks(updateHRAndRespirationRunnable);
        handler.removeCallbacks(updateBPRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateHRAndRespirationRunnable);
        handler.removeCallbacks(updateBPRunnable);
    }
}

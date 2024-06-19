package com.empatica.sample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.EmpaticaDevice;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSION_ACCESS_FINE_LOCATION = 1;

    private EmpaDeviceManager deviceManager;

    private TextView accel_xLabel;
    private TextView bvpLabel;
    private TextView edaLabel;
    private TextView ibiLabel;
    private TextView statusLabel;

    private TextView deviceNameLabel;
    private TextView temperatureLabel;
    private TextView batteryLabel;
    private LinearLayout dataCnt;
    private TextView heartRateLabel;
    private TextView respirationRateLabel;
    private TextView systolicPressureLabel;
    private TextView diastolicPressureLabel;
    private Boolean isConnected = false;
    private TextView wristStatusLabel;
    private Button toggleStreamingButton;
    private boolean isStreaming = false;

    private HeartRateCalculator heartRateCalculator;
    private RespiratoryRateCalculator respiratoryRateCalculator;
    private BloodPressureCalculator bloodPressureCalculator;
    private List<Float> hrData = new ArrayList<>(); // List to store heart rate data
    private List<Float> respirationData = new ArrayList<>(); // List to store respiration rate data
    private List<Double> systolicBPData = new ArrayList<>(); // List to store systolic blood pressure data
    private List<Double> diastolicBPData = new ArrayList<>(); // List to store diastolic blood pressure data

    private boolean isDeviceManagerInitialized = false;
    private EmpaStatus currentStatus = EmpaStatus.DISCONNECTED; // Track the current status

    private BloodPressureDBHelper bloodPressureDBHelper; // Add instance of BloodPressureDBHelper


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize vars that reference UI components
        statusLabel = findViewById(R.id.status_label);
        accel_xLabel = findViewById(R.id.accel_x);
        bvpLabel = findViewById(R.id.bvp);
        edaLabel = findViewById(R.id.eda);
        ibiLabel = findViewById(R.id.ibi);
        temperatureLabel = findViewById(R.id.temperature);
        batteryLabel = findViewById(R.id.battery);
        heartRateLabel = findViewById(R.id.heartrate);
        respirationRateLabel = findViewById(R.id.respiration_rate);
        systolicPressureLabel = findViewById(R.id.bp_systolic);
        diastolicPressureLabel = findViewById(R.id.bp_diastolic);
        wristStatusLabel = findViewById(R.id.wrist_status_label);
        dataCnt = findViewById(R.id.dataArea);

        final Button connectButton = findViewById(R.id.connectButton);
        connectButton.setOnClickListener(v -> toggleConnection());

        Button showChartButton = findViewById(R.id.show_chart_button);
        showChartButton.setOnClickListener(v -> showChart());

        heartRateCalculator = new HeartRateCalculator();
        respiratoryRateCalculator = new RespiratoryRateCalculator();
        bloodPressureCalculator = new BloodPressureCalculator();
        bloodPressureDBHelper = new BloodPressureDBHelper(MainActivity.this); // Initialize BloodPressureDBHelper

        checkPermissionsAndInitialize();
        Intent intent = new Intent(this, BluetoothService.class);
        startService(intent); // Ensure the service is running even if the activity is not bound
    }

    private void toggleConnection() {
        if (!isConnected) {
            if (isDeviceManagerInitialized) {
                deviceManager = EmpaManagerSingleton.getInstance(MainActivity.this, MainActivity.this, MainActivity.this).getDeviceManager();
                if (deviceManager != null && currentStatus == EmpaStatus.READY) {
                    deviceManager.startScanning();
                    isConnected = true;
                    updateConnectButtonText();
                } else {
                    Log.e(TAG, "DeviceManager is not ready, cannot start scanning");
                    Toast.makeText(MainActivity.this, "Device Manager is not ready", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "DeviceManager is not initialized, cannot start scanning");
                Toast.makeText(MainActivity.this, "Device Manager is not initialized", Toast.LENGTH_SHORT).show();
            }
        } else {
            EmpaManagerSingleton.getInstance(MainActivity.this, MainActivity.this, MainActivity.this).disconnect();
            isConnected = false;
            updateConnectButtonText();
        }
    }

    private void updateConnectButtonText() {
        Button connectButton = findViewById(R.id.connectButton);
        if (isConnected) {
            connectButton.setText("Disconnect");
        } else {
            connectButton.setText("Connect");
        }
    }

    private void checkPermissionsAndInitialize() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN}, REQUEST_PERMISSION_ACCESS_FINE_LOCATION);
        } else {
            // All permissions are granted, initialize the device manager
            initializeDeviceManager();
        }
    }

    private void initializeDeviceManager() {
        deviceManager = EmpaManagerSingleton.getInstance(this, this, this).getDeviceManager();
        isDeviceManagerInitialized = true;
    }

    private void showChart() {
        Intent intent = new Intent(MainActivity.this, HeartRateChartActivity.class);
        intent.putExtra("HRData", (Serializable) hrData);
        intent.putExtra("RespirationData", (Serializable) respirationData);
        intent.putExtra("SystolicBPData", (Serializable) systolicBPData);
        intent.putExtra("DiastolicBPData", (Serializable) diastolicBPData);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_ACCESS_FINE_LOCATION) {
            boolean allPermissionsGranted = true;
            if (grantResults.length > 0) {
                for (int grantResult : grantResults) {
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false;
                        break;
                    }
                }
            } else {
                allPermissionsGranted = false;
            }

            if (allPermissionsGranted) {
                // Permissions were granted, initialize the device manager
                initializeDeviceManager();
            } else {
                // If the user denies the permission, check if any permission is denied permanently
                boolean needRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_CONNECT)
                        || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_SCAN);

                DialogInterface.OnClickListener retryButtonClickListener = (dialog, which) -> {
                    // try again
                    if (needRationale) {
                        // Retry permission request
                        checkPermissionsAndInitialize();
                    } else {
                        // Navigate to app's settings page
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivity(intent);
                    }
                };

                DialogInterface.OnClickListener exitButtonClickListener = (dialog, which) -> {
                    // Exit the application
                    finish();
                };

                // Show a dialog informing the user about the need for permissions
                new AlertDialog.Builder(this)
                        .setTitle("Permission required")
                        .setMessage("Without these permissions, the app cannot find Bluetooth low energy devices. Please allow them.")
                        .setPositiveButton("Retry", retryButtonClickListener)
                        .setNegativeButton("Exit application", exitButtonClickListener)
                        .show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (deviceManager != null) {
            deviceManager.stopScanning();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceManager != null) {
            deviceManager.cleanUp();
        }
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        updateLabel(accel_xLabel, "" + x);
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        updateLabel(bvpLabel, "" + bvp);

        // Process BVP data for heart rate and respiratory rate calculation
        heartRateCalculator.didReceiveBVP(bvp, timestamp);
        respiratoryRateCalculator.didReceiveBVP(bvp);
        bloodPressureCalculator.didReceiveBVP(bvp);

        // Check if heart rate calculator is ready to calculate heart rate
        if (heartRateCalculator.isReady()) {
            // Get calculated heart rate
            float heartRate = heartRateCalculator.getHeartRate();

            // Add heart rate to the list for chart display
            hrData.add(heartRate);

            // Update UI with heart rate
            updateLabel(heartRateLabel, String.format("%.2f BPM", heartRate));

            // Save the heart rate data to the database
            HeartRateDBHelper dbHelper = new HeartRateDBHelper(MainActivity.this);
            dbHelper.saveHeartRate(timestamp, heartRate);
        }

        // Check if respiratory rate calculator is ready to calculate respiratory rate
        if (respiratoryRateCalculator.isReady()) {
            // Get calculated respiratory rate
            float respiratoryRate = respiratoryRateCalculator.getRespiratoryRate();

            // Add respiratory rate to the list for chart display
            respirationData.add(respiratoryRate);

            // Update UI with respiratory rate
            updateLabel(respirationRateLabel, String.format("%.2f breaths/min", respiratoryRate));
        }

        // Check if blood pressure calculator is ready to estimate blood pressure
        if (bloodPressureCalculator.isReady()) {
            // Get estimated blood pressure
            double systolicBP = bloodPressureCalculator.getSystolicBloodPressure();
            double diastolicBP = bloodPressureCalculator.getDiastolicBloodPressure();

            // Add blood pressure data to the list for chart display
            systolicBPData.add(systolicBP);
            diastolicBPData.add(diastolicBP);

            // Update UI with blood pressure values
            updateBloodPressureUI(systolicBP, diastolicBP);

            // Save the blood pressure data to the database
            bloodPressureDBHelper.saveBloodPressure(timestamp, systolicBP, diastolicBP);
        }
    }

    private void updateBloodPressureUI(double systolicBP, double diastolicBP) {
        updateLabel(systolicPressureLabel, String.format("%.2f mmHg", systolicBP));
        updateLabel(diastolicPressureLabel, String.format("%.2f mmHg", diastolicBP));
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        updateLabel(edaLabel, "" + gsr);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        Log.d(TAG, "IBI: " + ibi + " at timestamp: " + timestamp);
        updateLabel(ibiLabel, "" + ibi);
    }

    @Override
    public void didReceiveTemperature(float temp, double timestamp) {
        updateLabel(temperatureLabel, "" + temp);
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        Log.d(TAG, "Battery level: " + battery * 100 + "% at timestamp: " + timestamp);
        updateLabel(batteryLabel, String.format("%.0f %%", battery * 100));
    }


    @Override
    public void didReceiveTag(double timestamp) {

    }

    @Override
    public void didEstablishConnection() {
        // Connection established
        dataCnt.setVisibility(View.VISIBLE);
        wristStatusLabel.setVisibility(View.VISIBLE);
    }

    @Override
    public void didUpdateSensorStatus(@EmpaSensorStatus int status, EmpaSensorType type) {
        didUpdateOnWristStatus(status);
    }

    @Override
    public void didUpdateStatus(EmpaStatus status) {
        currentStatus = status; // Update the current status
        updateLabel(statusLabel, status.name());

        if (status == EmpaStatus.READY) {
            hide();
        } else if (status == EmpaStatus.CONNECTED) {
            show();
        } else if (status == EmpaStatus.DISCONNECTED) {
            updateLabel(deviceNameLabel, "");
            hide();
        }
    }

    @Override
    public void didUpdateOnWristStatus(@EmpaSensorStatus final int status) {
        String text = status == EmpaSensorStatus.ON_WRIST ? "ON WRIST" : "NOT ON WRIST";
        updateLabel(wristStatusLabel, text);
    }

    @Override
    public void didDiscoverDevice(EmpaticaDevice device, String deviceName, int rssi, boolean allowed) {
        Log.i(TAG, "didDiscoverDevice: " + deviceName + " allowed: " + allowed);

        if (allowed) {
            EmpaManagerSingleton.getInstance(this, this, this).stopScanning();
            EmpaManagerSingleton.getInstance(this, this, this).connectDevice(device);
        }
    }

    @Override
    public void didFailedScanning(int errorCode) {
        Log.e(TAG, "Failed scanning with error code: " + errorCode);
    }

    @Override
    public void didRequestEnableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    public void bluetoothStateChanged() {
        boolean isBluetoothOn = BluetoothAdapter.getDefaultAdapter().isEnabled();
        Log.i(TAG, "Bluetooth State Changed: " + isBluetoothOn);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // Handle the case where the user chose not to enable Bluetooth
            Toast.makeText(this, "Bluetooth is required to use this app", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void updateLabel(final TextView label, final String text) {
        runOnUiThread(() -> {
            if (label != null) {
                label.setText(text);
            }
        });
    }

    void show() {
        runOnUiThread(() -> dataCnt.setVisibility(View.VISIBLE));
    }

    void hide() {
        runOnUiThread(() -> dataCnt.setVisibility(View.INVISIBLE));
    }
}

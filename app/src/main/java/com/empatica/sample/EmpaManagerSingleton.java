package com.empatica.sample;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.EmpaticaDevice;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

public class EmpaManagerSingleton {

    private static final String TAG = "EmpaManagerSingleton";
    private static final String EMPATICA_API_KEY = "3dae274e320b4cfd97acad923ab0f777"; // TODO insert your API Key here

    private static EmpaManagerSingleton instance;
    private EmpaDeviceManager deviceManager;
    private Context context;
    private EmpaStatusDelegate statusDelegate;
    private EmpaDataDelegate dataDelegate;

    private EmpaManagerSingleton(Context context, EmpaStatusDelegate statusDelegate, EmpaDataDelegate dataDelegate) {
        this.context = context.getApplicationContext();
        this.statusDelegate = statusDelegate;
        this.dataDelegate = dataDelegate;
        initDeviceManager();
    }

    public static synchronized EmpaManagerSingleton getInstance(Context context, EmpaStatusDelegate statusDelegate, EmpaDataDelegate dataDelegate) {
        if (instance == null) {
            instance = new EmpaManagerSingleton(context, statusDelegate, dataDelegate);
        }
        return instance;
    }

    private void initDeviceManager() {
        if (TextUtils.isEmpty(EMPATICA_API_KEY)) {
            Log.e(TAG, "3dae274e320b4cfd97acad923ab0f777");
            return;
        }

        deviceManager = new EmpaDeviceManager(context, dataDelegate, statusDelegate);
        deviceManager.authenticateWithAPIKey(EMPATICA_API_KEY);
    }

    public EmpaDeviceManager getDeviceManager() {
        return deviceManager;
    }

    public void startScanning() {
        if (deviceManager != null) {
            deviceManager.startScanning();
        } else {
            Log.e(TAG, "DeviceManager is not initialized, cannot start scanning");
        }
    }

    public void stopScanning() {
        if (deviceManager != null) {
            deviceManager.stopScanning();
        }
    }

    public void connectDevice(EmpaticaDevice device) {
        try {
            if (deviceManager != null) {
                deviceManager.connectDevice(device);
            }
        } catch (ConnectionNotAllowedException e) {
            Log.e(TAG, "ConnectionNotAllowedException", e);
            Toast.makeText(context, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
        }
    }

    public void disconnect() {
        if (deviceManager != null) {
            deviceManager.disconnect();
        }
    }

    public void cleanUp() {
        if (deviceManager != null) {
            deviceManager.cleanUp();
        }
    }
}

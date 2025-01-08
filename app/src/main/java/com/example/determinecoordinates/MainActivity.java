package com.example.determinecoordinates;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private WifiManager wifiManager;
    private ListView listView;
    private WifiListAdapter wifiListAdapter;

    private boolean isAutoModeEnabled = false; // Trạng thái Auto Mode
    private Handler autoHandler; // Handler để quản lý Auto Mode
    private Runnable autoTask; // Tác vụ Auto Mode
    private Runnable countdownTask; // Tác vụ đếm ngược
    private int countdownTime = 30; // Thời gian đếm ngược 30 giây

    private Button buttonToggleAutoMode;
    private Button buttonSendWifiInformation;
    private Button buttonRescan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        autoHandler = new Handler();

        listView = findViewById(R.id.listView);
        buttonToggleAutoMode = findViewById(R.id.buttonToggleAutoMode);
        buttonRescan = findViewById(R.id.buttonRescan);
        buttonSendWifiInformation = findViewById(R.id.buttonSendWifiInformation);

        buttonToggleAutoMode.setOnClickListener(v -> toggleAutoMode());
        buttonRescan.setOnClickListener(v -> {
            scanWifiNetworks();
            Toast.makeText(MainActivity.this, "Scan completed", Toast.LENGTH_SHORT).show();
        });
        buttonSendWifiInformation.setOnClickListener(v -> {
            List<ScanResult> scanResults = wifiManager.getScanResults();
            JSONArray wifiDataJson = prepareWifiDataAsJson(scanResults);
            sendWifiDataAsJson(wifiDataJson);
        });

        // Kiểm tra quyền vị trí
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            scanWifiNetworks();
        }

        setupAutoTask();
    }

    private void toggleAutoMode() {
        isAutoModeEnabled = !isAutoModeEnabled;

        if (isAutoModeEnabled) {
            buttonToggleAutoMode.setText("Stop Auto Mode");
            startCountdown(); // Bắt đầu đếm ngược
            autoHandler.post(autoTask); // Bắt đầu tác vụ Auto Mode
        } else {
            stopAutoMode(); // Dừng Auto Mode
        }
    }

    private void stopAutoMode() {
        isAutoModeEnabled = false;
        autoHandler.removeCallbacks(autoTask);
        autoHandler.removeCallbacks(countdownTask);
        buttonToggleAutoMode.setText("Start Auto Mode");
    }

    private void startCountdown() {
        countdownTime = 30; // Reset thời gian đếm ngược

        countdownTask = new Runnable() {
            @Override
            public void run() {
                if (isAutoModeEnabled) {
                    buttonToggleAutoMode.setText("Auto Mode: " + countdownTime + "s");
                    countdownTime--;

                    if (countdownTime < 0) {
                        countdownTime = 30; // Reset đếm ngược
                    }
                    autoHandler.postDelayed(this, 1000); // Lặp lại mỗi giây
                }
            }
        };
        autoHandler.post(countdownTask);
    }

    private void setupAutoTask() {
        autoTask = new Runnable() {
            @Override
            public void run() {
                if (isAutoModeEnabled) {
                    scanWifiNetworks();

                    if (wifiManager.getScanResults() != null) {
                        JSONArray wifiDataJson = prepareWifiDataAsJson(wifiManager.getScanResults());
                        sendWifiDataAsJson(wifiDataJson);
                    }

                    autoHandler.postDelayed(this, 30000); // Lặp lại sau 30 giây
                }
            }
        };
    }

    private void scanWifiNetworks() {
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        boolean isScanStarted = wifiManager.startScan();
        if (!isScanStarted) {
            Toast.makeText(this, "Wi-Fi scan could not start. Try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                List<ScanResult> scanResults = wifiManager.getScanResults();
                if (scanResults != null && !scanResults.isEmpty()) {
                    wifiListAdapter = new WifiListAdapter(MainActivity.this, scanResults);
                    listView.setAdapter(wifiListAdapter);
                    wifiListAdapter.notifyDataSetChanged();
                    Toast.makeText(MainActivity.this, "Wi-Fi list updated!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "No Wi-Fi networks found.", Toast.LENGTH_SHORT).show();
                }
                unregisterReceiver(this);
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }

    private JSONArray prepareWifiDataAsJson(List<ScanResult> scanResults) {
        JSONArray wifiArray = new JSONArray();
        for (ScanResult result : scanResults) {
            try {
                JSONObject wifiObject = new JSONObject();
                wifiObject.put("ssid", result.SSID);
                wifiObject.put("macAddress", result.BSSID);
                wifiObject.put("signalStrength", result.level);
                wifiArray.put(wifiObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return wifiArray;
    }

    private void sendWifiDataAsJson(JSONArray wifiData) {
        String serverUrl = "http://10.10.17.93:5000/upload_csv";

        OkHttpClient client = new OkHttpClient();

        RequestBody body = RequestBody.create(
                wifiData.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(serverUrl)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Failed to send data: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "Response: " + responseBody, Toast.LENGTH_SHORT).show()
                    );
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanWifiNetworks();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

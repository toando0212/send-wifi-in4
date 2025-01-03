package com.example.determinecoordinates;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
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


    private Button buttonSendWifiInformation, buttonRescan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.listView);


        buttonSendWifiInformation = findViewById(R.id.buttonSendWifiInformation);
        buttonRescan = findViewById(R.id.buttonRescan);


        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        // Check if permissions are granted
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Permission granted, proceed with scanning Wi-Fi networks
            scanWifiNetworks();
        }

        buttonRescan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanWifiNetworks();
                Toast.makeText(MainActivity.this, "Scan completed", Toast.LENGTH_SHORT).show();
            }
        });

        buttonSendWifiInformation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Quét mạng Wi-Fi
                List<ScanResult> scanResults = wifiManager.getScanResults();

                // Tạo JSON từ dữ liệu Wi-Fi
                JSONArray wifiDataJson = prepareWifiDataAsJson(scanResults);

                // Gửi dữ liệu đến server
                sendWifiDataAsJson(wifiDataJson);
            }
        });



    }




    private void sendWifiDataAsJson(JSONArray wifiData) {
        // URL của server
        String serverUrl = "http://192.168.103.114:5000/upload_csv";

        OkHttpClient client = new OkHttpClient();

        RequestBody body = RequestBody.create(
                wifiData.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(serverUrl)
                .post(body)
                .build();

        // Thực hiện yêu cầu HTTP
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
                    // Nhận phản hồi từ server
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

    private JSONArray prepareWifiDataAsJson(List<ScanResult> scanResults) {
        JSONArray wifiArray = new JSONArray();
        for (ScanResult result : scanResults) {
            try {
                JSONObject wifiObject = new JSONObject();
                wifiObject.put("ssid", result.SSID);
                wifiObject.put("macAddress", result.BSSID);
                wifiObject.put("signalStrength", result.level); // dBm
                wifiArray.put(wifiObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return wifiArray;
    }
    private void scanWifiNetworks() {
        // Ensure Wi-Fi is enabled
        if (!wifiManager.isWifiEnabled()) {
            wifiManager.setWifiEnabled(true);
        }


        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CHANGE_WIFI_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        // Start scanning for available networks
        boolean isScanStarted = wifiManager.startScan();
        if (!isScanStarted) {
            Toast.makeText(this, "Wi-Fi scan could not start. Try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Register BroadcastReceiver to handle scan results
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                List<ScanResult> scanResults = wifiManager.getScanResults();
                if (scanResults != null && !scanResults.isEmpty()) {
                    // Update the ListView with new scan results
                    wifiListAdapter = new WifiListAdapter(MainActivity.this, scanResults);
                    listView.setAdapter(wifiListAdapter);
                    wifiListAdapter.notifyDataSetChanged();

                    Toast.makeText(MainActivity.this, "Wi-Fi list updated!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "No Wi-Fi networks found.", Toast.LENGTH_SHORT).show();
                }

                // Unregister receiver after processing results
                unregisterReceiver(this);
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
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
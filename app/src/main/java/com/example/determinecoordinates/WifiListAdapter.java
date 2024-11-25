package com.example.determinecoordinates;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class WifiListAdapter extends ArrayAdapter<ScanResult> {

    private Context context;
    private List<ScanResult> wifiNetworks;

    public WifiListAdapter(Context context, List<ScanResult> wifiNetworks) {
        super(context, 0, wifiNetworks);
        this.context = context;
        this.wifiNetworks = wifiNetworks;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.wifi_item, parent, false);
        }

        TextView ssidTextView = convertView.findViewById(R.id.ssidTextView);
        TextView signalStrengthTextView = convertView.findViewById(R.id.signalStrengthTextView);
        TextView macAddressTextView = convertView.findViewById(R.id.macAddressTextView);

        ScanResult scanResult = wifiNetworks.get(position);

        ssidTextView.setText(scanResult.SSID);
        signalStrengthTextView.setText("Signal Strength: " + scanResult.level + " dBm");

        return convertView;
    }
}

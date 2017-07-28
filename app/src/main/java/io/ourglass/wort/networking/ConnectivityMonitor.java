package io.ourglass.wort.networking;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import io.ourglass.wort.application.WortApplication;

/**
 * Created by mkahn on 5/8/17.
 */

public class ConnectivityMonitor {

    public static final String TAG = "ConnectivityMonitor";
    public  WifiManager wifiManager = (WifiManager) WortApplication.sharedContext.getSystemService(Context.WIFI_SERVICE);
    public  ConnectivityManager connectivityManager = (ConnectivityManager) WortApplication.sharedContext.getSystemService(Context.CONNECTIVITY_SERVICE);

    private  ArrayList<ScanResult> mWiFiScanResults = new ArrayList<>();

    public  WifiInfo currentWiFiInfo;
    public  NetworkInfo currentNetworkInfo;
    public  List<WifiConfiguration> currentConfiguredWifiList;

    private WifiScanReceiver mWiFiReceiver;

    private static ConnectivityMonitor mInstance = null;

    protected ConnectivityMonitor() {
        // Exists only to defeat instantiation.
    }

    public static ConnectivityMonitor getInstance() {
        if(mInstance == null) {
            mInstance = new ConnectivityMonitor();
            mInstance.registerForConnectivityChangeNotifications();
            mInstance.updateWiFiState();
        }
        return mInstance;
    }


    class WifiScanReceiver extends BroadcastReceiver {
        // This method call when number of wifi connections changed
        public void onReceive(Context c, Intent intent) {
            Log.d(TAG, "WiFi scan complete.");
            processScanList(wifiManager.getScanResults());
        }
    }

    class NetworkChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(final Context context, final Intent intent) {

            Log.d(TAG, "Network Status changed!!");


        }
    }

    private void registerForConnectivityChangeNotifications(){

        WortApplication.sharedContext
                .registerReceiver(new NetworkChangeReceiver(),
                        new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));


    }

    public void updateWiFiState(){

        currentWiFiInfo = wifiManager.getConnectionInfo();
        currentNetworkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        currentConfiguredWifiList = wifiManager.getConfiguredNetworks();
        if (currentConfiguredWifiList != null ){
            Log.d(TAG, currentConfiguredWifiList.size() + " configured networks found");
        } else {
            Log.wtf(TAG, "WiFiManager returned null for configured networks.");
        }

    }

    public boolean isNetConnected(){
        updateWiFiState();
        return currentNetworkInfo.isConnected();
    }

    public boolean isConfiguredNetwork(String BSSID){

        updateWiFiState();
        boolean rval = false;
        for (WifiConfiguration i: currentConfiguredWifiList){
            if ( i.BSSID != null && i.BSSID.equals(BSSID) ) {
                rval = true;
                break;
            }
        }

        return rval;
    }

    public static boolean isOpenNetwork(ScanResult sr){

        String locked[] = { "PSK", "WEP", "EAP" };
        boolean isOpen = true;

        for (String s: locked){
            if (sr.capabilities.contains(s)) {
                isOpen = false;
                break;
            }
        }

        return isOpen;

    }

    public String getCurrentWiFiSSID(){
        updateWiFiState();
        return currentWiFiInfo.getSSID().replaceAll("^\"|\"$", "");
    }

    public void startWiFiScan(){
        // Create scan result receiver
        mWiFiReceiver = new WifiScanReceiver();
        WortApplication.sharedContext.registerReceiver(mWiFiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        // Broadcast receiver will automatically call when number of wifi connections changed
        Log.d(TAG, "Kicking off a WiFi scan....");
        wifiManager.startScan();
    }

    public void stopWiFiScan(){

        try {
            WortApplication.sharedContext.unregisterReceiver(mWiFiReceiver);
        } catch (Exception e){
            Log.e(TAG, "Android bug unregistering!");
        }
    }

    private void processScanList(List<ScanResult> inboundList){

        HashMap< String, ScanResult> localMap = new HashMap<>();
        Log.d(TAG, inboundList.size() + " wifi nets found, cleaning up" );

        for ( ScanResult sr: inboundList){

            Log.d(TAG, "Level for this SR is: "+ WifiManager.calculateSignalLevel(sr.level, 10));

            if (sr.SSID.isEmpty()){
                Log.d(TAG, "Skipping net with no SSID");
                continue;
            }

            if (!sr.capabilities.contains("ESS")){
                Log.d(TAG, "Skipping net that's not from Access Point.");
                continue;
            }

            ScanResult currentEntry = localMap.get(sr.SSID);
            if ( currentEntry==null ){
                Log.d(TAG, "Adding "+sr.SSID+ " to scan list.");
                localMap.put(sr.SSID, sr);
            } else {
                Log.d(TAG, sr.SSID+ " is alread in scan list. Checking strength.");
                if ( currentEntry.level < sr.level ){
                    Log.d(TAG, "This entry has a better signal, replacing.");
                    localMap.put(sr.SSID, sr);
                } else {
                    Log.d(TAG, "This entry has a worse signal, dropping.");
                }
            }
        }

        Log.d(TAG, "Post processed hashmap has "+localMap.size()+" entries.");

        mWiFiScanResults.clear();
        mWiFiScanResults.addAll(new ArrayList(localMap.values()));
        Collections.sort(mWiFiScanResults, new Comparator<ScanResult>() {
            @Override
            public int compare(ScanResult lhs, ScanResult rhs) {
                if (lhs.level < rhs.level){
                    return 1;
                } else if ( lhs.level > rhs.level ){
                    return -1;
                }
                return 0;
            }
        });

        Log.d(TAG, "Sorted arraylist has "+mWiFiScanResults.size()+" entries.");


    }

    public void connectTo(String ssid){

        for (WifiConfiguration i: currentConfiguredWifiList){
            if(i.SSID != null && i.SSID.equals("\"" + ssid + "\"")) {
                wifiManager.disconnect();
                wifiManager.enableNetwork(i.networkId, true);
                wifiManager.reconnect();
                break;
            }
        }

    }

    // TODO this is going to be different depending if WEP, OPEN, etc.
    public void configureNetwork(String ssid, String password, boolean purgeAll){

        updateWiFiState();

        // First remove all configured networks
        if (purgeAll){
            for (WifiConfiguration i: currentConfiguredWifiList){
                wifiManager.removeNetwork(i.networkId);
            }
        }

        WifiConfiguration conf = new WifiConfiguration();
        String networkPass = password;
        conf.SSID = "\"" + ssid + "\"";
        conf.preSharedKey = "\""+ networkPass +"\"";
        wifiManager.addNetwork(conf);

    }



}

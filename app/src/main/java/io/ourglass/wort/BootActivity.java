package io.ourglass.wort;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.jdeferred.DoneCallback;
import org.jdeferred.FailCallback;
import org.jdeferred.Promise;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

import io.ourglass.wort.application.WortApplication;
import io.ourglass.wort.networking.DHCPDControl;
import io.ourglass.wort.networking.DeferredRequest;
import io.ourglass.wort.settings.OGSettings;
import io.ourglass.wort.support.OGAnimations;


/**
 * Bootup needs to go in the following order:
 * <p>
 * 1) Do we have a WiFi link?
 * YES: Proceed to Check for OGC apk package upgrade
 * NO: Present only the WiFi setup button with message: Please Configure WiFi
 * 2) Check the upgrade package
 * SUCCESS: If there IS a newer package, or if NO package is installed, present upgrade button
 * and CHange Wifi Settings button. If no newer package, start the outro.
 * FAIL: Show error message "Cannot Connect to Ourglass" and Please Configure WiFi button
 */

public class BootActivity extends Activity {

    public static final String TAG = "WortBootActivity";
    public static final boolean SKIP_WIFI_CHECK = true; //use this in emulator


    public static final int WIFI_SETTLE_TIME = 90;
    public static final String MAIN_APK_NAME = "io.ourglass.bucanero";
    public static final int WIFI_SETUP_REQ_CODE = 99;  // I got 99 problems but WiFi ain't one
    public static final int INST_APK_REQ_CODE = 44;


    ImageView logo;
    TextView message;
    Button wifiButton;
    Button upgradeButton;

    private boolean mDebouncing = false;
    private String mAccumulator = "";

    private File mUpdateFolder;
    private String mPathToScript;
    private ConnectivityManager mConnectivityMgr = (ConnectivityManager) WortApplication.sharedContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    public WifiManager mWifiManager = (WifiManager) WortApplication.sharedContext.getSystemService(Context.WIFI_SERVICE);

    private boolean mWiFiConnected;
    private boolean mMainAppInstalled;
    private String mUpgradeFilename;
    private WifiInfo mCurrentWiFiInfo;
    private TextView message2;
    private ProgressBar mSpinner;
    private TextView textViewTimer;

    private int mCountdown;
    private Handler mHandler = new Handler();
    private String mWiFiSSID;

    private enum UIState { WIFI_SETTLE, REQUEST_PERMISSIONS, START,
                           NO_WIFI_NO_MAINFRAME, NO_WIFI_HAVE_MAINFRAME, NO_CONNECTION_2_OGC, NO_MAINFRAME_INSTALLED, UPGRADE_MAINFRAME,
                            DOWNLOADING, REQUEST_INSTALL, WAIT_2_START, PACKAGE_ERROR}

    private UIState mUiState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_boot);

        // Temp while Treb fixes certs
        //OGSettings.setBelliniDMMode("dev");

        Intent i = getIntent();
        boolean freshBoot = i.getBooleanExtra("FRESH_BOOTY", false);

        mSpinner = (ProgressBar) findViewById(R.id.progressBar);
        mSpinner.setVisibility(View.INVISIBLE);
        logo = (ImageView) findViewById(R.id.imageView);
        message = (TextView) findViewById(R.id.textViewMessage);
        message.setText("Starting up");

        message2 = (TextView) findViewById(R.id.textViewMessage2);
        message2.setText("");

        textViewTimer = (TextView) findViewById(R.id.textViewTimer);
        textViewTimer.setText("");


        wifiButton = (Button) findViewById(R.id.buttonWiFi);
        wifiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.removeCallbacksAndMessages(null); // stop any countdowns!
                startActivityForResult(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS), 99);
            }

        });

        upgradeButton = (Button) findViewById(R.id.buttonUpgrade);
        upgradeButton.setVisibility(View.INVISIBLE);
        upgradeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHandler.removeCallbacksAndMessages(null); // stop any countdowns!
                downloadAndInstallLatestApk();
            }
        });


        DHCPDControl.startUdhcpd();

        // This check is looking to see if this box has ever connected to a WiFi network and if it
        // has, we assume we are coming out of boot.
        if (freshBoot) {
            mUiState = UIState.WIFI_SETTLE;
            updateUi();
        } else {
            runCheckSequence();
        }

    }

    private void runCheckSequence() {

        mUiState = UIState.START;
        updateUi();

        if (!getAndOfAllPermissions()) {

            // This would only run if we switch to an Android 6 target, or permissions get messed
            // with in settings.
            Log.d(TAG, "Requesting permissions on screen");
            mUiState = UIState.REQUEST_PERMISSIONS;
            updateUi();
            requestPermissions();

        } else if (!checkWiFiNetwork()) {

            // We have no WiFi.
            Log.d(TAG, "~~~ No wifi link!");
            mUiState = checkMainAppInstalled() ? UIState.NO_WIFI_HAVE_MAINFRAME : UIState.NO_WIFI_NO_MAINFRAME;
            updateUi();

        } else {

            // We have WiFi
            Log.d(TAG, "+++ We have WiFi, checking if any package at all.");
            if (!checkMainAppInstalled()) {
                mUiState = UIState.NO_MAINFRAME_INSTALLED;
                updateUi();
            } else {
                checkForUpgrade();
            }

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        runCheckSequence();

    }


    private void updateUi() {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                switch (mUiState) {

                    case WIFI_SETTLE:
                        wifiButton.setVisibility(View.INVISIBLE);
                        message.setText("Starting Up");
                        message2.setText("Waiting for WiFi to Settle");
                        new CountDownTimer(WIFI_SETTLE_TIME * 1000, 1000) {

                            public void onTick(long millisUntilFinished) {
                                textViewTimer.setText("" + millisUntilFinished / 1000);
                                if (checkWiFiNetwork()) {
                                    this.cancel();
                                    runCheckSequence();
                                }
                            }

                            public void onFinish() {
                                runCheckSequence();
                            }
                        }.start();
                        break;

                    // needed only > API 19
                    case REQUEST_PERMISSIONS:
                        wifiButton.setText("SETUP WIFI");
                        wifiButton.setVisibility(View.VISIBLE);
                        upgradeButton.setVisibility(View.INVISIBLE);
                        message.setText("Initial Permissions Setup");
                        message2.setText("Answer Yes To All Prompts!");
                        break;

                    case NO_WIFI_HAVE_MAINFRAME:
                        // In this state, there may be a temporary networking problem in the venue,
                        // so if Mainframe is installed, we should run it so the TV is not DOA.
                        wifiButton.setText("SETUP WIFI");
                        wifiButton.setVisibility(View.VISIBLE);
                        upgradeButton.setVisibility(View.INVISIBLE);
                        message.setText("WiFi is NOT Connected!");
                        message2.setText("Please click the SETUP WIFI button to continue, or wait for Ourglass to start.");
                        outroCountdown(30);
                        break;

                    case NO_CONNECTION_2_OGC:
                        wifiButton.setText("SETUP WIFI");
                        wifiButton.setVisibility(View.VISIBLE);
                        upgradeButton.setVisibility(View.INVISIBLE);
                        message.setText("Could Not Reach Ourglass Cloud!");
                        message2.setText("Please make sure your WiFi settings are correct and that you have an active internet connection");
                        if (checkMainAppInstalled()){
                            outroCountdown(30);
                        }
                        break;

                    case NO_MAINFRAME_INSTALLED:
                        //wifiButton.setText("CHANGE WIFI SETTINGS");
                        wifiButton.setVisibility(View.INVISIBLE);
                        upgradeButton.setVisibility(View.INVISIBLE);
                        message.setText("Downloading OG Software");
                        message2.setText("Pour yourself a cold one...");
                        // This timer is here to sneak in hidden keystrokes
                        new CountDownTimer(5000, 1000) {

                            public void onTick(long millisUntilFinished) {
                                textViewTimer.setText("" + millisUntilFinished / 1000);
                            }

                            public void onFinish() {
                                textViewTimer.setText("Please wait");
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        downloadAndInstallLatestApk();
                                    }
                                });
                            }
                        }.start();

                        break;

                    case UPGRADE_MAINFRAME:
                        wifiButton.setText("CHANGE WIFI SETTINGS ( " + mWiFiSSID + " )");
                        wifiButton.setVisibility(View.VISIBLE);
                        upgradeButton.setVisibility(View.VISIBLE);
                        message.setText("Software Upgrade Available, Click Below to Install");
                        message2.setText("");
                        outroCountdown(30);
                        break;

                    case PACKAGE_ERROR:
                        wifiButton.setText("CHANGE WIFI SETTINGS (CURRENTLY ON: " + mWiFiSSID + ")");
                        wifiButton.setVisibility(View.VISIBLE);
                        upgradeButton.setVisibility(View.INVISIBLE);
                        message.setText("Upgrade Check Error");
                        message2.setText("Check Dev/Production Setting");

                        if (checkMainAppInstalled()){
                            outroCountdown(60);
                        }
                        else {
                            textViewTimer.setText("I'm stuck. No main OG app installed, no upgrade package available, or network issue.");
                        }
                        break;

                    case WAIT_2_START:
                        wifiButton.setText("CHANGE WIFI SETTINGS (CURRENTLY ON: " + mWiFiSSID + ")");
                        wifiButton.setVisibility(View.VISIBLE);
                        upgradeButton.setVisibility(View.INVISIBLE);
                        message.setText("Everything looking good!");
                        message2.setText("");
                        outroCountdown(15);
                        break;

                    case REQUEST_INSTALL:
                        wifiButton.setVisibility(View.INVISIBLE);
                        upgradeButton.setVisibility(View.INVISIBLE);
                        message.setText("Waiting for Upgrade");
                        message2.setText("");
                        break;

                    case DOWNLOADING:
                        wifiButton.setVisibility(View.INVISIBLE);
                        upgradeButton.setVisibility(View.INVISIBLE);
                        message.setText("Downloading Updated OG Software");
                        message2.setText("Pour yourself a cold one...");
                        break;

                }


            }
        });

    }


    private void requestPermissions() {
        mUiState = UIState.REQUEST_PERMISSIONS;
        updateUi();
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE},
                2727);
    }

    public boolean getAndOfAllPermissions() {

        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&

                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

    }


    private void outroCountdown(final int seconds) {

        mCountdown = seconds;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                textViewTimer.setText("Starting in " + mCountdown + " seconds");
                mCountdown--;
                if (mCountdown >= 0) {
                    mHandler.postDelayed(this, 1000);
                } else {
                    message2.setText("");
                    transitionToMainApp();
                }
            }
        }, 10);

    }

    private int getMainAppVersionCode() {

        PackageManager pm = getPackageManager();
        PackageInfo pinfo;

        try {
            pinfo = pm.getPackageInfo(MAIN_APK_NAME, 0);
            return pinfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }

    }

    private boolean checkMainAppInstalled() {

        boolean rval = getMainAppVersionCode() != 0;

        if (rval) {
            Log.d(TAG, MAIN_APK_NAME + " is installed");
        } else {
            Log.d(TAG, MAIN_APK_NAME + " NOT is installed");
        }

        return rval;
    }

    private boolean checkWiFiNetwork() {

        NetworkInfo wifiCheck = mConnectivityMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        mWiFiConnected = wifiCheck.isConnectedOrConnecting();
        mCurrentWiFiInfo = mWifiManager.getConnectionInfo();
        if (mWiFiConnected) {
            mWiFiSSID = getSSID();
            OGSettings.setLastSSID(mWiFiSSID);
        } else {
            mWiFiSSID = null;
        }

        return mWiFiConnected;

    }

    private String getSSID() {
        if (mCurrentWiFiInfo != null) {
            return mCurrentWiFiInfo.getSSID().replaceAll("^\"|\"$", "");
        }
        return null;
    }

    private void setWiFiButtonText() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                wifiButton.setText(mWiFiConnected ? "CHANGE WIFI SETTINGS" : "SETUP WIFI");
                if (mWiFiConnected) {
                    message.setText("Connected to " + getSSID());
                } else {
                    message.setText("Please set up WiFi");
                    upgradeButton.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        Log.d(TAG, "Button with this code pressed: " + keyCode);

        // The remote control does not debounce and we can get multiple onKeyDown per click
        if (mDebouncing) {
            return false;
        }

        mDebouncing = true;
        startDebounceTimer();

        if (mAccumulator.isEmpty()) {
            clearAccumulateTimer();
        }

        Log.d(TAG, "Button with this code being processed: " + keyCode);


        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d(TAG, "Pressed back button.");

            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_0) {
            Log.d(TAG, "Clearing accum");
            mAccumulator = "";
            return true;
        }

        mAccumulator += keyCode;
        Log.d(TAG, "Accumulator at: " + mAccumulator);


        // 891211 is "1254" on keypad

        if (mAccumulator.equalsIgnoreCase("891211")) {
            Log.d(TAG, "Dev mode apk toggle sequence detected");
            mAccumulator = "";
            nextApkType();
        }

        // 12131615 is "5698" on keypad
        if (mAccumulator.equalsIgnoreCase("12131615")) {
            Log.d(TAG, "Dev mode server toggle sequence detected");
            mAccumulator = "";
            nextDevModeType();
        }

        if (mAccumulator.equalsIgnoreCase("15151515")) {
            toastModes();
        }

        return false;
    }

    private void startDebounceTimer() {

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mDebouncing = false;
            }
        }, 100);

    }

    private void clearAccumulateTimer() {

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mAccumulator = "";
                Log.d(TAG, "Cleared keystroke accumulator");
            }
        }, 10000);

    }

    private void toastModes() {

        toast("Server: " + OGSettings.getBelliniDMMode() + " | APK: " + OGSettings.getMainframeApkType());
    }

    private void nextDevModeType() {
        Log.d(TAG, "Changing dev mode");
        String mode = OGSettings.getBelliniDMMode();
        if (mode.equalsIgnoreCase("dev")) {
            OGSettings.setBelliniDMMode("production");
            toast("Using Production OG Cloud Servers");
        } else {
            OGSettings.setBelliniDMMode("dev");
            toast("Using Dev OG Cloud Servers");
        }

        // Stop outro timer if running
        mHandler.removeCallbacksAndMessages(null);
        textViewTimer.setText("");
        runCheckSequence();
    }

    private void nextApkType() {

        String currentApkType = OGSettings.getMainframeApkType();
        if (currentApkType.equalsIgnoreCase("beta")) {
            OGSettings.setMainframeApkType("release");
            toast("Switching to Release Package");
        } else {
            OGSettings.setMainframeApkType("beta");
            toast("Switching to Beta Package");
        }

        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:" + MAIN_APK_NAME));
        startActivityForResult(intent, 66);

        downloadAndInstallLatestApk();
    }

    private void downloadAndInstallLatestApk() {

        getLatestApkInfo()
                .done(new DoneCallback<JSONObject>() {
                    @Override
                    public void onDone(JSONObject result) {

                        if (result.optInt("versionCode", 0) == 0) {
                            toast("No package found on " + OGSettings.getBelliniDMMode() + " server!");
                            mUiState = UIState.PACKAGE_ERROR;
                            updateUi();
                        } else {
                            try {
                                String fname = result.getString("filename");
                                downloadAndInstallApk(fname);
                            } catch (JSONException e) {
                                toast("Release package info missing filename!");
                                toast("Package found on " + OGSettings.getBelliniDMMode() + " server was missing file name!");
                                mUiState = UIState.PACKAGE_ERROR;
                                updateUi();
                            }
                        }

                    }
                })
                .fail(new FailCallback<Exception>() {
                    @Override
                    public void onFail(Exception result) {
                        toast("Error Getting Latest Package. Network Failure.");
                        mUiState = UIState.PACKAGE_ERROR;
                        updateUi();
                    }
                });
    }

    private Promise<JSONObject, Exception, Void> getLatestApkInfo() {

        String url = OGSettings.getBelliniDMAddress() + "/ogandroidrelease/latest?level=" + OGSettings.getMainframeApkType();
        return DeferredRequest.getJsonObject(url).go();
    }

    public void toast(final String message) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });

    }


    private void transitionToMainApp() {

        OGAnimations.animateAlphaTo(upgradeButton, 0f);
        OGAnimations.animateAlphaTo(message, 0f);
        OGAnimations.animateAlphaTo(message2, 0f);

        wifiButton.postDelayed(new Runnable() {
            @Override
            public void run() {
                OGAnimations.animateAlphaTo(wifiButton, 0f);
            }
        }, 500);

        logo.postDelayed(new Runnable() {
            @Override
            public void run() {
                OGAnimations.animateAlphaTo(logo, 0f);
            }
        }, 1000);

        logo.postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(MAIN_APK_NAME);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                    finish();
                }
            }
        }, 1500);

    }


    @Override
    public void onActivityResult(int reqCode, int resCode, Intent i) {
        Log.d(TAG, "Back from req: " + reqCode);

        if (reqCode == 66) {
            // delete of package, now we need to get latest
            downloadAndInstallLatestApk();
        } else {
            runCheckSequence();
        }
    }

    private void spinnerVisible(final boolean isVisible) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSpinner.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
            }
        });
    }

    public void downloadAndInstallApk(final String newApkFilename) {

        // The UI stuff in here needs to be on main thread, so make sure it is promoted.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (mUiState!=UIState.DOWNLOADING) {

                    mUiState = UIState.DOWNLOADING;
                    updateUi();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                        }
                    });
                    textViewTimer.setText("Package name: " + newApkFilename);
                    toast("Fetching latest " + OGSettings.getMainframeApkType() + " code from " + OGSettings.getBelliniDMMode() + " server");
                    spinnerVisible(true);

                    String destination = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/";
                    String fileName = "ogpackage.apk";
                    destination += fileName;
                    final Uri uri = Uri.parse("file://" + destination);

                    //Delete update file if exists
                    File file = new File(destination);
                    if (file.exists())
                        file.delete();

                    //get url of app on server
                    String url = OGSettings.getBelliniDMAddress() + "/updates/" + newApkFilename;

                    //set downloadmanager
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setDescription("Retrieving latest awesome.");
                    request.setTitle("OG Code Feeatch");
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                    //set destination
                    request.setDestinationUri(uri);

                    // get download service and enqueue file
                    final DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    final long downloadId = manager.enqueue(request);

                    //set BroadcastReceiver to install app when .apk is downloaded
                    BroadcastReceiver onComplete = new BroadcastReceiver() {
                        public void onReceive(Context ctxt, Intent intent) {

                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    spinnerVisible(false);
                                    Intent install = new Intent(Intent.ACTION_VIEW);
                                    //install.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                    install.setDataAndType(uri,
                                            manager.getMimeTypeForDownloadedFile(downloadId));
                                    mUiState = UIState.REQUEST_INSTALL;
                                    updateUi();
                                    startActivityForResult(install, 44);
                                }
                            }, 1000);

                            unregisterReceiver(this);
                            //finish();
                        }
                    };
                    //register receiver for when .apk download is compete
                    //register receiver for when .apk download is compete
                    registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

                    mHandler.post(new Runnable() {

                        @Override
                        public void run() {

                            DownloadManager.Query q = new DownloadManager.Query();
                            q.setFilterById(downloadId);

                            Cursor cursor = manager.query(q);
                            cursor.moveToFirst();

                            int bytes_downloaded = cursor.getInt(cursor
                                    .getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));

                            int bytes_total = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                            final double dl_progress = Math.floor(((double) bytes_downloaded / (double) bytes_total) * 100.0);

                            runOnUiThread(new Runnable() {

                                @Override
                                public void run() {

                                    mSpinner.setProgress((int) dl_progress);

                                }
                            });

                            Log.d(TAG, bytes_downloaded + " of " + bytes_total);
                            if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) {
                                mHandler.postDelayed(this, 250);
                            }

                            cursor.close();

                        }
                    });

                } else {
                    Log.e(TAG, "Call to download while downloading! Ignored!!");
                    return;
                }

            }
        });

    }


    private void checkForUpgrade() {

        message.setText("Checking for upgrade package");

        getLatestApkInfo()
                .done(new DoneCallback<JSONObject>() {
                    @Override
                    public void onDone(JSONObject result) {

                        int cloudRev = result.optInt("versionCode", 0);
                        int localRev = getMainAppVersionCode();

                       if (cloudRev > localRev) {
                            try {
                                mUpgradeFilename = result.getString("filename");
                                mUiState = UIState.UPGRADE_MAINFRAME;
                            } catch (JSONException e) {
                                toast("Release package info missing filename!");
                                mUiState = UIState.PACKAGE_ERROR;
                            }
                        } else if (cloudRev == 0) {
                            toast("No package found on OG Cloud!!");
                            mUiState = UIState.PACKAGE_ERROR;

                        } else {
                            mUiState = UIState.WAIT_2_START;
                        }

                        updateUi();
                    }
                })
                .fail(new FailCallback<Exception>() {
                    @Override
                    public void onFail(Exception result) {
                        toast("Failed checking for upgrade package!");
                        mUiState = UIState.NO_CONNECTION_2_OGC;
                        updateUi();
                    }
                });

    }

}


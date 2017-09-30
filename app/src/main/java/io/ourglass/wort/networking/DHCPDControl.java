package io.ourglass.wort.networking;

import android.content.res.Resources;
import android.util.Log;

import com.snatik.storage.Storage;

import java.io.InputStream;
import java.util.ArrayList;

import io.ourglass.wort.R;
import io.ourglass.wort.application.WortApplication;
import io.ourglass.wort.support.OGShellE;

import static io.ourglass.wort.application.WortApplication.sharedContext;

/**
 * Created by mkahn on 9/12/17.
 */

public class DHCPDControl {

    public static final String TAG = "DHCPDControl";

    private static void installUdhcpdConfFile(){
        Storage storage = new Storage(sharedContext);

        // get external storage
        String path = storage.getExternalStorageDirectory() + "/udhcpd.conf";

        String cfile ;
        try {
            Resources res = WortApplication.sharedContext.getResources();
            InputStream in_s = res.openRawResource(R.raw.udhcpd);

            byte[] b = new byte[in_s.available()];
            in_s.read(b);
            cfile = new String(b);
            storage.createFile(path, cfile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * Per Treb, here's the magic sequence
     *
     * touch /data/user/udhcpd.leases
     * ifconfig eth0 10.21.200.2 netmask 255.255.255.0
     * /system/xbin/busybox udhcpd /data/user/udhcpd.conf
     * ip route add table 1005 10.21.200.0/24 via 10.21.200.1 dev eth0
     *
     */
    public static void startUdhcpd(){
        installUdhcpdConfFile();
        touchLeaseFile();
    }

    private static void touchLeaseFile(){

        OGShellE.execRoot("touch /mnt/sdcard/udhcpd.leases", new OGShellE.OGShellEListener() {
            @Override
            public void stdout(ArrayList<String> results) {
                Log.d(TAG, "TOUCH of lease file completed without fail.");
                ifconfig();
            }

            @Override
            public void stderr(ArrayList<String> errors) {
                Log.e(TAG, "Turds in stderr after touching lease file.");
                Log.e(TAG, errors.toString());
                Log.e(TAG, "Continuing anyway, what's the worst that can happen?");
                ifconfig();
            }

            @Override
            public void fail(Exception e) {
                Log.wtf(TAG, "Failed out at TOUCH of lease file. Chain over at stage 1.");
            }
        });

    }

    private static void ifconfig(){

        OGShellE.execRoot("ifconfig eth0 10.21.200.1 netmask 255.255.255.0", new OGShellE.OGShellEListener() {
            @Override
            public void stdout(ArrayList<String> results) {
                Log.d(TAG, "ifconfig of eth0 completed without fail.");
                startDaemon();
            }

            @Override
            public void stderr(ArrayList<String> errors) {
                Log.e(TAG, "Turds in stderr after ifconfig eth0.");
                Log.e(TAG, errors.toString());
                Log.e(TAG, "Continuing anyway, what's the worst that can happen?");
                startDaemon();
            }

            @Override
            public void fail(Exception e) {
                Log.wtf(TAG, "Failed out at ifconfig of eth0. Chain over at stage 2.");
            }
        });

    }

    private static void startDaemon(){

        OGShellE.execRoot("/system/xbin/busybox udhcpd /mnt/sdcard/udhcpd.conf", new OGShellE.OGShellEListener() {
            @Override
            public void stdout(ArrayList<String> results) {
                Log.d(TAG, "busybox/udhcp start completed without fail.");
                ipRoute();
            }

            @Override
            public void stderr(ArrayList<String> errors) {
                Log.e(TAG, "Turds in stderr after starting udhcpd.");
                Log.e(TAG, errors.toString());
                Log.e(TAG, "Continuing anyway, what's the worst that can happen?");
                ipRoute();
            }

            @Override
            public void fail(Exception e) {
                Log.wtf(TAG, "Failed out at busybox/udhcp start. Chain over at stage 3.");
            }
        });
    }

    private static void ipRoute(){

        OGShellE.execRoot("ip route add table wlan0 10.21.200.0/24 via 10.21.200.1 dev eth0", new OGShellE.OGShellEListener() {
            @Override
            public void stdout(ArrayList<String> results) {
                Log.d(TAG, "ip route table add completed without fail. We should be good to go, homie.");
            }

            @Override
            public void stderr(ArrayList<String> errors) {
                Log.e(TAG, "Turds in stderr after ip route add.");
                Log.e(TAG, errors.toString());
                Log.e(TAG, "That's a wrap. It might work, maybe not.");
            }

            @Override
            public void fail(Exception e) {
                Log.wtf(TAG, "Failed out at ip route add. Chain over at stage 4.");
            }
        });
    }


}

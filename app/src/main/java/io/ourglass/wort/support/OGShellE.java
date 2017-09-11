package io.ourglass.wort.support;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Created by mkahn on 9/9/17.
 */

public class OGShellE {

    private static final String TAG = "OGShellE";

    private Thread mShellThread;
    private boolean mExecDone = false;

    private boolean mRunAsRoot = false;

    private static int sTerminationDelay = 5000;

    private OGShellEListener mListener;

    public interface OGShellEListener {
        public void stdout(ArrayList<String> results);

        public void stderr(ArrayList<String> errors);

        public void fail(Exception e);
    }

    public OGShellE(OGShellEListener listener) {
        mListener = listener;
    }

    /**
     * Silent fire and forget. Not really recommended.
     *
     * @param command to execute
     */
    public static void exec(String command) {
        Log.d(TAG, "Fire and forget exec with: '" + command + "'. YOYOMF");
        (new OGShellE(null)).execute(command);
    }

    /**
     * Silent fire and forget as ROOT. Not really recommended.
     *
     * @param command to execute
     */
    public static void execRoot(String command) {
        Log.d(TAG, "Fire and forget exec root with: '" + command + "'. YOYOMF");

        OGShellE shell = (new OGShellE(null));
        shell.mRunAsRoot = true;
        shell.execute(command);
    }

    /**
     * Fire and get results.
     *
     * @param command to execute
     */
    public static void exec(String command, OGShellEListener listener) {
        Log.d(TAG, "Firing exec with: '" + command + "'.");
        (new OGShellE(listener)).execute(command);
    }

    /**
     * Fire as ROOT and get results.
     *
     * @param command to execute
     */
    public static void execRoot(String command, OGShellEListener listener) {
        Log.d(TAG, "Firing exec with: '" + command + "'.");
        OGShellE shell = new OGShellE(listener);
        shell.mRunAsRoot = true;
        shell.execute(command);
    }

    /**
     * Sets the termination delay for all execs gone wrong.
     *
     * @param delayInMs
     */
    public static void setTimeoutDelay(int delayInMs) {
        sTerminationDelay = delayInMs;
    }

    /**
     * In the off chance we block in the thread, this will kill after the default timeout.
     */
    private Runnable mTerminator = new Runnable() {
        @Override
        public void run() {
            try {
                Thread.sleep(sTerminationDelay);
                if (!mExecDone) {
                    Log.wtf("TERMINATOR", "Killing EXEC thread with force.");
                    mShellThread.interrupt();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    private void execute(final String command, int terminationDelay) {
        sTerminationDelay = terminationDelay;
        execute(command);
    }

    private void execute(final String command) {

        Runnable seRun = new Runnable() {
            @Override
            public void run() {

                ArrayList<String> stdout = new ArrayList<>();
                ArrayList<String> stderr = new ArrayList<>();
                Exception exc = null;

                Process process;
                try {
                    process = Runtime.getRuntime().exec("su "+ (mRunAsRoot?"0":"1") + "\n");
                    DataOutputStream toProcess = new DataOutputStream(process.getOutputStream());
                    toProcess.writeBytes(command + "\n");
                    toProcess.flush();
                    toProcess.writeBytes("exit\n");
                    toProcess.flush();

                    DataInputStream fromProcess = new DataInputStream(process.getInputStream());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(fromProcess));

                    String line = "";
                    while (reader.ready()) {
                        line = reader.readLine();
                        stdout.add(line);
                    }

                    BufferedReader ereader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                    String eline = "";
                    while (ereader.ready()) {
                        eline = ereader.readLine();
                        stderr.add(eline);
                    }

                    (new Thread(mTerminator)).start(); // kills this thread if waitFor() hangs.
                    process.waitFor();
                    mExecDone = true;


                } catch (Exception e) {
                    exc = e;
                }

                if (mListener != null) {

                    if (exc == null){
                        // Always send something to STDOUT
                        mListener.stdout(stdout);
                        if (stderr.size() > 0)
                            mListener.stderr(stderr);
                    } else {
                        mListener.fail(exc);
                    }

                }
            }
        };

        mShellThread = new Thread(seRun);
        mShellThread.start();

    }
}

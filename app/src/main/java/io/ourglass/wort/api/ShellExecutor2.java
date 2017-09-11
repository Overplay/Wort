package io.ourglass.wort.api;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Created by mkahn on 1/26/17.
 */

public class ShellExecutor2 {

    private Thread mShellThread;
    private boolean mExecDone = false;

    private int mTerminationDelay = 5000;

    private ShellExecutorListener2 mListener;

    public ShellExecutor2(ShellExecutorListener2 listener) {
        mListener = listener;
    }

    private Runnable mTerminator = new Runnable() {
        @Override
        public void run() {
            try {
                Thread.sleep(mTerminationDelay);
                if (!mExecDone){
                    Log.wtf("TERMINATOR", "Killing EXEC thread with force.");
                    mShellThread.interrupt();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


        }
    };

    public interface ShellExecutorListener2 {
        public void results(ArrayList<String> results);
    }

    public void exec(final String command, int terminationDelay){
        mTerminationDelay = terminationDelay;
        exec(command);
    }

    public void exec(final String command) {

        Runnable seRun = new Runnable() {
            @Override
            public void run() {

                ArrayList<String> output = new ArrayList<>();

                Process su;
                try {
                    su = Runtime.getRuntime().exec("su\n");
                    DataOutputStream toSu = new DataOutputStream(su.getOutputStream());
                    DataInputStream fromSu = new DataInputStream(su.getInputStream());
                    toSu.writeBytes(command + "\n");
                    toSu.flush();
                    toSu.writeBytes("exit\n");
                    toSu.flush();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(fromSu));

                    String line = "";
                    while (reader.ready()) {
                        line = reader.readLine();
                        output.add(line);
                    }

                    (new Thread(mTerminator)).start(); // kills this thread if waitFor() hangs.
                    su.waitFor();
                    mExecDone = true;


                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (mListener != null) {
                    mListener.results(output);
                }
            }
        };

        mShellThread = new Thread(seRun);
        mShellThread.start();
    }


}

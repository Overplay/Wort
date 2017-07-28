package io.ourglass.wort.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Created by mkahn on 1/26/17.
 */

public class ShellExecutor {

    private ShellExecutorListener mListener;

    public ShellExecutor(ShellExecutorListener listener) {
        mListener = listener;
    }

    public interface ShellExecutorListener {
        public void results(String results);
    }

    public void exec(final String command) {

        Runnable seRun = new Runnable() {
            @Override
            public void run() {

                StringBuffer output = new StringBuffer();

                Process p;
                try {
                    p = Runtime.getRuntime().exec(command);
                    p.waitFor();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

                    String line = "";
                    while ((line = reader.readLine()) != null) {
                        output.append(line + "n");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                String response = output.toString();
                if (mListener != null) {
                    mListener.results(response);
                }
            }
        };

        Thread seThread = new Thread(seRun);
        seThread.start();
    }


}

package io.ourglass.wort.api;

import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Created by mkahn on 1/26/17.
 */

public class ShellExecutorPromise {

    private final Deferred<String, Exception, Void> deferred = new DeferredObject<>();
    private final String mCommand;
    private final String[] mCommands;
    private boolean asRoot;


    public ShellExecutorPromise(String command, boolean asRoot) {

        mCommand = command;
        mCommands = new String[] { "su", "0", command };
        this.asRoot = asRoot;
    }

    public void exec() {

        Runnable seRun = new Runnable() {
            @Override
            public void run() {

                StringBuffer output = new StringBuffer();

                Process p;
                try {
                    if (asRoot == true ){
                        p = Runtime.getRuntime().exec(mCommands);
                    } else {
                        p = Runtime.getRuntime().exec(mCommand);
                    }
                    p.waitFor();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

                    String line = "";
                    while ((line = reader.readLine()) != null) {
                        output.append(line + "n");
                    }

                    String response = output.toString();
                    deferred.resolve(response);

                } catch (Exception e) {
                    e.printStackTrace();
                    deferred.reject(e);
                }


            }
        };

        Thread seThread = new Thread(seRun);
        seThread.start();
    }

    public Promise<String, Exception, Void> promise() {
        return deferred.promise();
    }

}

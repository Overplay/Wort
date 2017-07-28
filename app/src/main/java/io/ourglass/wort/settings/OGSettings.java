package io.ourglass.wort.settings;

import android.content.Context;
import android.content.SharedPreferences;

import io.ourglass.wort.application.WortApplication;


/**
 * Created by mkahn on 7/15/17.
 */

public class OGSettings {

    public static void restoreDefaultSettings() {
        //TODO implement this

    }

    public static String getBelliniDMAddress() {
        String mode = getStringFromPrefs("belliniDMMode", "production");

        if (OGConstants.USE_LOCAL_DM_SERVER) {
            mode = "local"; // hard override
        }

        switch (mode) {

            case "dev":
                return OGConstants.BELLINI_DM_DEV_ADDRESS;
            case "local":
                return OGConstants.BELLINI_DM_EMU_LOCAL_ADDRESS;
            case "production":
            default:
                return OGConstants.BELLINI_DM_PRODUCTION_ADDRESS;
        }
    }

    public static String getBelliniDMMode(){
        return getStringFromPrefs("belliniDMMode", "production");
    }

    public static void setBelliniDMMode(String mode) {
        putStringToPrefs("belliniDMMode", mode);
    }

    public static String getMainframeApkType(){
        return getStringFromPrefs("mfVersionType", "release");
    }

    public static void setMainframeApkType(String type){
        putStringToPrefs("mfVersionType", type);
    }



    /*
    * Verbose/Debug Mode
    */
    public static boolean getVerboseMode() {
        return getBoolFromPrefs("verboseMode", OGConstants.SHOW_DB_TOASTS);
    };

    public static void setVerboseMode(boolean verbose) {
        putBoolToPrefs("verboseMode", verbose);
    }

    ;

    /*
     *
     * Shared Preferences Methods
     *
     *
     */

    public static SharedPreferences mPrefs = WortApplication.sharedContext.getSharedPreferences(
            "ourglass.buc", Context.MODE_PRIVATE);

    public static SharedPreferences.Editor mEditor = mPrefs.edit();

    public static void putStringToPrefs(String key, String string) {

        mEditor.putString(key, string);
        mEditor.apply();
    }

    public static String getStringFromPrefs(String key, String defValue) {

        return mPrefs.getString(key, defValue);

    }

    public static void putIntToPrefs(String key, int integer) {

        mEditor.putInt(key, integer);
        mEditor.apply();
    }

    public static int getIntFromPrefs(String key) {

        return mPrefs.getInt(key, 0);

    }

    public static void putBoolToPrefs(String key, boolean bool) {

        mEditor.putBoolean(key, bool);
        mEditor.apply();
    }

    public static boolean getBoolFromPrefs(String key, boolean defaultValue) {
        return mPrefs.getBoolean(key, defaultValue);
    }


}

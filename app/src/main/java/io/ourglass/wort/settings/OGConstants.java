package io.ourglass.wort.settings;

/**
 * Created by mkahn on 5/17/16.
 */
public class OGConstants {

    // For debug in the emulator, this must be set to true
    public static final Boolean USE_LOCAL_DM_SERVER = false;

    public static final String CODE_REV_NAME = "Bucanero";
    public static final String DEVICE_AUTH_HDR = "x-ogdevice-1234";

    public static final String BELLINI_DM_PRODUCTION_ADDRESS = "https://cloud-dm.ourglass.tv";
    public static final String BELLINI_DM_DEV_ADDRESS = "http://138.68.230.239:2001";
    public static final String BELLINI_DM_EMU_LOCAL_ADDRESS = "http://10.0.2.2:2001";

    public static final boolean SHOW_DB_TOASTS = true;

    public static final String[] APK_TYPES = { "beta", "release" };



}

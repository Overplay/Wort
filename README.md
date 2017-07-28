WORT: Upgrade Checker and WiFi Config for Bucanero
==========================

Wort provides two simple functions for the OG1 System:
- It checks for upgrades to the Bucanero APK hosted on Bellini-DM
    - If there is an upgrade, the user is prompted to download and install it
- It checks/Tests the WiFi and allows the user to configure, modify thru calls to the Android Native WiFi Setup
    - Native is used because of the issues we have had with setting up from the user space code.
    
    
###Operation

The app registers as a boot receiver through the manifest. The Receiver sends an Intent to start BootActivity and
in that intent `putsExtra` a boolean saying it's a fresh boot.

When BootActivity starts, if that extra is there, it waits 30 seconds for the WiFi to settle since it takes
about that long out of boot for it to settle (~20 seconds). The countdown bails early if it sees a stable connection.

If it never gets a stable connection, it goes to the setup WiFi state.

Once it has a stable connection, BootActivity checks with cloud-dm.ourglass.net (or 138.x.y.z) to see if there is
an upgrade package. If the package is at a newer rev than the installed Bucanero (or if no Buc is installed), the app downloads the 
APK and prompts for install.

###Backdoors

You can change the release level of the package you want pressing "1254" on the remote control within 10 seconds. Note
this forces a download automatically. You might need to force uninstall Buc to get this to work if the package rev goes 
backwards (i.e. from Beta (say VersCode 5) to Release (say VersCode 4)).


You can change the server you are pointing at by pressing "5698".
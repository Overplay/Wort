WORT: Upgrade Checker and WiFi Config for Bucanero
==========================

Wort provides two simple functions for the OG1 System:
- It checks for upgrades to the Bucanero APK hosted on Bellini-DM
    - If there is an upgrade, the user is prompted to download and install it
- It checks/Tests the WiFi and allows the user to configure, modify thru calls to the Android Native WiFi Setup
    - Native is used because of the issues we have had with setting up from the user space code.
    
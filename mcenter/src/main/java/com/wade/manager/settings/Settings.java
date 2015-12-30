package com.wade.manager.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.wade.manager.R;
import com.stericson.RootTools.RootTools;

public final class Settings {

    private static SharedPreferences mPrefs;

    public static int mTheme;

    public static void updatePreferences(Context context) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mTheme = Integer.parseInt(mPrefs.getString("preference_theme", Integer.toString(R.style.ThemeLight)));

        rootAccess();
    }

    public static boolean showThumbnail() {
        return mPrefs.getBoolean("showpreview", true);
    }

    public static boolean showHiddenFiles() {
        return mPrefs.getBoolean("displayhiddenfiles", true);
    }

    public static boolean rootAccess() {
        return mPrefs.getBoolean("enablerootaccess", true) && RootTools.isAccessGiven();
    }

    public static boolean reverseListView() {
        return mPrefs.getBoolean("reverseList", false);
    }

    public static String getDefaultDir() {
        String dir = mPrefs.getString("defaultdir", Environment.getExternalStorageDirectory().getPath());
        if (!dir.startsWith("/storage")) dir = "/storage";
        return dir;
    }

    public static String getDefaultPort() {
        String port = mPrefs.getString("defaultport", "8080");
        Integer portNum = new Integer(port);
        if (portNum <= 1024) portNum = 8080;
        return ""+portNum;
    }

    public static Boolean checkServer() {
        return  mPrefs.getBoolean("enablewebserver", true);
    }

    public static int getListAppearance() {
        return Integer.parseInt(mPrefs.getString("viewmode", "1"));
    }

    public static int getSortType() {
        return Integer.parseInt(mPrefs.getString("sort", "1"));
    }

    public static int getDefaultTheme() {
        return mTheme;
    }

    public static void setDefaultTheme(int theme) {
        mTheme = theme;
    }
}

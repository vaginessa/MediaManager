package com.wade.manager.settings;

import android.app.Activity;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import com.wade.manager.R;

public final class SettingsFragment extends PreferenceFragment {

    private static final String[] THEMES_VALUES = new String[]{
            Integer.toString(R.style.ThemeLight),
            Integer.toString(R.style.ThemeDark)};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        init();
    }

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof SettingsActivity)) {
            throw new RuntimeException("Should be attached only to SettingsActivity");
        }
    }

    private void init() {
        final ListPreference theme = (ListPreference) findPreference("preference_theme");
        theme.setEntryValues(THEMES_VALUES);
        theme.setValue(String.valueOf(Settings.getDefaultTheme()));
        theme.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                final int chosenTheme = Integer.parseInt((String) newValue);
                if (chosenTheme != Settings.getDefaultTheme()) {
                    Settings.setDefaultTheme(chosenTheme);
                    ((SettingsActivity) getActivity()).proxyRestart();
                    return true;
                }
                return false;
            }
        });

        final CheckBoxPreference enablewebserver = (CheckBoxPreference) findPreference("enablewebserver");
        enablewebserver.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if ((boolean)newValue) {
                    enablewebserver.setTitle("Server Started");
                }
                else {
                    enablewebserver.setTitle("Server Stopped");
                }
                return true;
            }
        });
    }
}

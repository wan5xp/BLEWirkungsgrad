package de.hsulm.blewirkungsgrad.cps.settings;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.content.SharedPreferences;
import android.preference.PreferenceScreen;

import de.hsulm.blewirkungsgrad.R;

/**
 * Created by wan5xp on 02.11.2017.
 */

public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String SETTINGS_WHEEL_SIZE = "settings_wheel_size";
    public static final int SETTINGS_WHEEL_SIZE_DEFAULT = 2340;
    public static final String SETTINGS_UNIT = "settings_cps_unit";
    public static final int SETTINGS_UNIT_M_S = 0; // [m/s]
    public static final int SETTINGS_UNIT_KM_H = 1; // [m/s]
    public static final int SETTINGS_UNIT_MPH = 2; // [m/s]
    public static final int SETTINGS_UNIT_DEFAULT = SETTINGS_UNIT_KM_H;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings_cps);

        updateWheelSizeSummary();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Attach the preference change listener.
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        // unregister listener
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if (SETTINGS_WHEEL_SIZE.equals(key)) {
            updateWheelSizeSummary();
        }
    }

    private void updateWheelSizeSummary() {
        final PreferenceScreen screen = getPreferenceScreen();
        final SharedPreferences preferences = getPreferenceManager().getSharedPreferences();

        final String value = preferences.getString(SETTINGS_WHEEL_SIZE, String.valueOf(SETTINGS_WHEEL_SIZE_DEFAULT));
        screen.findPreference(SETTINGS_WHEEL_SIZE).setSummary(getString(R.string.cps_settings_wheel_diameter_summary, value));
    }
}

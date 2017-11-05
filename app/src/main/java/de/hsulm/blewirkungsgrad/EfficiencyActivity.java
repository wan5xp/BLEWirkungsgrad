package de.hsulm.blewirkungsgrad;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Locale;
import java.util.UUID;

import de.hsulm.blewirkungsgrad.cps.CPSManager;
import de.hsulm.blewirkungsgrad.cps.CPSService;
import de.hsulm.blewirkungsgrad.cps.settings.SettingsFragment;
import de.hsulm.blewirkungsgrad.profile.BleProfileService;
import de.hsulm.blewirkungsgrad.profile.BleProfileServiceReadyActivity;

public class EfficiencyActivity extends BleProfileServiceReadyActivity<CPSService.CPSBinder> {
    private TextView mValueIP;
    private TextView mValueTotalIP;
    private TextView mValueEP;
    private TextView mEfficiency;

    private int ePower = 40;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            if (CPSService.BROADCAST_INSTANT_POWER.equals(action)) {
                final int instantPower = intent.getIntExtra(CPSService.EXTRA_INSTANT_POWER, 0); // [m/s]
                // Update GUI
                ePower = (int) (instantPower * 0.1);
                onInstantPowerReceived(instantPower);
            } else if (CPSService.BROADCAST_SENSOR_LOCATION.equals(action)) {
                final String sensorLocation = intent.getStringExtra(CPSService.EXTRA_SENSOR_LOCATION);
                // Update GUI
                onSensorLocationFound(sensorLocation);
            }
        }
    };

    private static IntentFilter makeintentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CPSService.BROADCAST_INSTANT_POWER);
        intentFilter.addAction(CPSService.BROADCAST_SENSOR_LOCATION);
        return intentFilter;
    }

    @Override
    protected void onCreateView(Bundle savedInstanceState) {
        setContentView(R.layout.activity_efficiency);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setGUI();
    }

    @Override
    protected void onInitialise(final Bundle savedInstanceState) {
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, makeintentFilter());
    }

    private void setGUI() {

        mValueIP = (TextView) findViewById(R.id.textValueLeftPower);
        mValueTotalIP = (TextView) findViewById(R.id.textValueTotalMPower);
        mValueEP = (TextView) findViewById(R.id.textValueEPower);
        mEfficiency = (TextView) findViewById(R.id.textValueEfficiency);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setDefaultUI();
    }

    @Override
    protected void setDefaultUI() {

        mValueIP.setText(R.string.not_available_value);
        mValueTotalIP.setText(R.string.not_available_value);
        mValueEP.setText(R.string.not_available_value);
        mEfficiency.setText(R.string.not_available_value);
    }

    @Override
    protected int getLoggerProfileTitle() {
        return R.string.cps_feature_title;
    }

    @Override
    protected int getDefaultDeviceName() {
        return R.string.cps_default_name;
    }

    @Override
    protected void onServiceBinded(CPSService.CPSBinder binder) {

    }

    @Override
    protected void onServiceUnbinded() {

    }

    @Override
    protected Class<? extends BleProfileService> getServiceClass() {
        return CPSService.class;
    }

    @Override
    protected UUID getFilterUUID() {
        return CPSManager.UUID_CYCLE_POWER_SERVICE;
    }

    private void onSensorLocationFound(String sensorLocation) {

    }

    private void onInstantPowerReceived(int instantPowerLeft) {
        mValueIP.setText(String.format(Locale.GERMAN, "%d", instantPowerLeft));
        final int totalIP = instantPowerLeft; // + instantPowerRight;
        mValueTotalIP.setText(String.format(Locale.GERMAN, "%d", totalIP));
        mValueEP.setText(String.format(Locale.GERMAN, "%d", ePower));
        if (totalIP > 0) {
            mEfficiency.setText(String.format(Locale.GERMAN, "%d", (ePower * 100 / totalIP)));
        }
    }

}
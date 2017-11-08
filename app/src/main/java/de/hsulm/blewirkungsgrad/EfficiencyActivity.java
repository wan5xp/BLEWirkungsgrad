package de.hsulm.blewirkungsgrad;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;
import java.util.UUID;

import de.hsulm.blewirkungsgrad.cps.CPSManager;
import de.hsulm.blewirkungsgrad.cps.CPSService;
import de.hsulm.blewirkungsgrad.profile.multiconnect.BleMulticonnectProfileService;
import de.hsulm.blewirkungsgrad.profile.multiconnect.BleMulticonnectProfileServiceReadyActivity;
import de.hsulm.blewirkungsgrad.scanner.ScannerFragment;

public class EfficiencyActivity extends BleMulticonnectProfileServiceReadyActivity<CPSService.CPSBinder> {
    private static final String TAG = "EfficiencyActivity";
    private TextView mValueInstantPowerL;
    private TextView mValueInstantPowerR;
    private TextView mValueTotalIP;
    private TextView mValueEP;
    private TextView mEfficiency;
    private TextView mLeftLabel;
    private TextView mRightLabel;
    private Button mAddPedal;
    private Button mElectricConnect;

    private BluetoothDevice mLeftPedal, mRightPedal, mCurrentSensor;

    private int electricalPower = -1;
    private int instantPowerL = 0;
    private int instantPowerR = 0;
    private int totalInstantPower = -1;
    private int mCadenceL = -1;
    private int mCadenceR = -1;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();

            final BluetoothDevice device = intent.getParcelableExtra(CPSService.EXTRA_DEVICE);
            if (CPSService.BROADCAST_INSTANT_POWER.equals(action)) {
                final int instantPower = intent.getIntExtra(CPSService.EXTRA_INSTANT_POWER, 0); // [m/s]
                // Update GUI
                onInstantPowerReceived(device, instantPower);
            } else if (CPSService.BROADCAST_PEDAL_POWER_BALANCE.equals(action)) {
                final int pedalPowerBalance = intent.getIntExtra(CPSService.EXTRA_PEDAL_POWER_BALANCE, 0);
                // Update GUI
                onPedalPowerBalanceReceived(device, pedalPowerBalance);
            } else if (CPSService.BROADCAST_ACCUMULATED_TORQUE.equals(action)) {
                final int accumulatedTorque = intent.getIntExtra(CPSService.EXTRA_ACCUMULATED_TORQUE, 0);
                // Update GUI
                onAccumulatedTorqueReceived(device, accumulatedTorque);
            } else if (CPSService.BROADCAST_WHEEL_DATA.equals(action)) {
                final int wheelRPM = intent.getIntExtra(CPSService.EXTRA_WHEEL_RPM, 0);
                final int sessionRev = intent.getIntExtra(CPSService.EXTRA_SESSION_REV, 0);
                final int totalRev = intent.getIntExtra(CPSService.EXTRA_TOTAL_REV, 0);
                // Update GUI
                onWheelDataReceived(device, wheelRPM, sessionRev, totalRev);
            } else if (CPSService.BROADCAST_CRANK_DATA.equals(action)) {
                final int cadence = intent.getIntExtra(CPSService.EXTRA_CADENCE, 0);
                // Update GUI
                onCrankDataReceived(device, cadence);
            } else if (CPSService.BROADCAST_EXTREME_FORCE.equals(action)) {
                final int maxForce = intent.getIntExtra(CPSService.EXTRA_MAX_FORCE, 0);
                final int minForce = intent.getIntExtra(CPSService.EXTRA_MIN_FORCE, 0);
                // Update GUI
                onExtremeForceReceived(device, maxForce, minForce);
            } else if (CPSService.BROADCAST_EXTREME_TORQUE.equals(action)) {
                final int maxTorque = intent.getIntExtra(CPSService.EXTRA_MAX_TORQUE, 0);
                final int minTorque = intent.getIntExtra(CPSService.EXTRA_MIN_TORQUE, 0);
                // Update GUI
                onExtremeTorqueReceived(device, maxTorque, minTorque);
            } else if (CPSService.BROADCAST_EXTREME_ANGLES.equals(action)) {
                final int maxAngle = intent.getIntExtra(CPSService.EXTRA_MAX_ANGLE, 0);
                final int minAngle = intent.getIntExtra(CPSService.EXTRA_MIN_ANGLE, 0);
                // Update GUI
                onExtremeAnglesReceived(device, maxAngle, minAngle);
            } else if (CPSService.BROADCAST_TOP_DEAD_SPOT_ANGLE.equals(action)) {
                final int topAngle = intent.getIntExtra(CPSService.EXTRA_TOP_DEAD_SPOT_ANGLE, 0);
                // Update GUI
                onTopDeadSpotAngleReceived(device, topAngle);
            } else if (CPSService.BROADCAST_BOTTOM_DEAD_SPOT_ANGLE.equals(action)) {
                final int bottomAngle = intent.getIntExtra(CPSService.EXTRA_BOTTOM_DEAD_SPOT_ANGLE, 0);
                // Update GUI
                onBottomDeadSpotAngleReceived(device, bottomAngle);
            } else if (CPSService.BROADCAST_ACCUMULATED_ENERGY.equals(action)) {
                final int accumulatedEnergy = intent.getIntExtra(CPSService.EXTRA_ACCUMULATED_ENERGY, 0);
                // Update GUI
                onAccumulatedEnergyReceived(device, accumulatedEnergy);
            } else if (CPSService.BROADCAST_SENSOR_LOCATION.equals(action)) {
                final String sensorLocation = intent.getStringExtra(CPSService.EXTRA_SENSOR_LOCATION);
                // Update GUI
                onSensorLocationFound(device, sensorLocation);
            } else if (CPSService.BROADCAST_ELECTRICAL_POWER.equals(action)) {
                final int ePower = intent.getIntExtra(CPSService.EXTRA_ELECTRICAL_POWER, 0);
                // Update GUI
                onElectricalPowerReceived(device, ePower);
            }

        }
    };

    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CPSService.BROADCAST_INSTANT_POWER);
        intentFilter.addAction(CPSService.BROADCAST_PEDAL_POWER_BALANCE);
        intentFilter.addAction(CPSService.BROADCAST_ACCUMULATED_TORQUE);
        intentFilter.addAction(CPSService.BROADCAST_WHEEL_DATA);
        intentFilter.addAction(CPSService.BROADCAST_CRANK_DATA);
        intentFilter.addAction(CPSService.BROADCAST_EXTREME_FORCE);
        intentFilter.addAction(CPSService.BROADCAST_EXTREME_ANGLES);
        intentFilter.addAction(CPSService.BROADCAST_TOP_DEAD_SPOT_ANGLE);
        intentFilter.addAction(CPSService.BROADCAST_BOTTOM_DEAD_SPOT_ANGLE);
        intentFilter.addAction(CPSService.BROADCAST_ACCUMULATED_ENERGY);
        intentFilter.addAction(CPSService.BROADCAST_SENSOR_LOCATION);
        intentFilter.addAction(CPSService.BROADCAST_ELECTRICAL_POWER);
        return intentFilter;
    }

    @Override
    protected void onCreateView(Bundle savedInstanceState) {
        setContentView(R.layout.activity_efficiency);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setGUI();
    }

    @Override
    protected void onViewCreated(final Bundle savedInstanceState) {
        // set GUI
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
    }

    @Override
    protected void onInitialise(final Bundle savedInstanceState) {
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, makeIntentFilter());
    }

    private void setGUI() {

        mValueInstantPowerL = (TextView) findViewById(R.id.textValueLeftPower);
        mValueInstantPowerR = (TextView) findViewById(R.id.textValueRightPower);
        mLeftLabel = (TextView) findViewById(R.id.textLeft);
        mRightLabel = (TextView) findViewById(R.id.textRight);
        mValueTotalIP = (TextView) findViewById(R.id.textValueTotalMPower);
        mValueEP = (TextView) findViewById(R.id.textValueEPower);
        mEfficiency = (TextView) findViewById(R.id.textValueEfficiency);
        mAddPedal = (Button) findViewById(R.id.buttonAddDevice);

    }

    @Override
    protected void onResume() {
        super.onResume();
        setDefaultUI();
    }

    protected void setDefaultUI() {

        mValueInstantPowerL.setText(R.string.not_available_value);
        mValueInstantPowerR.setText(R.string.not_available_value);
        mValueTotalIP.setText(R.string.not_available_value);
        mValueEP.setText(R.string.not_available_value);
        mEfficiency.setText(R.string.not_available_value);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLeftPedal = null;
        mRightPedal = null;
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    protected int getLoggerProfileTitle() {
        return R.string.cps_feature_title;
    }

    @Override
    protected void onServiceBinded(CPSService.CPSBinder binder) {

    }

    @Override
    protected void onServiceUnbinded() {

    }

    @Override
    public void onDeviceConnecting(final BluetoothDevice device) {

    }

    @Override
    public void onDeviceConnected(BluetoothDevice device) {

    }

    @Override
    public void onDeviceDisconnected(BluetoothDevice device) {
        if (device == mLeftPedal || device == mRightPedal) {
            mAddPedal.setEnabled(true);
        }

    }

    public void onAddCurrentSensorClick(final View view) {
        if (isBLEEnabled()) {
            showDeviceScanningDialog(getFilterUUID());
        } else {
            showBLEDialog();
        }
    }


    @Override
    protected int getAboutTextID() {
        return R.string.cps_about_text;
    }

    @Override
    protected Class<? extends BleMulticonnectProfileService> getServiceClass() {
        return CPSService.class;
    }

    @Override
    protected UUID getFilterUUID() {
        return CPSManager.UUID_CYCLE_POWER_SERVICE;
    }

    private void onSensorLocationFound(BluetoothDevice device, String sensorLocation) {
        Log.i(TAG, device.getName() + ": Sensor Location : " + sensorLocation);
        final String[] locations = getResources().getStringArray(R.array.cps_locations);
        if (locations[7].equals(sensorLocation)) {
            mLeftPedal = device;
            mLeftLabel.setText(device.getName());
        } else if (locations[8].equals(sensorLocation)) {
            mRightPedal = device;
            mRightLabel.setText(device.getName());
        }

        if (mLeftPedal != null && mRightPedal != null) {
            mAddPedal.setEnabled(false);
        }
    }

    private void onInstantPowerReceived(BluetoothDevice device, int instantPower) {
        if (device == mLeftPedal) {
            instantPowerL = instantPower;
            mValueInstantPowerL.setText(String.format(Locale.GERMAN, "%d", instantPower));
        } else if (device == mRightPedal) {
            instantPowerR = instantPower;
            mValueInstantPowerR.setText(String.format(Locale.GERMAN, "%d", instantPower));
        }

        totalInstantPower = instantPowerL + instantPowerR;
        mValueTotalIP.setText(String.format(Locale.GERMAN, "%d", totalInstantPower));
        if (totalInstantPower > 0) {
            updateEfficiency();
        }
    }

    private void onPedalPowerBalanceReceived(BluetoothDevice device, int pedalPowerBalance) {

    }

    private void onAccumulatedTorqueReceived(BluetoothDevice device, int accumulatedTorque) {

    }

    private void onWheelDataReceived(BluetoothDevice device, int wheelRPM, int sessionRev, int totalRev) {

    }

    private void onCrankDataReceived(BluetoothDevice device, int cadence) {
        if (device == mLeftPedal) {
            mCadenceL = cadence;
        } else if (device == mRightPedal) {
            mCadenceR = cadence;
        }
    }

    private void onExtremeForceReceived(BluetoothDevice device, int maxForce, int minForce) {

    }

    private void onExtremeTorqueReceived(BluetoothDevice device, int maxTorque, int minTorque) {

    }

    private void onExtremeAnglesReceived(BluetoothDevice device, int maxAngle, int minAngle) {

    }

    private void onTopDeadSpotAngleReceived(BluetoothDevice device, int topDeadSpotAngle) {

    }

    private void onBottomDeadSpotAngleReceived(BluetoothDevice device, int bottomDeadSpotAngle) {

    }

    private void onAccumulatedEnergyReceived(BluetoothDevice device, int accumulatedEnergy) {

    }

    private void onElectricalPowerReceived(BluetoothDevice device, int ePower) {
        electricalPower = ePower;
        mValueEP.setText(String.format(Locale.GERMAN, "%d", electricalPower));
    }

    private void updateEfficiency() {
        if (totalInstantPower > 0 && electricalPower >= 0) { // Prevent divide by 0
            final int efficiency = (electricalPower * 100) / totalInstantPower;
            mEfficiency.setText(String.format(Locale.GERMAN, "%d", efficiency));
        }
    }

    private void showDeviceScanningDialog(final UUID filter) {
        final ScannerFragment dialog = ScannerFragment.getInstance(filter);
        dialog.show(getSupportFragmentManager(), "scan_fragment");
    }
}
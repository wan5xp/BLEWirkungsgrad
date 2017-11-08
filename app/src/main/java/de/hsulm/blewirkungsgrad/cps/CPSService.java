package de.hsulm.blewirkungsgrad.cps;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import de.hsulm.blewirkungsgrad.EfficiencyActivity;
import de.hsulm.blewirkungsgrad.R;
import de.hsulm.blewirkungsgrad.cps.settings.SettingsFragment;
import de.hsulm.blewirkungsgrad.log.LogContract;
import de.hsulm.blewirkungsgrad.log.Logger;
import de.hsulm.blewirkungsgrad.profile.BleManager;
import de.hsulm.blewirkungsgrad.profile.BleProfileService;
import de.hsulm.blewirkungsgrad.profile.multiconnect.BleMulticonnectProfileService;

/**
 * Created by wan5xp on 02.11.2017.
 */

public class CPSService extends BleMulticonnectProfileService implements CPSManagerCallbacks {
    // Instantaneous Power
    public static final String BROADCAST_INSTANT_POWER = "de.hsulm.blewirkungsgrad.cps.BROADCAST_INSTANT_POWER";
    public static final String EXTRA_INSTANT_POWER = "de.hsulm.blewirkungsgrad.cps.EXTRA_INSTANT_POWER";
    // Pedal Power Balance
    public static final String BROADCAST_PEDAL_POWER_BALANCE = "de.hsulm.blewirkungsgrad.cps.BROADCAST_PEDAL_POWER_BALANCE";
    public static final String EXTRA_PEDAL_POWER_BALANCE = "de.hsulm.blewirkungsgrad.cps.EXTRA_PEDAL_POWER_BALANCE";
    // Accumulated Torque
    public static final String BROADCAST_ACCUMULATED_TORQUE = "de.hsulm.blewirkungsgrad.cps.BROADCAST_ACCUMULATED_TORQUE";
    public static final String EXTRA_ACCUMULATED_TORQUE = "de.hsulm.blewirkungsgrad.cps.EXTRA_ACCUMULATED_TORQUE";
    // Wheel Event
    public static final String BROADCAST_WHEEL_DATA = "de.hsulm.blewirkungsgrad.cps.BROADCAST_WHEEL_DATA";
    public static final String EXTRA_WHEEL_RPM = "de.hsulm.blewirkungsgrad.cps.EXTRA_WHEEL_RPM";
    public static final String EXTRA_SESSION_REV = "de.hsulm.blewirkungsgrad.cps.EXTRA_SESSION_REV";
    public static final String EXTRA_TOTAL_REV = "de.hsulm.blewirkungsgrad.cps.EXTRA_TOTAL_REV";
    // Crank Event
    public static final String BROADCAST_CRANK_DATA = "de.hsulm.blewirkungsgrad.cps.BROADCAST_CRANK_DATA";
    public static final String EXTRA_CADENCE = "de.hsulm.blewirkungsgrad.cps.EXTRA_CADENCE";
    // Extreme Force Magnitude
    public static final String BROADCAST_EXTREME_FORCE = "de.hsulm.blewirkungsgrad.cps.BROADCAST_EXTREME_FORCE";
    public static final String EXTRA_MAX_FORCE = "de.hsulm.blewirkungsgrad.cps.EXTRA_MAX_FORCE";
    public static final String EXTRA_MIN_FORCE = "de.hsulm.blewirkungsgrad.cps.EXTRA_MIN_FORCE";
    // Extreme Torque Magnitude
    public static final String BROADCAST_EXTREME_TORQUE = "de.hsulm.blewirkungsgrad.cps.BROADCAST_EXTREME_TORQUE";
    public static final String EXTRA_MAX_TORQUE = "de.hsulm.blewirkungsgrad.cps.EXTRA_MAX_TORQUE";
    public static final String EXTRA_MIN_TORQUE = "de.hsulm.blewirkungsgrad.cps.EXTRA_MIN_TORQUE";
    // Extreme Angles
    public static final String BROADCAST_EXTREME_ANGLES = "de.hsulm.blewirkungsgrad.cps.BROADCAST_EXTREME_ANGLES";
    public static final String EXTRA_MAX_ANGLE = "de.hsulm.blewirkungsgrad.cps.EXTRA_MAX_ANGLE";
    public static final String EXTRA_MIN_ANGLE = "de.hsulm.blewirkungsgrad.cps.EXTRA_MIN_ANGLE";
    // Top Dead Spot Angle
    public static final String BROADCAST_TOP_DEAD_SPOT_ANGLE = "de.hsulm.blewirkungsgrad.cps.BROADCAST_TOP_DEAD_SPOT_ANGLE";
    public static final String EXTRA_TOP_DEAD_SPOT_ANGLE = "de.hsulm.blewirkungsgrad.cps.EXTRA_TOP_DEAD_SPOT_ANGLE";
    // Bottom Dead Spot Angle
    public static final String BROADCAST_BOTTOM_DEAD_SPOT_ANGLE = "de.hsulm.blewirkungsgrad.cps.BROADCAST_BOTTOM_DEAD_SPOT_ANGLE";
    public static final String EXTRA_BOTTOM_DEAD_SPOT_ANGLE = "de.hsulm.blewirkungsgrad.cps.EXTRA_BOTTOM_DEAD_SPOT_ANGLE";
    // Accumulated Energy
    public static final String BROADCAST_ACCUMULATED_ENERGY = "de.hsulm.blewirkungsgrad.cps.BROADCAST_ACCUMULATED_ENERGY";
    public static final String EXTRA_ACCUMULATED_ENERGY = "de.hsulm.blewirkungsgrad.cps.EXTRA_ACCUMULATED_ENERGY";
    // Sensor Location
    public static final String BROADCAST_SENSOR_LOCATION = "de.hsulm.blewirkungsgrad.cps.BROADCAST_SENSOR_LOCATION";
    public static final String EXTRA_SENSOR_LOCATION = "de.hsulm.blewirkungsgrad.cps.EXTRA_SENSOR_LOCATION";
    // Electrical Power
    public static final String BROADCAST_ELECTRICAL_POWER = "de.hsulm.blewirkungsgrad.cps.BROADCAST_ELECTRICAL_POWER";
    public static final String EXTRA_ELECTRICAL_POWER = "de.hsulm.blewirkungsgrad.cps.EXTRA_ELECTRICAL_POWER";
    private static final String TAG = "CPSService";
    private static final String ACTION_DISCONNECT = "de.hsulm.blewirkungsgrad.cps.ACTION_DISCONNECT";
    private final static int NOTIFICATION_ID = 200;
    private final static int OPEN_ACTIVITY_REQ = 0;
    private final static int DISCONNECT_REQ = 1;

    private final LocalBinder mBinder = new CPSBinder();

    private final BroadcastReceiver mDisconnectActionBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(EXTRA_DEVICE);
            Log.i(TAG, "[Notification] DISCONNECT action pressed");
            mBinder.log(device, LogContract.Log.Level.INFO, "[Notification] DISCONNECT action pressed");
            mBinder.disconnect(device);
        }
    };

    private int mFirstWheelRevolutions = -1;
    private int mLastWheelRevolutions = -1;
    private int mLastWheelEventTime = -1;
    private int mLastCrankRevolutions = -1;
    private int mLastCrankEventTime = -1;

    @Override
    public void onInstantPowerReceived(BluetoothDevice device, int instantPower) {
        mBinder.log(device, LogContract.Log.Level.INFO, "Instantaneous Power: " + instantPower + "W");

        final Intent broadcast = new Intent(BROADCAST_INSTANT_POWER);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_INSTANT_POWER, instantPower);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

    }

    @Override
    public void onPedalPowerBalanceReceived(BluetoothDevice device, int pedalPowerBalance) {
        mBinder.log(device, LogContract.Log.Level.INFO, "Pedal Power Balance: " + pedalPowerBalance + "%");

        final float powerBalance = pedalPowerBalance / 2.0f;

        final Intent broadcast = new Intent(BROADCAST_PEDAL_POWER_BALANCE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_PEDAL_POWER_BALANCE, powerBalance);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onAccumulatedTorqueReceived(BluetoothDevice device, int accumulatedTorque) {
        mBinder.log(device, LogContract.Log.Level.INFO, "Accumulated Torque: " + accumulatedTorque + "Nm");

        final float torque = accumulatedTorque / 32.0f;

        final Intent broadcast = new Intent(BROADCAST_ACCUMULATED_TORQUE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_ACCUMULATED_TORQUE, torque);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onWheelMeasurementReceived(BluetoothDevice device, int wheelRevolutions, int lastWheelEventTime) {
        mBinder.log(device, LogContract.Log.Level.INFO, "Wheel rev: " + wheelRevolutions + "\nLast wheel event time: " + lastWheelEventTime + " ms");

        if (mFirstWheelRevolutions < 0)
            mFirstWheelRevolutions = wheelRevolutions;

        if (mLastWheelEventTime == lastWheelEventTime)
            return;

        if (mLastWheelRevolutions >= 0) {
            float timeDifference;
            if (lastWheelEventTime < mLastWheelEventTime)
                timeDifference = (65535 + lastWheelEventTime - mLastWheelEventTime) / 2048.0f; // [s]
            else
                timeDifference = (lastWheelEventTime - mLastWheelEventTime) / 2048.0f; // [s]
            final int revDifference = wheelRevolutions - mLastWheelRevolutions;
            final int totalRev = wheelRevolutions;
            final int sessionRev = wheelRevolutions - mFirstWheelRevolutions;
            final float wheelRPM = revDifference * 60.0f / timeDifference;

            final Intent broadcast = new Intent(BROADCAST_WHEEL_DATA);
            broadcast.putExtra(EXTRA_DEVICE, device);
            broadcast.putExtra(EXTRA_WHEEL_RPM, (int) wheelRPM);
            broadcast.putExtra(EXTRA_SESSION_REV, sessionRev);
            broadcast.putExtra(EXTRA_TOTAL_REV, totalRev);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        }
        mLastWheelRevolutions = wheelRevolutions;
        mLastWheelEventTime = lastWheelEventTime;
    }

    @Override
    public void onCrankMeasurementReceived(BluetoothDevice device, int crankRevolutions, int lastCrankEventTime) {
        mBinder.log(device, LogContract.Log.Level.INFO, "Crank rev: " + crankRevolutions + "\nLast crank event time: " + lastCrankEventTime + " ms");

        if (mLastCrankEventTime == lastCrankEventTime)
            return;

        if (mLastCrankRevolutions >= 0) {
            float timeDifference;
            if (lastCrankEventTime < mLastCrankEventTime)
                timeDifference = (65535 + lastCrankEventTime - mLastCrankEventTime) / 1024.0f; // [s]
            else
                timeDifference = (lastCrankEventTime - mLastCrankEventTime) / 1024.0f; // [s]

            final float crankCadence = (crankRevolutions - mLastCrankRevolutions) * 60.0f / timeDifference;
            if (crankCadence > 0) {
                final Intent broadcast = new Intent(BROADCAST_CRANK_DATA);
                broadcast.putExtra(EXTRA_DEVICE, device);
                broadcast.putExtra(EXTRA_CADENCE, (int) crankCadence);
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
            }
        }
        mLastCrankRevolutions = crankRevolutions;
        mLastCrankEventTime = lastCrankEventTime;
    }

    @Override
    public void onExtremeForceMagnitudeReceived(BluetoothDevice device, int maxForceMagnitude, int minForceMagnitude) {
        mBinder.log(device, LogContract.Log.Level.INFO, "Maximum Force Magnitude: " + maxForceMagnitude + "N");
        mBinder.log(device, LogContract.Log.Level.INFO, "Minimum Force Magnitude: " + minForceMagnitude + "N");

        final Intent broadcast = new Intent(BROADCAST_EXTREME_FORCE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_MAX_FORCE, maxForceMagnitude);
        broadcast.putExtra(EXTRA_MIN_FORCE, minForceMagnitude);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onExtremeTorqueMagnitudeReceived(BluetoothDevice device, int maxTorqueMagnitude, int minTorqueMagnitude) {
        mBinder.log(device, LogContract.Log.Level.INFO, "Maximum Torque Magnitude: " + maxTorqueMagnitude + "Nm");
        mBinder.log(device, LogContract.Log.Level.INFO, "Minimum Torque Magnitude: " + minTorqueMagnitude + "Nm");

        final float max = maxTorqueMagnitude / 32.0f;
        final float min = minTorqueMagnitude / 32.0f;
        final Intent broadcast = new Intent(BROADCAST_EXTREME_TORQUE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_MAX_TORQUE, max);
        broadcast.putExtra(EXTRA_MIN_TORQUE, min);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onExtremeAnglesReceived(BluetoothDevice device, int maxAngle, int minAngle) {
        mBinder.log(device, LogContract.Log.Level.INFO, "Angle at Maximum: " + maxAngle + "°");
        mBinder.log(device, LogContract.Log.Level.INFO, "Angle at Minimum: " + minAngle + "°");

        final Intent broadcast = new Intent(BROADCAST_EXTREME_ANGLES);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_MAX_ANGLE, maxAngle);
        broadcast.putExtra(EXTRA_MIN_ANGLE, minAngle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onTopDeadSpotAngleReceived(BluetoothDevice device, int topDeadSpotAngle) {
        mBinder.log(device, LogContract.Log.Level.INFO, "Top Dead Spot Angle: " + topDeadSpotAngle + "°");

        final Intent broadcast = new Intent(BROADCAST_TOP_DEAD_SPOT_ANGLE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_TOP_DEAD_SPOT_ANGLE, topDeadSpotAngle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onBottomDeadSpotAngleReceived(BluetoothDevice device, int bottomDeadSpotAngle) {
        mBinder.log(device, LogContract.Log.Level.INFO, "Bottom Dead Spot Angle: " + bottomDeadSpotAngle + "°");

        final Intent broadcast = new Intent(BROADCAST_BOTTOM_DEAD_SPOT_ANGLE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_BOTTOM_DEAD_SPOT_ANGLE, bottomDeadSpotAngle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onAccumulatedEnergyReceived(BluetoothDevice device, int accumulatedEnergy) {
        mBinder.log(device, LogContract.Log.Level.INFO, "Accumulated Energy: " + accumulatedEnergy + "kJ");

        final Intent broadcast = new Intent(BROADCAST_ACCUMULATED_ENERGY);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_ACCUMULATED_ENERGY, accumulatedEnergy);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onSensorLocationFound(BluetoothDevice device, String sensorLocation) {
        final Intent broadcast = new Intent(BROADCAST_SENSOR_LOCATION);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_SENSOR_LOCATION, sensorLocation);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onElectricalPowerReceived(BluetoothDevice device, int ePower) {
        final Intent broadcast = new Intent(BROADCAST_ELECTRICAL_POWER);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_ELECTRICAL_POWER, ePower);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    protected LocalBinder getBinder() {
        return mBinder;
    }

    @Override
    protected BleManager<CPSManagerCallbacks> initializeManager() {
        return new CPSManager(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_DISCONNECT);
        registerReceiver(mDisconnectActionBroadcastReceiver, filter);
    }

    @Override
    public void onDestroy() {
        cancelNotification();
        unregisterReceiver(mDisconnectActionBroadcastReceiver);

        super.onDestroy();
    }

    @Override
    protected void onRebind() {
        cancelNotification();
    }

    @Override
    protected void onUnbind() {
    }

    private void cancelNotification() {
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID);
    }

    public class CPSBinder extends LocalBinder {
        // empty
    }
}

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

import de.hsulm.blewirkungsgrad.EfficiencyActivity;
import de.hsulm.blewirkungsgrad.R;
import de.hsulm.blewirkungsgrad.cps.settings.SettingsFragment;
import de.hsulm.blewirkungsgrad.log.Logger;
import de.hsulm.blewirkungsgrad.profile.BleManager;
import de.hsulm.blewirkungsgrad.profile.BleProfileService;

/**
 * Created by wan5xp on 02.11.2017.
 */

public class CPSService extends BleProfileService implements CPSManagerCallbacks {
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
    public static final String EXTRA_SPEED = "de.hsulm.blewirkungsgrad.cps.EXTRA_SPEED";
    /**
     * Distance in meters
     */
    public static final String EXTRA_DISTANCE = "de.hsulm.blewirkungsgrad.cps.EXTRA_DISTANCE";
    /**
     * Total distance in meters
     */
    public static final String EXTRA_TOTAL_DISTANCE = "de.hsulm.blewirkungsgrad.cps.EXTRA_TOTAL_DISTANCE";
    // Crank Event
    public static final String BROADCAST_CRANK_DATA = "de.hsulm.blewirkungsgrad.cps.BROADCAST_CRANK_DATA";
    public static final String EXTRA_GEAR_RATIO = "de.hsulm.blewirkungsgrad.cps.EXTRA_GEAR_RATIO";
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
    // Accumulated Energy
    public static final String BROADCAST_SENSOR_LOCATION = "de.hsulm.blewirkungsgrad.cps.BROADCAST_SENSOR_LOCATION";
    public static final String EXTRA_SENSOR_LOCATION = "de.hsulm.blewirkungsgrad.cps.EXTRA_SENSOR_LOCATION";
    private static final String TAG = "CPSService";
    private static final String ACTION_DISCONNECT = "de.hsulm.blewirkungsgrad.cps.ACTION_DISCONNECT";
    private final static int NOTIFICATION_ID = 200;
    private final static int OPEN_ACTIVITY_REQ = 0;
    private final static int DISCONNECT_REQ = 1;
    private final LocalBinder mBinder = new CPSBinder();
    private final BroadcastReceiver mDisconnectActionBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            Logger.i(getLogSession(), "[Notification] Disconnect action pressed");
            if (isConnected())
                getBinder().disconnect();
            else
                stopSelf();
        }
    };
    private CPSManager mManager;
    private int mFirstWheelRevolutions = -1;
    private int mLastWheelRevolutions = -1;
    private int mLastWheelEventTime = -1;
    private float mWheelCadence = -1;
    private int mLastCrankRevolutions = -1;
    private int mLastCrankEventTime = -1;

    @Override
    public void onInstantPowerReceived(BluetoothDevice device, int instantPower) {
        Logger.a(getLogSession(), "Instantaneous Power: " + instantPower + "W");

        final Intent broadcast = new Intent(BROADCAST_INSTANT_POWER);
        broadcast.putExtra(EXTRA_DEVICE, getBluetoothDevice());
        broadcast.putExtra(EXTRA_INSTANT_POWER, instantPower);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

    }

    @Override
    public void onPedalPowerBalanceReceived(BluetoothDevice device, int pedalPowerBalance) {
        Logger.a(getLogSession(), "Pedal Power Balance: " + pedalPowerBalance + "%");

        final float powerBalance = pedalPowerBalance / 2.0f;

        final Intent broadcast = new Intent(BROADCAST_PEDAL_POWER_BALANCE);
        broadcast.putExtra(EXTRA_DEVICE, getBluetoothDevice());
        broadcast.putExtra(EXTRA_PEDAL_POWER_BALANCE, powerBalance);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onAccumulatedTorqueReceived(BluetoothDevice device, int accumulatedTorque) {
        Logger.a(getLogSession(), "Accumulated Torque: " + accumulatedTorque + "Nm");

        final float torque = accumulatedTorque / 32.0f;

        final Intent broadcast = new Intent(BROADCAST_ACCUMULATED_TORQUE);
        broadcast.putExtra(EXTRA_DEVICE, getBluetoothDevice());
        broadcast.putExtra(EXTRA_ACCUMULATED_TORQUE, torque);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onWheelMeasurementReceived(BluetoothDevice device, int wheelRevolutions, int lastWheelEventTime) {
        Logger.a(getLogSession(), "Wheel rev: " + wheelRevolutions + "\nLast wheel event time: " + lastWheelEventTime + " ms");

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final int circumference = Integer.parseInt(preferences.getString(SettingsFragment.SETTINGS_WHEEL_SIZE, String.valueOf(SettingsFragment.SETTINGS_WHEEL_SIZE_DEFAULT))); // [mm]

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
            final float distanceDifference = (wheelRevolutions - mLastWheelRevolutions) * circumference / 1000.0f; // [m]
            final float totalDistance = (float) wheelRevolutions * (float) circumference / 1000.0f; // [m]
            final float distance = (float) (wheelRevolutions - mFirstWheelRevolutions) * (float) circumference / 1000.0f; // [m]
            final float speed = distanceDifference / timeDifference;
            mWheelCadence = (wheelRevolutions - mLastWheelRevolutions) * 60.0f / timeDifference;

            final Intent broadcast = new Intent(BROADCAST_WHEEL_DATA);
            broadcast.putExtra(EXTRA_DEVICE, getBluetoothDevice());
            broadcast.putExtra(EXTRA_SPEED, speed);
            broadcast.putExtra(EXTRA_DISTANCE, distance);
            broadcast.putExtra(EXTRA_TOTAL_DISTANCE, totalDistance);
            LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        }
        mLastWheelRevolutions = wheelRevolutions;
        mLastWheelEventTime = lastWheelEventTime;
    }

    @Override
    public void onCrankMeasurementReceived(BluetoothDevice device, int crankRevolutions, int lastCrankEventTime) {
        Logger.a(getLogSession(), "Crank rev: " + crankRevolutions + "\nLast crank event time: " + lastCrankEventTime + " ms");

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
                final float gearRatio = mWheelCadence / crankCadence;

                final Intent broadcast = new Intent(BROADCAST_CRANK_DATA);
                broadcast.putExtra(EXTRA_DEVICE, getBluetoothDevice());
                broadcast.putExtra(EXTRA_GEAR_RATIO, gearRatio);
                broadcast.putExtra(EXTRA_CADENCE, (int) crankCadence);
                LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
            }
        }
        mLastCrankRevolutions = crankRevolutions;
        mLastCrankEventTime = lastCrankEventTime;
    }

    @Override
    public void onExtremeForceMagnitudeReceived(BluetoothDevice device, int maxForceMagnitude, int minForceMagnitude) {
        Logger.a(getLogSession(), "Maximum Force Magnitude: " + maxForceMagnitude + "N");
        Logger.a(getLogSession(), "Minimum Force Magnitude: " + minForceMagnitude + "N");

        final Intent broadcast = new Intent(BROADCAST_EXTREME_FORCE);
        broadcast.putExtra(EXTRA_DEVICE, getBluetoothDevice());
        broadcast.putExtra(EXTRA_MAX_FORCE, maxForceMagnitude);
        broadcast.putExtra(EXTRA_MIN_FORCE, minForceMagnitude);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onExtremeTorqueMagnitudeReceived(BluetoothDevice device, int maxTorqueMagnitude, int minTorqueMagnitude) {
        Logger.a(getLogSession(), "Maximum Torque Magnitude: " + maxTorqueMagnitude + "Nm");
        Logger.a(getLogSession(), "Minimum Torque Magnitude: " + minTorqueMagnitude + "Nm");

        final float max = maxTorqueMagnitude / 32.0f;
        final float min = minTorqueMagnitude / 32.0f;
        final Intent broadcast = new Intent(BROADCAST_EXTREME_TORQUE);
        broadcast.putExtra(EXTRA_DEVICE, getBluetoothDevice());
        broadcast.putExtra(EXTRA_MAX_TORQUE, max);
        broadcast.putExtra(EXTRA_MIN_TORQUE, min);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onExtremeAnglesReceived(BluetoothDevice device, int maxAngle, int minAngle) {
        Logger.a(getLogSession(), "Angle at Maximum: " + maxAngle + "°");
        Logger.a(getLogSession(), "Angle at Minimum: " + minAngle + "°");

        final Intent broadcast = new Intent(BROADCAST_EXTREME_ANGLES);
        broadcast.putExtra(EXTRA_DEVICE, getBluetoothDevice());
        broadcast.putExtra(EXTRA_MAX_ANGLE, maxAngle);
        broadcast.putExtra(EXTRA_MIN_ANGLE, minAngle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onTopDeadSpotAngleReceived(BluetoothDevice device, int topDeadSpotAngle) {
        Logger.a(getLogSession(), "Top Dead Spot Angle: " + topDeadSpotAngle + "°");

        final Intent broadcast = new Intent(BROADCAST_TOP_DEAD_SPOT_ANGLE);
        broadcast.putExtra(EXTRA_DEVICE, getBluetoothDevice());
        broadcast.putExtra(EXTRA_TOP_DEAD_SPOT_ANGLE, topDeadSpotAngle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onBottomDeadSpotAngleReceived(BluetoothDevice device, int bottomDeadSpotAngle) {
        Logger.a(getLogSession(), "Bottom Dead Spot Angle: " + bottomDeadSpotAngle + "°");

        final Intent broadcast = new Intent(BROADCAST_BOTTOM_DEAD_SPOT_ANGLE);
        broadcast.putExtra(EXTRA_DEVICE, getBluetoothDevice());
        broadcast.putExtra(EXTRA_BOTTOM_DEAD_SPOT_ANGLE, bottomDeadSpotAngle);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onAccumulatedEnergyReceived(BluetoothDevice device, int accumulatedEnergy) {
        Logger.a(getLogSession(), "Accumulated Energy: " + accumulatedEnergy + "kJ");

        final Intent broadcast = new Intent(BROADCAST_ACCUMULATED_ENERGY);
        broadcast.putExtra(EXTRA_DEVICE, getBluetoothDevice());
        broadcast.putExtra(EXTRA_ACCUMULATED_ENERGY, accumulatedEnergy);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onSensorLocationFound(BluetoothDevice device, String sensorLocation) {
        final Intent broadcast = new Intent(BROADCAST_SENSOR_LOCATION);
        broadcast.putExtra(EXTRA_DEVICE, getBluetoothDevice());
        broadcast.putExtra(EXTRA_SENSOR_LOCATION, sensorLocation);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    protected LocalBinder getBinder() {
        return mBinder;
    }

    @Override
    protected BleManager<CPSManagerCallbacks> initializeManager() {
        return mManager = new CPSManager(this);
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
        // when the activity closes we need to show the notification that user is connected to the sensor
        createNotification(R.string.cps_notification_connected_message, 0);
    }

    private void createNotification(final int messageResId, final int defaults) {
        final Intent parentIntent = new Intent(this, EfficiencyActivity.class);
        parentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        final Intent disconnect = new Intent(ACTION_DISCONNECT);
        final PendingIntent disconnectAction = PendingIntent.getBroadcast(this, DISCONNECT_REQ, disconnect, PendingIntent.FLAG_UPDATE_CURRENT);

        // both activities above have launchMode="singleTask" in the AndroidManifest.xml file, so if the task is already running, it will be resumed
        final PendingIntent pendingIntent = PendingIntent.getActivities(this, OPEN_ACTIVITY_REQ, new Intent[]{parentIntent}, PendingIntent.FLAG_UPDATE_CURRENT);
        final android.support.v7.app.NotificationCompat.Builder builder = new android.support.v7.app.NotificationCompat.Builder(this);
        builder.setContentIntent(pendingIntent);
        builder.setContentTitle(getString(R.string.app_name)).setContentText(getString(messageResId, getDeviceName()));
        builder.setSmallIcon(R.drawable.ic_stat_notify_cps);
        builder.setShowWhen(defaults != 0).setDefaults(defaults).setAutoCancel(true).setOngoing(true);
        builder.addAction(new android.support.v7.app.NotificationCompat.Action(R.drawable.ic_action_bluetooth, getString(R.string.cps_notification_action_disconnect), disconnectAction));

        final Notification notification = builder.build();
        final NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification);

    }

    private void cancelNotification() {
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID);
    }

    public class CPSBinder extends LocalBinder {
        // empty
    }
}

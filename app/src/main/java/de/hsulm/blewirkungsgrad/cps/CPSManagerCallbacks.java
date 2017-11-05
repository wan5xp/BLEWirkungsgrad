package de.hsulm.blewirkungsgrad.cps;

import android.bluetooth.BluetoothDevice;

import de.hsulm.blewirkungsgrad.profile.BleManagerCallbacks;

/**
 * Created by wan5xp on 02.11.2017.
 */

public interface CPSManagerCallbacks extends BleManagerCallbacks {

    /*
     * Called when Instantaneous Power value is received
     * @param device a device from which the value was obtained
     * @param instantPower value of Instantaneous Power
     *
     */
    void onInstantPowerReceived(final BluetoothDevice device, final int instantPower);

    void onPedalPowerBalanceReceived(final BluetoothDevice device, final int pedalPowerBalance);

    void onAccumulatedTorqueReceived(final BluetoothDevice device, final int accumulatedTorque);

    void onWheelMeasurementReceived(final BluetoothDevice device, final int wheelRevolutions, final int lastWheelEventTime);

    void onCrankMeasurementReceived(final BluetoothDevice device, final int crankRevolutions, final int lastCrankEventTime);

    void onExtremeForceMagnitudeReceived(final BluetoothDevice device, final int maxForceMagnitude, final int minForceMagnitude);

    void onExtremeTorqueMagnitudeReceived(final BluetoothDevice device, final int maxTorqueMagnitude, final int minTorqueMagnitude);

    void onExtremeAnglesReceived(final BluetoothDevice device, final int maxAngle, final int minAngle);

    void onTopDeadSpotAngleReceived(final BluetoothDevice device, final int topDeadSpotAngle);

    void onBottomDeadSpotAngleReceived(final BluetoothDevice device, final int bottomDeadSpotAngle);

    void onAccumulatedEnergyReceived(final BluetoothDevice device, final int accumulatedEnergy);

    void onSensorLocationFound(final BluetoothDevice device, final String sensorLocation);

}

package de.hsulm.blewirkungsgrad.cps;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;

import java.util.Deque;
import java.util.LinkedList;
import java.util.UUID;

import de.hsulm.blewirkungsgrad.R;
import de.hsulm.blewirkungsgrad.log.Logger;
import de.hsulm.blewirkungsgrad.parser.CPSMeasurementParser;
import de.hsulm.blewirkungsgrad.profile.BleManager;

/**
 * Created by wan5xp on 02.11.2017.
 */

public class CPSManager extends BleManager<CPSManagerCallbacks> {
    // Service UUID
    public final static UUID UUID_CYCLE_POWER_SERVICE = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb");
    // Characteristic UUID
    public final static UUID UUID_CYCLE_POWER_MEASUREMENT = UUID.fromString("00002A63-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_CYCLE_POWER_FEATURE = UUID.fromString("00002A65-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_SENSOR_LOCATION = UUID.fromString("00002A5D-0000-1000-8000-00805f9b34fb");
    private static final String TAG = "CPSManager";
    // Flags for CPSMeasurement characteristic
    private static final byte PEDAL_POWER_BALANCE_PRESENT = 0x01; // 1 bit
    private static final byte PEDAL_POWER_BALANCE_REFERENCE_LEFT = 0x02; // 1 bit
    private static final byte ACCUMULATED_TORQUE_PRESENT = 0x04; // 1 bit
    private static final byte ACCUMULATED_TORQUE_SOURCE_CRANK = 0x08; // 1 bit
    private static final byte WHEEL_REVOLUTIONS_DATA_PRESENT = 0x10; // 1 bit
    private static final byte CRANK_REVOLUTION_DATA_PRESENT = 0x20; // 1 bit
    private static final byte EXTREME_FORCE_MAGNITUDE_PRESENT = 0x40; // 1 bit
    private static final byte EXTREME_TORQUE_MAGNITUDE_PRESENT = (byte) 0x80; // 1 bit
    private static final byte EXTREME_ANGLE_PRESENT = 0x01; // 1 bit
    private static final byte TOP_DEAD_SPOT_ANGLE_PRESENT = 0x02; // 1 bit
    private static final byte BOTTOM_DEAD_SPOT_ANGLE_PRESENT = 0x04; // 1 bit
    private static final byte ACCUMULATED_ENERGY_PRESENT = 0x08; // 1 bit
    private static final byte OFFSET_COMPENSATION_INDICATOR = 0x10; // 1 bit

    private BluetoothGattCharacteristic mCPSMeasurementCharacteristic;
    private BluetoothGattCharacteristic mCPSFeatureCharacteristic;
    private BluetoothGattCharacteristic mSensorLocationCharacteristic;
    private final BleManagerGattCallback mGattCallback = new BleManagerGattCallback() {

        @Override
        protected Deque<Request> initGatt(final BluetoothGatt gatt) {
            final LinkedList<Request> requests = new LinkedList<>();
            if (mSensorLocationCharacteristic != null) {
                requests.add(Request.newReadRequest(mSensorLocationCharacteristic));
            }
            requests.add(Request.newEnableNotificationsRequest(mCPSMeasurementCharacteristic));
            return requests;
        }

        @Override
        protected boolean isRequiredServiceSupported(BluetoothGatt gatt) {
            final BluetoothGattService service = gatt.getService(UUID_CYCLE_POWER_SERVICE);
            if (service != null) {
                mCPSMeasurementCharacteristic = service.getCharacteristic(UUID_CYCLE_POWER_MEASUREMENT);
                mCPSFeatureCharacteristic = service.getCharacteristic(UUID_CYCLE_POWER_FEATURE);
                mSensorLocationCharacteristic = service.getCharacteristic(UUID_SENSOR_LOCATION);
            }
            return (mCPSMeasurementCharacteristic != null) &&
                    (mCPSFeatureCharacteristic != null) &&
                    (mSensorLocationCharacteristic != null);
        }

        @Override
        protected void onDeviceDisconnected() {
            mCPSMeasurementCharacteristic = null;
            mCPSFeatureCharacteristic = null;
            mSensorLocationCharacteristic = null;
        }

        @Override
        public void onCharacteristicNotified(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            Logger.a(mLogSession, "\"" + CPSMeasurementParser.parse(characteristic) + "\" received");

            // Decode the new data
            int offset = 0;
            final int flags1 = characteristic.getValue()[offset]; // 1 byte
            final int flags2 = characteristic.getValue()[offset += 1]; // 1 byte
            offset += 1;

            // Read Instantaneous Power
            final int instantPower = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset);
            offset += 2;
            mCallbacks.onInstantPowerReceived(gatt.getDevice(), instantPower);

            final boolean pedalPowerBalancePresent = (flags1 & PEDAL_POWER_BALANCE_PRESENT) > 0;
            final boolean pedalPowerBalanceReferenceLeft = (flags1 & PEDAL_POWER_BALANCE_REFERENCE_LEFT) > 0;
            final boolean accumulatedTorquePresent = (flags1 & ACCUMULATED_TORQUE_PRESENT) > 0;
            final boolean accumulatedTorqueSourceCrank = (flags1 & ACCUMULATED_TORQUE_SOURCE_CRANK) > 0;
            final boolean wheelRevPresent = (flags1 & WHEEL_REVOLUTIONS_DATA_PRESENT) > 0;
            final boolean crankRevPresent = (flags1 & CRANK_REVOLUTION_DATA_PRESENT) > 0;
            final boolean extremeForceMagnitudePresent = (flags1 & EXTREME_FORCE_MAGNITUDE_PRESENT) > 0;
            final boolean extremeTorqueMagnitudePresent = (flags1 & EXTREME_TORQUE_MAGNITUDE_PRESENT) > 0;
            final boolean extremeAnglePresent = (flags2 & EXTREME_ANGLE_PRESENT) > 0;
            final boolean topDeadSpotAnglePresent = (flags2 & TOP_DEAD_SPOT_ANGLE_PRESENT) > 0;
            final boolean bottomDeadSpotAnglePresent = (flags2 & BOTTOM_DEAD_SPOT_ANGLE_PRESENT) > 0;
            final boolean accumulatedEnergyPresent = (flags2 & ACCUMULATED_ENERGY_PRESENT) > 0;
            final boolean offsetCompensationIndicator = (flags2 & OFFSET_COMPENSATION_INDICATOR) > 0;

            if (pedalPowerBalancePresent) {
                final int pedalPowerBalance = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
                offset += 1;

                // Notify listener about the new measurement
                mCallbacks.onPedalPowerBalanceReceived(gatt.getDevice(), pedalPowerBalance);
            }

            if (accumulatedTorquePresent) {
                final int accumulatedTorque = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                offset += 2;

                // Notify listener about the new measurement
                mCallbacks.onAccumulatedTorqueReceived(gatt.getDevice(), accumulatedTorque);
            }

            if (wheelRevPresent) {
                final int wheelRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, offset);
                offset += 4;

                final int lastWheelEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset); // 1/1024 s
                offset += 2;

                // Notify listener about the new measurement
                mCallbacks.onWheelMeasurementReceived(gatt.getDevice(), wheelRevolutions, lastWheelEventTime);
            }

            if (crankRevPresent) {
                final int crankRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                offset += 2;

                final int lastCrankEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                offset += 2;

                // Notify listener about the new measurement
                mCallbacks.onCrankMeasurementReceived(gatt.getDevice(), crankRevolutions, lastCrankEventTime);
            }

            if (extremeForceMagnitudePresent) {
                final int maxForceMagnitude = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset);
                offset += 2;

                final int minForceMagnitude = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset);
                offset += 2;

                // Notify listener about the new measurement
                mCallbacks.onExtremeForceMagnitudeReceived(gatt.getDevice(), maxForceMagnitude, minForceMagnitude);
            }

            if (extremeTorqueMagnitudePresent) {
                final int maxTorqueMagnitude = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset);
                offset += 2;

                final int minTorqueMagnitude = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset);
                offset += 2;

                // Notify listener about the new measurement
                mCallbacks.onExtremeTorqueMagnitudeReceived(gatt.getDevice(), maxTorqueMagnitude, minTorqueMagnitude);
            }

            if (extremeAnglePresent) {
                final int maxAngle = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset) & 0x0FF;
                offset += 1;

                final int minAngle = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset) >> 8;
                offset += 2;

                // Notify listener about the new measurement
                mCallbacks.onExtremeAnglesReceived(gatt.getDevice(), maxAngle, minAngle);
            }

            if (topDeadSpotAnglePresent) {
                final int topDeadSpotAngle = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                offset += 2;

                // Notify listener about the new measurement
                mCallbacks.onTopDeadSpotAngleReceived(gatt.getDevice(), topDeadSpotAngle);
            }

            if (bottomDeadSpotAnglePresent) {
                final int bottomDeadSpotAngle = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                offset += 2;

                // Notify listener about the new measurement
                mCallbacks.onBottomDeadSpotAngleReceived(gatt.getDevice(), bottomDeadSpotAngle);
            }

            if (accumulatedEnergyPresent) {
                final int accumulatedEnergy = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
                // offset += 2;

                // Notify listener about the new measurement
                mCallbacks.onAccumulatedEnergyReceived(gatt.getDevice(), accumulatedEnergy);
            }
        }

        @Override
        public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            final String sensorLocation = getSensorLocation(characteristic.getValue()[0]);
            Logger.a(mLogSession, "\" Sensor Location: " + sensorLocation + "\" received");
            mCallbacks.onSensorLocationFound(gatt.getDevice(), sensorLocation);
        }
    };

    public CPSManager(final Context context) {
        super(context);
    }

    @Override
    protected BleManagerGattCallback getGattCallback() {
        return mGattCallback;
    }

    private String getSensorLocation(final byte sensorPositionValue) {
        final String[] locations = getContext().getResources().getStringArray(R.array.cps_locations);
        if (sensorPositionValue > locations.length)
            return getContext().getString(R.string.cps_location_other);
        return locations[sensorPositionValue];
    }
}

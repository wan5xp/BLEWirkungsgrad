package de.hsulm.blewirkungsgrad.parser;

import android.bluetooth.BluetoothGattCharacteristic;

/**
 * Created by wan5xp on 02.11.2017.
 */

public class CPSMeasurementParser {
    // Flags for CPSMeasurement characteristic
    private static final byte PEDAL_POWER_BALANCE_PRESENT = 0x01; // 1 bit
    //private static final byte PEDAL_POWER_BALANCE_REFERENCE_LEFT = 0x02; // 1 bit
    private static final byte ACCUMULATED_TORQUE_PRESENT = 0x04; // 1 bit
    //private static final byte ACCUMULATED_TORQUE_SOURCE_CRANK = 0x08; // 1 bit
    private static final byte WHEEL_REVOLUTIONS_DATA_PRESENT = 0x10; // 1 bit
    private static final byte CRANK_REVOLUTION_DATA_PRESENT = 0x20; // 1 bit
    private static final byte EXTREME_FORCE_MAGNITUDE_PRESENT = 0x40; // 1 bit
    private static final byte EXTREME_TORQUE_MAGNITUDE_PRESENT = (byte) 0x80; // 1 bit
    private static final byte EXTREME_ANGLE_PRESENT = 0x01; // 1 bit
    private static final byte TOP_DEAD_SPOT_ANGLE_PRESENT = 0x02; // 1 bit
    private static final byte BOTTOM_DEAD_SPOT_ANGLE_PRESENT = 0x04; // 1 bit
    private static final byte ACCUMULATED_ENERGY_PRESENT = 0x08; // 1 bit
    //private static final byte OFFSET_COMPENSATION_INDICATOR = 0x10; // 1 bit

    public static String parse(final BluetoothGattCharacteristic characteristic) {
        int offset = 0;
        final int flags1 = characteristic.getValue()[offset]; // 1 byte
        final int flags2 = characteristic.getValue()[offset += 1]; // 1 byte
        offset += 1;

        int instantPower = 0;
        // Read Instantaneous Power
        instantPower = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset);
        offset += 2;

        final boolean pedalPowerBalancePresent = (flags1 & PEDAL_POWER_BALANCE_PRESENT) > 0;
        //final boolean pedalPowerBalanceReferenceLeft = (flags1 & PEDAL_POWER_BALANCE_REFERENCE_LEFT) > 0;
        final boolean accumulatedTorquePresent = (flags1 & ACCUMULATED_TORQUE_PRESENT) > 0;
        //final boolean accumulatedTorqueSourceCrank = (flags1 & ACCUMULATED_TORQUE_SOURCE_CRANK) > 0;
        final boolean wheelRevPresent = (flags1 & WHEEL_REVOLUTIONS_DATA_PRESENT) > 0;
        final boolean crankRevPresent = (flags1 & CRANK_REVOLUTION_DATA_PRESENT) > 0;
        final boolean extremeForceMagnitudePresent = (flags1 & EXTREME_FORCE_MAGNITUDE_PRESENT) > 0;
        final boolean extremeTorqueMagnitudePresent = (flags1 & EXTREME_TORQUE_MAGNITUDE_PRESENT) > 0;
        final boolean extremeAnglePresent = (flags2 & EXTREME_ANGLE_PRESENT) > 0;
        final boolean topDeadSpotAnglePresent = (flags2 & TOP_DEAD_SPOT_ANGLE_PRESENT) > 0;
        final boolean bottomDeadSpotAnglePresent = (flags2 & BOTTOM_DEAD_SPOT_ANGLE_PRESENT) > 0;
        final boolean accumulatedEnergyPresent = (flags2 & ACCUMULATED_ENERGY_PRESENT) > 0;
        //final boolean offsetCompensationIndicator = (flags2 & OFFSET_COMPENSATION_INDICATOR) > 0;

        int pedalPowerBalance = 0;
        if (pedalPowerBalancePresent) {
            pedalPowerBalance = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
            offset += 1;

        }

        int accumulatedTorque = 0;
        if (accumulatedTorquePresent) {
            accumulatedTorque = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
            offset += 2;

        }

        int wheelRevolutions = 0;
        int lastWheelEventTime = 0;
        if (wheelRevPresent) {
            wheelRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, offset);
            offset += 4;

            lastWheelEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset); // 1/1024 s
            offset += 2;

        }

        int crankRevolutions = 0;
        int lastCrankEventTime = 0;
        if (crankRevPresent) {
            crankRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
            offset += 2;

            lastCrankEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
            offset += 2;

        }

        int maxForceMagnitude = 0;
        int minForceMagnitude = 0;
        if (extremeForceMagnitudePresent) {
            maxForceMagnitude = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset);
            offset += 2;

            minForceMagnitude = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset);
            offset += 2;

        }

        int maxTorqueMagnitude = 0;
        int minTorqueMagnitude = 0;
        if (extremeTorqueMagnitudePresent) {
            maxTorqueMagnitude = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset);
            offset += 2;

            minTorqueMagnitude = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, offset);
            offset += 2;

        }

        int maxAngle = 0;
        int minAngle = 0;
        if (extremeAnglePresent) {
            maxAngle = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset) & 0x0FF;
            offset += 1;

            minAngle = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset) >> 8;
            offset += 2;

        }

        int topDeadSpotAngle = 0;
        if (topDeadSpotAnglePresent) {
            topDeadSpotAngle = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
            offset += 2;

        }

        int bottomDeadSpotAngle = 0;
        if (bottomDeadSpotAnglePresent) {
            bottomDeadSpotAngle = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
            offset += 2;

        }

        int accumulatedEnergy = 0;
        if (accumulatedEnergyPresent) {
            accumulatedEnergy = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, offset);
            // offset += 2;

        }

        final StringBuilder builder = new StringBuilder();
        builder.append(String.format("Instantaneous Power: %s,\n", instantPower));
        if (pedalPowerBalancePresent) {
            builder.append(String.format("Pedal Power Balance: %d,\n", pedalPowerBalance));
        }
        if (accumulatedTorquePresent) {
            builder.append(String.format("Accumulated Torque: %d Nm,\n", accumulatedTorque));
        }
        if (wheelRevPresent) {
            builder.append(String.format("Wheel rev: %d,\n", wheelRevolutions));
            builder.append(String.format("Last wheel event time: %d ms,\n", lastWheelEventTime));
        }
        if (crankRevPresent) {
            builder.append(String.format("Crank rev: %d,\n", crankRevolutions));
            builder.append(String.format("Last crank event time: %d,\n", lastCrankEventTime));
        }
        if (extremeForceMagnitudePresent) {
            builder.append(String.format("Maximum Force Magnitude: %d N,\n", maxForceMagnitude));
            builder.append(String.format("Minimum Force Magnitude: %d N,\n", minForceMagnitude));
        }
        if (extremeTorqueMagnitudePresent) {
            builder.append(String.format("Maximum Torque Magnitude: %d Nm,\n", maxTorqueMagnitude));
            builder.append(String.format("Minimum Torque Magnitude: %d Nm,\n", minTorqueMagnitude));
        }
        if (extremeAnglePresent) {
            builder.append(String.format("Angle at maximum: %d °,\n", maxAngle));
            builder.append(String.format("Angle at minimum: %d °,\n", minAngle));
        }
        if (topDeadSpotAnglePresent) {
            builder.append(String.format("Top Dead Spot Angle: %d °,\n", topDeadSpotAngle));
        }
        if (bottomDeadSpotAnglePresent) {
            builder.append(String.format("Bottome Dead Spot Angle: %d °,\n", bottomDeadSpotAngle));
        }
        if (accumulatedEnergyPresent) {
            builder.append(String.format("Accumulated Energy: %d kJ,\n", accumulatedEnergy));
        }
        builder.setLength(builder.length() - 2);
        return builder.toString();
    }
}

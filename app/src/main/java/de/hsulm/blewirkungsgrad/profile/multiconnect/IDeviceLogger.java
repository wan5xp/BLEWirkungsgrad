package de.hsulm.blewirkungsgrad.profile.multiconnect;

import android.bluetooth.BluetoothDevice;
import android.support.annotation.StringRes;

/**
 * Created by wan5xp on 05.11.2017.
 */

public interface IDeviceLogger {
    /**
     * Logs the given message with given log level into the device's log session.
     *
     * @param device  the target device
     * @param level   the log level
     * @param message the message to be logged
     */
    void log(final BluetoothDevice device, final int level, final String message);

    /**
     * Logs the given message with given log level into the device's log session.
     *
     * @param device     the target device
     * @param level      the log level
     * @param messageRes string resource id
     * @param params     additional (optional) parameters used to fill the message
     */
    void log(final BluetoothDevice device, final int level, @StringRes final int messageRes, final Object... params);
}

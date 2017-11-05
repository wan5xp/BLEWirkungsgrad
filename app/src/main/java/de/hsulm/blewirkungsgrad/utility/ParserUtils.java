package de.hsulm.blewirkungsgrad.utility;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

/**
 * Created by wan5xp on 31.10.2017.
 */

public class ParserUtils {
    final private static char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();


    public static String parse(final BluetoothGattCharacteristic characteristic) {
        return parse(characteristic.getValue());
    }

    public static String parse(final BluetoothGattDescriptor descriptor) {
        return parse(descriptor.getValue());
    }

    public static String parse(final byte[] data) {
        if (data == null || data.length == 0)
            return "";

        final char[] out = new char[data.length * 3 - 1];
        for (int j = 0; j < data.length; j++) {
            int v = data[j] & 0xFF;
            out[j * 3] = HEX_ARRAY[v >>> 4];
            out[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
            if (j != data.length - 1)
                out[j * 3 + 2] = '-';
        }
        return "(0x) " + new String(out);
    }

    public static String parseDebug(final byte[] data) {
        if (data == null || data.length == 0)
            return "";

        final char[] out = new char[data.length * 2];
        for (int j = 0; j < data.length; j++) {
            int v = data[j] & 0xFF;
            out[j * 2] = HEX_ARRAY[v >>> 4];
            out[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return "0x" + new String(out);
    }
}

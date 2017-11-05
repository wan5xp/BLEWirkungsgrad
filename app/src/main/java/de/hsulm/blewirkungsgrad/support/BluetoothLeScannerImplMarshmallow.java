package de.hsulm.blewirkungsgrad.support;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.os.Build;
import android.support.annotation.NonNull;

/**
 * Created by wan5xp on 31.10.2017.
 */

@TargetApi(Build.VERSION_CODES.M)
/* package */ class BluetoothLeScannerImplMarshmallow extends BluetoothLeScannerImplLollipop {

    protected android.bluetooth.le.ScanSettings toImpl(@NonNull final BluetoothAdapter adapter, @NonNull final ScanSettings settings) {
        final android.bluetooth.le.ScanSettings.Builder builder = new android.bluetooth.le.ScanSettings.Builder().setScanMode(settings.getScanMode());

        if (adapter.isOffloadedScanBatchingSupported() && settings.getUseHardwareBatchingIfSupported())
            builder.setReportDelay(settings.getReportDelayMillis());

        if (settings.getUseHardwareCallbackTypesIfSupported())
            builder.setCallbackType(settings.getCallbackType())
                    .setMatchMode(settings.getMatchMode())
                    .setNumOfMatches(settings.getNumOfMatches());

        return builder.build();
    }
}

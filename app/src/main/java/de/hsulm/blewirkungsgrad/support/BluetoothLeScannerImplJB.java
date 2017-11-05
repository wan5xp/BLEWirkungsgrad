package de.hsulm.blewirkungsgrad.support;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.RequiresPermission;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by wan5xp on 31.10.2017.
 */

/* package */ class BluetoothLeScannerImplJB extends BluetoothLeScannerCompat implements BluetoothAdapter.LeScanCallback {
    private final BluetoothAdapter mBluetoothAdapter;
    private final Map<ScanCallback, ScanCallbackWrapper> mWrappers;
    private long mPowerSaveRestInterval;
    private long mPowerSaveScanInterval;
    private Handler mPowerSaveHandler;
    private Runnable mPowerSaveScanRunnable = new Runnable() {
        @SuppressWarnings("deprecation")
        @Override
        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH})
        public void run() {
            if (mBluetoothAdapter != null && mPowerSaveRestInterval > 0 && mPowerSaveScanInterval > 0) {
                mBluetoothAdapter.startLeScan(BluetoothLeScannerImplJB.this);

                if (mPowerSaveHandler != null) {
                    mPowerSaveHandler.postDelayed(mPowerSaveSleepRunnable, mPowerSaveScanInterval);
                }
            }
        }
    };
    private Runnable mPowerSaveSleepRunnable = new Runnable() {
        @SuppressWarnings("deprecation")
        @Override
        @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH})
        public void run() {
            if (mBluetoothAdapter != null && mPowerSaveRestInterval > 0 && mPowerSaveScanInterval > 0) {
                mBluetoothAdapter.stopLeScan(BluetoothLeScannerImplJB.this);

                if (mPowerSaveHandler != null) {
                    mPowerSaveHandler.postDelayed(mPowerSaveScanRunnable, mPowerSaveRestInterval);
                }
            }
        }
    };

    public BluetoothLeScannerImplJB() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mWrappers = new HashMap<>();
    }

    @Override
    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH})
    @SuppressWarnings("deprecation")
    /* package */ void startScanInternal(final List<ScanFilter> filters, final ScanSettings settings, final ScanCallback callback) {
        BluetoothLeUtils.checkAdapterStateOn(mBluetoothAdapter);

        if (mWrappers.containsKey(callback)) {
            throw new IllegalArgumentException("scanner already started with given callback");
        }

        boolean shouldStart;
        synchronized (mWrappers) {
            shouldStart = mWrappers.isEmpty();

            final ScanCallbackWrapper wrapper = new ScanCallbackWrapper(filters, settings, callback);
            mWrappers.put(callback, wrapper);
        }

        setPowerSaveSettings();

        if (shouldStart) {
            mBluetoothAdapter.startLeScan(this);
        }
    }

    private void setPowerSaveSettings() {
        long minRest = Long.MAX_VALUE, minScan = Long.MAX_VALUE;
        synchronized (mWrappers) {
            for (ScanCallbackWrapper wrapper : mWrappers.values()) {
                final ScanSettings settings = wrapper.getScanSettings();
                if (settings.hasPowerSaveMode()) {
                    if (minRest > settings.getPowerSaveRest()) {
                        minRest = settings.getPowerSaveRest();
                    }
                    if (minScan > settings.getPowerSaveScan()) {
                        minScan = settings.getPowerSaveScan();
                    }
                }
            }
        }
        if (minRest < Long.MAX_VALUE && minScan < Long.MAX_VALUE) {
            mPowerSaveRestInterval = minRest;
            mPowerSaveScanInterval = minScan;
            if (mPowerSaveHandler == null) {
                mPowerSaveHandler = new Handler();
            } else {
                mPowerSaveHandler.removeCallbacks(mPowerSaveScanRunnable);
                mPowerSaveHandler.removeCallbacks(mPowerSaveSleepRunnable);
            }
            mPowerSaveHandler.postDelayed(mPowerSaveSleepRunnable, mPowerSaveScanInterval);
        } else {
            mPowerSaveRestInterval = mPowerSaveScanInterval = 0;
            if (mPowerSaveHandler != null) {
                mPowerSaveHandler.removeCallbacks(mPowerSaveScanRunnable);
                mPowerSaveHandler.removeCallbacks(mPowerSaveSleepRunnable);
            }
        }
    }

    @Override
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    @SuppressWarnings("deprecation")
    public void stopScan(final ScanCallback callback) {
        synchronized (mWrappers) {
            final ScanCallbackWrapper wrapper = mWrappers.get(callback);
            if (wrapper == null)
                return;

            mWrappers.remove(callback);
            wrapper.close();
        }

        setPowerSaveSettings();

        if (mWrappers.isEmpty()) {
            mBluetoothAdapter.stopLeScan(this);
        }
    }

    @Override
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    public void flushPendingScanResults(final ScanCallback callback) {
        BluetoothLeUtils.checkAdapterStateOn(mBluetoothAdapter);
        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null!");
        }
        mWrappers.get(callback).flushPendingScanResults();
    }

    @Override
    public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        final ScanResult scanResult = new ScanResult(device, ScanRecord.parseFromBytes(scanRecord), rssi, SystemClock.elapsedRealtimeNanos());

        synchronized (mWrappers) {
            final Collection<ScanCallbackWrapper> wrappers = mWrappers.values();
            for (final ScanCallbackWrapper wrapper : wrappers) {
                wrapper.handleScanResult(scanResult);
            }
        }
    }
}

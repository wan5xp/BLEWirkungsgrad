package de.hsulm.blewirkungsgrad.profile.multiconnect;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import de.hsulm.blewirkungsgrad.R;
import de.hsulm.blewirkungsgrad.log.ILogSession;
import de.hsulm.blewirkungsgrad.log.LocalLogSession;
import de.hsulm.blewirkungsgrad.log.LogContract;
import de.hsulm.blewirkungsgrad.log.Logger;
import de.hsulm.blewirkungsgrad.profile.BleManagerCallbacks;
import de.hsulm.blewirkungsgrad.scanner.ScannerFragment;
import de.hsulm.blewirkungsgrad.utility.DebugLogger;

/**
 * Created by wan5xp on 05.11.2017.
 */

public abstract class BleMulticonnectProfileServiceReadyActivity<E extends BleMulticonnectProfileService.LocalBinder>
        extends AppCompatActivity
        implements ScannerFragment.OnDeviceSelectedListener, BleManagerCallbacks {

    protected static final int REQUEST_ENABLE_BT = 2;
    private static final String TAG = "BleMulticonnectProfileServiceReadyActivity";
    private final BroadcastReceiver mCommonBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice bluetoothDevice =
                    intent.getParcelableExtra(BleMulticonnectProfileService.EXTRA_DEVICE);
            final String action = intent.getAction();
            switch (action) {
                case BleMulticonnectProfileService.BROADCAST_CONNECTION_STATE: {
                    final int state = intent.getIntExtra(BleMulticonnectProfileService.EXTRA_CONNECTION_STATE, BleMulticonnectProfileService.STATE_DISCONNECTED);

                    switch (state) {
                        case BleMulticonnectProfileService.STATE_CONNECTED: {
                            onDeviceConnected(bluetoothDevice);
                            break;
                        }
                        case BleMulticonnectProfileService.STATE_DISCONNECTED: {
                            onDeviceDisconnected(bluetoothDevice);
                            break;
                        }
                        case BleMulticonnectProfileService.STATE_LINK_LOSS: {
                            onLinklossOccur(bluetoothDevice);
                            break;
                        }
                        case BleMulticonnectProfileService.STATE_CONNECTING: {
                            onDeviceConnecting(bluetoothDevice);
                            break;
                        }
                        case BleMulticonnectProfileService.STATE_DISCONNECTING: {
                            onDeviceDisconnecting(bluetoothDevice);
                            break;
                        }
                        default:
                            // there should be no other actions
                            break;
                    }
                    break;
                }
                case BleMulticonnectProfileService.BROADCAST_SERVICES_DISCOVERED: {
                    final boolean primaryService = intent.getBooleanExtra(
                            BleMulticonnectProfileService.EXTRA_SERVICE_PRIMARY, false);
                    final boolean secondaryService = intent.getBooleanExtra(
                            BleMulticonnectProfileService.EXTRA_SERVICE_SECONDARY, false);

                    if (primaryService) {
                        onServicesDiscovered(bluetoothDevice, secondaryService);
                    } else {
                        onDeviceNotSupported(bluetoothDevice);
                    }
                    break;
                }
                case BleMulticonnectProfileService.BROADCAST_DEVICE_READY: {
                    onDeviceReady(bluetoothDevice);
                    break;
                }
                case BleMulticonnectProfileService.BROADCAST_BOND_STATE: {
                    final int state = intent.getIntExtra(
                            BleMulticonnectProfileService.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                    switch (state) {
                        case BluetoothDevice.BOND_BONDING: {
                            onBondingRequired(bluetoothDevice);
                            break;
                        }
                        case BluetoothDevice.BOND_BONDED: {
                            onBonded(bluetoothDevice);
                            break;
                        }
                    }
                    break;
                }
                case BleMulticonnectProfileService.BROADCAST_BATTERY_LEVEL: {
                    final int value = intent.getIntExtra(
                            BleMulticonnectProfileService.EXTRA_BATTERY_LEVEL, -1);
                    if (value > 0)
                        onBatteryValueReceived(bluetoothDevice, value);
                    break;
                }
                case BleMulticonnectProfileService.BROADCAST_ERROR: {
                    final String message = intent.getStringExtra(BleMulticonnectProfileService.EXTRA_ERROR_MESSAGE);
                    final int errorCode = intent.getIntExtra(BleMulticonnectProfileService.EXTRA_ERROR_CODE, 0);
                    onError(bluetoothDevice, message, errorCode);
                    break;
                }
            }

        }
    };
    private E mService;
    private List<BluetoothDevice> mManagedDevices;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            final E bleService = mService = (E) service;
            bleService.log(LogContract.Log.Level.DEBUG, "Activity bound to the service");
            mManagedDevices.addAll(bleService.getManagedDevices());
            onServiceBinded(bleService);

            for (final BluetoothDevice device : mManagedDevices) {
                if (bleService.isConnected(device))
                    onDeviceConnected(device);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            onServiceUnbinded();
        }
    };

    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleMulticonnectProfileService.BROADCAST_CONNECTION_STATE);
        intentFilter.addAction(BleMulticonnectProfileService.BROADCAST_SERVICES_DISCOVERED);
        intentFilter.addAction(BleMulticonnectProfileService.BROADCAST_DEVICE_READY);
        intentFilter.addAction(BleMulticonnectProfileService.BROADCAST_BOND_STATE);
        intentFilter.addAction(BleMulticonnectProfileService.BROADCAST_BATTERY_LEVEL);
        intentFilter.addAction(BleMulticonnectProfileService.BROADCAST_ERROR);
        return intentFilter;
    }

    @Override
    protected final void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mManagedDevices = new ArrayList<>();

        ensureBLESupported();
        if (!isBLEEnabled()) {
            showBLEDialog();
        }

        onInitialise(savedInstanceState);

        onCreateView(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final Intent service = new Intent(this, getServiceClass());
        startService(service);
        bindService(service, mServiceConnection, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mService != null) {
            mService.setActivityIsChangingConfiguration(isChangingConfigurations());
            mService.log(LogContract.Log.Level.DEBUG, "Activity unbound from service");
        }

        unbindService(mServiceConnection);
        mService = null;

        onServiceUnbinded();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mCommonBroadcastReceiver);
    }

    protected abstract void onServiceBinded(E binder);

    protected abstract void onServiceUnbinded();

    protected abstract Class<? extends BleMulticonnectProfileService> getServiceClass();

    protected E getService() {
        return mService;
    }

    protected void onInitialise(final Bundle savedInstanceState) {

    }

    protected abstract void onCreateView(final Bundle savedinstanceState);

    protected void onViewCreated(final Bundle savedInstanceState) {

    }

    protected final void setUpView() {

    }

    public void onAddDeviceClicked(final View view) {
        if (isBLEEnabled()) {
            showDeviceScanningDialog(getFilterUUID());
        } else {
            showBLEDialog();
        }
    }

    protected int getLoggerProfileTitle() {
        return 0;
    }

    protected Uri getLocalAuthorityLogger() {
        return null;
    }

    @Override
    public void onDeviceSelected(BluetoothDevice device, String name) {
        final int titleId = getLoggerProfileTitle();
        ILogSession logSession = null;
        if (titleId > 0) {
            logSession = Logger.newSession(
                    getApplicationContext(), getString(titleId), device.getAddress(), name);
            if (logSession == null && getLocalAuthorityLogger() != null) {
                logSession = LocalLogSession.newSession(
                        getApplicationContext(), getLocalAuthorityLogger(), device.getAddress(), name);
            }
        }
        mService.connect(device, logSession);
    }

    @Override
    public void onDialogCanceled() {

    }

    @Override
    public void onDeviceConnecting(BluetoothDevice device) {

    }

    @Override
    public void onDeviceDisconnecting(BluetoothDevice device) {

    }

    @Override
    public void onLinklossOccur(BluetoothDevice device) {

    }

    @Override
    public void onServicesDiscovered(BluetoothDevice device, boolean optionalServicesFound) {

    }

    @Override
    public void onDeviceReady(BluetoothDevice device) {

    }

    @Override
    public void onBondingRequired(BluetoothDevice device) {

    }

    @Override
    public void onBonded(BluetoothDevice device) {

    }

    @Override
    public void onDeviceNotSupported(BluetoothDevice device) {
        showToast(R.string.not_supported);
    }

    @Override
    public boolean shouldEnableBatteryLevelNotifications(BluetoothDevice device) {
        throw new UnsupportedOperationException("This method should not be called");
    }

    @Override
    public void onBatteryValueReceived(BluetoothDevice device, int value) {

    }

    @Override
    public void onError(BluetoothDevice device, String message, int errorCode) {
        DebugLogger.e(TAG, "Error occured: " + message + ", error code: " + errorCode);
        showToast(message + " (" + errorCode + ")");
    }

    protected void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(BleMulticonnectProfileServiceReadyActivity.this, message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    protected void showToast(final int messageResId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(BleMulticonnectProfileServiceReadyActivity.this, messageResId,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    protected abstract int getAboutTextID();

    protected abstract UUID getFilterUUID();

    protected List<BluetoothDevice> getManagedDevices() {
        return Collections.unmodifiableList(mManagedDevices);
    }

    protected boolean isDeviceConnected(final BluetoothDevice device) {
        return mService != null && mService.isConnected(device);
    }

    private void showDeviceScanningDialog(final UUID filter) {
        final ScannerFragment dialog = ScannerFragment.getInstance(filter);
        dialog.show(getSupportFragmentManager(), "scan_fragment");
    }

    private void ensureBLESupported() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.no_ble, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    protected boolean isBLEEnabled() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = bluetoothManager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    protected void showBLEDialog() {
        final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    }
}
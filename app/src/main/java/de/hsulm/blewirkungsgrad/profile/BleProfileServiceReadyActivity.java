package de.hsulm.blewirkungsgrad.profile;

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
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;

import de.hsulm.blewirkungsgrad.R;
import de.hsulm.blewirkungsgrad.log.ILogSession;
import de.hsulm.blewirkungsgrad.log.LocalLogSession;
import de.hsulm.blewirkungsgrad.log.Logger;
import de.hsulm.blewirkungsgrad.scanner.ScannerFragment;
import de.hsulm.blewirkungsgrad.utility.DebugLogger;

/**
 * Created by wan5xp on 02.11.2017.
 */

public abstract class BleProfileServiceReadyActivity<E extends BleProfileService.LocalBinder>
        extends AppCompatActivity
        implements ScannerFragment.OnDeviceSelectedListener, BleManagerCallbacks {

    protected static final int REQUEST_ENABLE_BT = 2;
    private static final String TAG = "BleProfileServiceReadyActivity";
    private static final String SIS_DEVICE_NAME = "device_name";
    private static final String SIS_DEVICE = "device";
    private static final String LOG_URI = "log_uri";
    private E mService;

    private TextView mDeviceNameView;
    private TextView mBatteryLevelView;
    private Button mConnectButton;

    private ILogSession mLogSession;
    private BluetoothDevice mBluetoothDevice;
    private String mDeviceName;

    private final BroadcastReceiver mCommonBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isBroadcastForThisDevice(intent))
                return;

            final BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BleProfileService.EXTRA_DEVICE);
            final String action = intent.getAction();
            switch (action) {
                case BleProfileService.BROADCAST_CONNECTION_STATE: {
                    final int state = intent.getIntExtra(BleProfileService.EXTRA_CONNECTION_STATE, BleProfileService.STATE_DISCONNECTED);

                    switch (state) {
                        case BleProfileService.STATE_CONNECTED: {
                            mDeviceName = intent.getStringExtra(BleProfileService.EXTRA_DEVICE_NAME);
                            onDeviceConnected(bluetoothDevice);
                            break;
                        }
                        case BleProfileService.STATE_DISCONNECTED: {
                            onDeviceDisconnected(bluetoothDevice);
                            mDeviceName = null;
                            break;
                        }
                        case BleProfileService.STATE_LINK_LOSS: {
                            onLinklossOccur(bluetoothDevice);
                            break;
                        }
                        case BleProfileService.STATE_CONNECTING: {
                            onDeviceConnecting(bluetoothDevice);
                            break;
                        }
                        case BleProfileService.STATE_DISCONNECTING: {
                            onDeviceDisconnecting(bluetoothDevice);
                            break;
                        }
                        default:
                            break;
                    }
                    break;
                }
                case BleProfileService.BROADCAST_SERVICES_DISCOVERED: {
                    final boolean primaryService = intent.getBooleanExtra(BleProfileService.EXTRA_SERVICE_PRIMARY, false);
                    final boolean secondaryService = intent.getBooleanExtra(BleProfileService.EXTRA_SERVICE_SECONDARY, false);

                    if (primaryService) {
                        onServicesDiscovered(bluetoothDevice, secondaryService);
                    } else {
                        onDeviceNotSupported(bluetoothDevice);
                    }
                    break;
                }
                case BleProfileService.BROADCAST_DEVICE_READY: {
                    onDeviceReady(bluetoothDevice);
                    break;
                }
                case BleProfileService.BROADCAST_BOND_STATE: {
                    final int state = intent.getIntExtra(BleProfileService.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                    switch (state) {
                        case BluetoothDevice.BOND_BONDING:
                            onBondingRequired(bluetoothDevice);
                            break;
                        case BluetoothDevice.BOND_BONDED:
                            onBonded(bluetoothDevice);
                            break;
                    }
                    break;
                }
                case BleProfileService.BROADCAST_BATTERY_LEVEL: {
                    final int value = intent.getIntExtra(BleProfileService.EXTRA_BATTERY_LEVEL, -1);
                    if (value > 0)
                        onBatteryValueReceived(bluetoothDevice, value);
                    break;
                }
                case BleProfileService.BROADCAST_ERROR: {
                    final String message = intent.getStringExtra(BleProfileService.EXTRA_ERROR_MESSAGE);
                    final int errorCode = intent.getIntExtra(BleProfileService.EXTRA_ERROR_CODE, 0);
                    onError(bluetoothDevice, message, errorCode);
                    break;
                }
            }
        }
    };

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName componentName, final IBinder service) {
            final E bleService = mService = (E) service;
            mBluetoothDevice = bleService.getBluetoothDevice();
            mLogSession = mService.getLogSession();
            Logger.d(mLogSession, "Activity bound to the service");
            onServiceBinded(bleService);

            mDeviceName = bleService.getDeviceName();
            mDeviceNameView.setText(mDeviceName);
            mConnectButton.setText(R.string.action_disconnect);

            if (bleService.isConnected()) {
                onDeviceConnected(mBluetoothDevice);
            } else {
                onDeviceConnecting(mBluetoothDevice);
            }

        }

        @Override
        public void onServiceDisconnected(final ComponentName componentName) {
            Logger.d(mLogSession, "Activity disconnected from the service");
            mDeviceNameView.setText(getDefaultDeviceName());
            mConnectButton.setText(R.string.action_connect);

            mService = null;
            mDeviceName = null;
            mBluetoothDevice = null;
            mLogSession = null;
            onServiceUnbinded();
        }
    };

    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleProfileService.BROADCAST_CONNECTION_STATE);
        intentFilter.addAction(BleProfileService.BROADCAST_SERVICES_DISCOVERED);
        intentFilter.addAction(BleProfileService.BROADCAST_DEVICE_READY);
        intentFilter.addAction(BleProfileService.BROADCAST_BOND_STATE);
        intentFilter.addAction(BleProfileService.BROADCAST_BATTERY_LEVEL);
        intentFilter.addAction(BleProfileService.BROADCAST_ERROR);
        return intentFilter;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ensureBLESupported();
        if (!isBLEEnabled()) {
            showBLEDialog();
        }
        if (savedInstanceState != null) {
            final Uri logUri = savedInstanceState.getParcelable(LOG_URI);
            mLogSession = Logger.openSession(getApplicationContext(), logUri);
        }

        onInitialise(savedInstanceState);

        onCreateView(savedInstanceState);

        setUpView();

        onViewCreated(savedInstanceState);

        LocalBroadcastManager.getInstance(this).registerReceiver(mCommonBroadcastReceiver, makeIntentFilter());
    }

    @Override
    protected void onResume() {
        super.onResume();

        final Intent service = new Intent(this, getServiceClass());
        bindService(service, mServiceConnection, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (mService != null)
                mService.setActivityIsChangingConfiguration(isChangingConfigurations());
            unbindService(mServiceConnection);
            mService = null;

            Logger.d(mLogSession, "Activity unbound from the service");
            onServiceUnbinded();
            mDeviceName = null;
            mBluetoothDevice = null;
            mLogSession = null;

        } catch (final IllegalArgumentException e) {

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mCommonBroadcastReceiver);
    }

    protected abstract void onServiceBinded(E binder);

    protected abstract void onServiceUnbinded();

    protected abstract Class<? extends BleProfileService> getServiceClass();

    protected void onInitialise(final Bundle savedInstanceState) {
    }

    protected abstract void onCreateView(final Bundle savedInstanceState);

    protected void onViewCreated(final Bundle savedInstanceState) {

    }

    protected final void setUpView() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        mConnectButton = (Button) findViewById(R.id.buttonAddDevice);
        mDeviceNameView = (TextView) findViewById(R.id.textLeft);
        mBatteryLevelView = (TextView) findViewById(R.id.battery);
    }

    protected E getService() {
        return mService;
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SIS_DEVICE_NAME, mDeviceName);
        outState.putParcelable(SIS_DEVICE, mBluetoothDevice);
        if (mLogSession != null)
            outState.putParcelable(LOG_URI, mLogSession.getSessionContentUri());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mDeviceName = savedInstanceState.getString(SIS_DEVICE_NAME);
        mBluetoothDevice = savedInstanceState.getParcelable(SIS_DEVICE);
    }


    public void onConnectClick(View view) {
        if (isBLEEnabled()) {
            if (mService == null) {
                setDefaultUI();
                showDeviceScanningDialog(getFilterUUID());
            } else {
                mService.disconnect();
            }
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
    public void onDeviceSelected(final BluetoothDevice device, final String name) {
        final int titleId = getLoggerProfileTitle();
        if (titleId > 0) {
            mLogSession = Logger.newSession(getApplicationContext(), getString(titleId), device.getAddress(), name);

            if (mLogSession == null && getLocalAuthorityLogger() != null) {
                mLogSession = LocalLogSession.newSession(getApplicationContext(), getLocalAuthorityLogger(), device.getAddress(), name);
            }
            mBluetoothDevice = device;
            mDeviceName = name;
            mDeviceNameView.setText(name != null ? name : getString(R.string.not_available));
            mConnectButton.setText(R.string.action_connecting);

            Logger.d(mLogSession, "Creating service...");
            final Intent service = new Intent(this, getServiceClass());
            service.putExtra(BleProfileService.EXTRA_DEVICE_ADDRESS, device.getAddress());
            service.putExtra(BleProfileService.EXTRA_DEVICE_NAME, name);
            if (mLogSession != null)
                service.putExtra(BleProfileService.EXTRA_LOG_URI, mLogSession.getSessionUri());
            startService(service);
            Logger.d(mLogSession, "Binding to the service...");
            bindService(service, mServiceConnection, 0);
        }
    }

    @Override
    public void onDialogCanceled() {

    }

    @Override
    public void onDeviceConnecting(final BluetoothDevice device) {

    }

    @Override
    public void onDeviceConnected(final BluetoothDevice device) {
        mDeviceNameView.setText(mDeviceName);
        mConnectButton.setText(R.string.action_disconnect);
    }

    @Override
    public void onDeviceDisconnecting(final BluetoothDevice device) {

    }

    @Override
    public void onDeviceDisconnected(final BluetoothDevice device) {
        mConnectButton.setText(R.string.action_connect);
        mDeviceNameView.setText(getDefaultDeviceName());
        if (mBatteryLevelView != null)
            mBatteryLevelView.setText(R.string.not_available);

        try {
            Logger.d(mLogSession, "Activity unbound from the service");
            onServiceUnbinded();
            mDeviceName = null;
            mBluetoothDevice = null;
            mLogSession = null;
        } catch (final IllegalArgumentException e) {

        }
    }

    @Override
    public void onLinklossOccur(final BluetoothDevice device) {
        if (mBatteryLevelView != null)
            mBatteryLevelView.setText(R.string.not_available);
    }

    @Override
    public void onServicesDiscovered(final BluetoothDevice device, final boolean optionalServicesFound) {

    }

    @Override
    public void onDeviceReady(final BluetoothDevice device) {

    }

    @Override
    public void onBondingRequired(final BluetoothDevice device) {
        // empty default implementation
    }

    @Override
    public void onBonded(final BluetoothDevice device) {
        // empty default implementation
    }

    @Override
    public final boolean shouldEnableBatteryLevelNotifications(final BluetoothDevice device) {
        // This method will never be called.
        // Please see BleProfileService#shouldEnableBatteryLevelNotifications(BluetoothDevice) instead.
        throw new UnsupportedOperationException("This method should not be called");
    }

    @Override
    public void onBatteryValueReceived(final BluetoothDevice device, final int value) {
        if (mBatteryLevelView != null)
            mBatteryLevelView.setText(getString(R.string.battery, value));
    }

    @Override
    public void onError(final BluetoothDevice device, final String message, final int errorCode) {
        DebugLogger.e(TAG, "Error occurred: " + message + ",  error code: " + errorCode);
        showToast(message + " (" + errorCode + ")");
    }

    @Override
    public void onDeviceNotSupported(final BluetoothDevice device) {
        showToast(R.string.not_supported);
    }

    protected void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(BleProfileServiceReadyActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    protected void showToast(final int messageResId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(BleProfileServiceReadyActivity.this, messageResId, Toast.LENGTH_SHORT).show();
            }
        });
    }

    protected boolean isDeviceConnected() {
        return mService != null && mService.isConnected();
    }

    protected String getDeviceName() {
        return mDeviceName;
    }

    protected abstract void setDefaultUI();

    protected abstract int getDefaultDeviceName();

    protected abstract UUID getFilterUUID();

    protected boolean isBroadcastForThisDevice(final Intent intent) {
        final BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BleProfileService.EXTRA_DEVICE);
        return mBluetoothDevice != null && mBluetoothDevice.equals(bluetoothDevice);
    }

    private void showDeviceScanningDialog(final UUID filter) {
        final ScannerFragment dialog = ScannerFragment.getInstance(filter);
        dialog.show(getSupportFragmentManager(), "scan_fragment");
    }

    protected ILogSession getLogSession() {
        return mLogSession;
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

package de.hsulm.blewirkungsgrad.profile.multiconnect;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.StringRes;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import de.hsulm.blewirkungsgrad.R;
import de.hsulm.blewirkungsgrad.log.ILogSession;
import de.hsulm.blewirkungsgrad.log.LogContract;
import de.hsulm.blewirkungsgrad.profile.BleManager;
import de.hsulm.blewirkungsgrad.profile.BleManagerCallbacks;
import de.hsulm.blewirkungsgrad.profile.ILogger;

/**
 * Created by wan5xp on 05.11.2017.
 */

public abstract class BleMulticonnectProfileService extends Service implements BleManagerCallbacks {
    public static final String BROADCAST_CONNECTION_STATE = "de.hsulm.blewirkungsgrad.BROADCAST_CONNECTION_STATE";
    public static final String BROADCAST_SERVICES_DISCOVERED = "de.hsulm.blewirkungsgrad.BROADCAST_SERVICES_DISCOVERED";
    public static final String BROADCAST_DEVICE_READY = "de.hsulm.blewirkungsgrad.DEVICE_READY";
    public static final String BROADCAST_BOND_STATE = "de.hsulm.blewirkungsgrad.BROADCAST_BOND_STATE";
    public static final String BROADCAST_BATTERY_LEVEL = "de.hsulm.blewirkungsgrad.BROADCAST_BATTERY_LEVEL";
    public static final String BROADCAST_ERROR = "de.hsulm.blewirkungsgrad.BROADCAST_ERROR";
    public static final String EXTRA_DEVICE = "de.hsulm.blewirkungsgrad.EXTRA_DEVICE";
    public static final String EXTRA_CONNECTION_STATE = "de.hsulm.blewirkungsgrad.EXTRA_CONNECTION_STATE";
    public static final String EXTRA_BOND_STATE = "de.hsulm.blewirkungsgrad.EXTRA_BOND_STATE";
    public static final String EXTRA_SERVICE_PRIMARY = "de.hsulm.blewirkungsgrad.EXTRA_SERVICE_PRIMARY";
    public static final String EXTRA_SERVICE_SECONDARY = "de.hsulm.blewirkungsgrad.EXTRA_SERVICE_SECONDARY";
    public static final String EXTRA_BATTERY_LEVEL = "de.hsulm.blewirkungsgrad.EXTRA_BATTERY_LEVEL";
    public static final String EXTRA_ERROR_MESSAGE = "de.hsulm.blewirkungsgrad.EXTRA_ERROR_MESSAGE";
    public static final String EXTRA_ERROR_CODE = "de.hsulm.blewirkungsgrad.EXTRA_ERROR_CODE";
    public static final int STATE_LINK_LOSS = -1;
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_DISCONNECTING = 3;
    private static final String TAG = "BleMultiProfileService";
    protected boolean mBinded;
    private HashMap<BluetoothDevice, BleManager<BleManagerCallbacks>> mBleManagers;
    private List<BluetoothDevice> mManagedDevices;
    private Handler mHandler;
    private final BroadcastReceiver mBluetoothStateBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.STATE_OFF);
            final int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE,
                    BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onBluetoothEnabled();
                        }
                    }, 600);
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                case BluetoothAdapter.STATE_OFF:
                    if (previousState != BluetoothAdapter.STATE_TURNING_OFF &&
                            previousState != BluetoothAdapter.STATE_OFF)
                        onBluetoothDisabled();
                    break;

            }
        }
    };
    private boolean mActivityIsChangingConfiguration;

    // Returns a handler that is created in onCreate().
    // The handler may be used to postpone execution of some operations or to run them in UI thread
    protected Handler getmHandler() {
        return mHandler;
    }

    // Returns the binder implementation
    protected LocalBinder getBinder() {
        return new LocalBinder();
    }

    @Override
    public IBinder onBind(final Intent intent) {
        mBinded = true;
        return getBinder();
    }

    @Override
    public final void onRebind(final Intent intent) {
        mBinded = true;

        if (!mActivityIsChangingConfiguration) {
            onRebind();
            for (final BleManager<BleManagerCallbacks> manager : mBleManagers.values()) {
                if (manager.isConnected())
                    manager.readBatteryLevel();
            }
        }
    }

    protected void onRebind() {

    }

    @Override
    public final boolean onUnbind(final Intent intent) {
        mBinded = false;

        if (!mActivityIsChangingConfiguration) {
            if (!mManagedDevices.isEmpty()) {
                onUnbind();
                for (final BleManager<BleManagerCallbacks> manager : mBleManagers.values()) {
                    if (manager.isConnected())
                        manager.setBatteryNotifications(false);
                }
            } else {
                stopSelf();
            }
        }

        return true;
    }

    protected void onUnbind() {

    }

    @Override
    public void onCreate() {
        super.onCreate();

        mHandler = new Handler();

        mBleManagers = new HashMap<>();
        mManagedDevices = new ArrayList<>();

        registerReceiver(mBluetoothStateBroadcastReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        onServiceCreated();

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter.isEnabled())
            onBluetoothEnabled();

    }

    protected void onServiceCreated() {

    }

    @SuppressWarnings("rawtypes")
    protected abstract BleManager initializeManager();

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startid) {
        onServiceStarted();

        return START_NOT_STICKY;
    }

    protected void onServiceStarted() {

    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        onServiceStopped();
        mHandler = null;
    }

    protected void onServiceStopped() {
        unregisterReceiver(mBluetoothStateBroadcastReceiver);

        for (final BleManager<BleManagerCallbacks> manager : mBleManagers.values()) {
            manager.close();
            manager.log(LogContract.Log.Level.INFO, "Service destroyed");
        }
        mBleManagers.clear();
        mManagedDevices.clear();
        mBleManagers = null;
        mManagedDevices = null;
    }

    protected void onBluetoothDisabled() {

    }

    protected void onBluetoothEnabled() {
        for (final BluetoothDevice device : mManagedDevices) {
            final BleManager<BleManagerCallbacks> manager = mBleManagers.get(device);
            if (!manager.isConnected())
                manager.connect(device);
        }
    }

    @Override
    public boolean shouldEnableBatteryLevelNotifications(BluetoothDevice device) {
        return mBinded;
    }

    @Override
    public void onDeviceConnecting(BluetoothDevice device) {
        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_CONNECTING);
        LocalBroadcastManager.getInstance(BleMulticonnectProfileService.this).sendBroadcast(broadcast);
    }

    @Override
    public void onDeviceConnected(BluetoothDevice device) {
        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_CONNECTED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onDeviceDisconnecting(BluetoothDevice device) {
        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_DISCONNECTING);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onDeviceDisconnected(BluetoothDevice device) {
        mManagedDevices.remove(device);

        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_DISCONNECTED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        if (!mBinded && mManagedDevices.isEmpty()) {
            stopSelf();
        }
    }

    @Override
    public void onLinklossOccur(BluetoothDevice device) {
        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_LINK_LOSS);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onServicesDiscovered(final BluetoothDevice device, final boolean optionalServicesFound) {
        final Intent broadcast = new Intent(BROADCAST_SERVICES_DISCOVERED);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_SERVICE_PRIMARY, true);
        broadcast.putExtra(EXTRA_SERVICE_SECONDARY, optionalServicesFound);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onDeviceReady(BluetoothDevice device) {
        final Intent broadcast = new Intent(BROADCAST_DEVICE_READY);
        broadcast.putExtra(EXTRA_DEVICE, device);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onDeviceNotSupported(BluetoothDevice device) {
        mManagedDevices.remove(device);
        mBleManagers.remove(device);

        final Intent broadcast = new Intent(BROADCAST_SERVICES_DISCOVERED);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_SERVICE_PRIMARY, false);
        broadcast.putExtra(EXTRA_SERVICE_SECONDARY, false);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onBatteryValueReceived(BluetoothDevice device, int value) {
        final Intent broadcast = new Intent(BROADCAST_BATTERY_LEVEL);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_BATTERY_LEVEL, value);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onBondingRequired(BluetoothDevice device) {
        showToast(R.string.bonding);

        final Intent broadcast = new Intent(BROADCAST_BOND_STATE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDING);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onBonded(BluetoothDevice device) {
        showToast(R.string.bonded);

        final Intent broadcast = new Intent(BROADCAST_BOND_STATE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    @Override
    public void onError(BluetoothDevice device, String message, int errorCode) {
        final Intent broadcast = new Intent(BROADCAST_ERROR);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_ERROR_MESSAGE, message);
        broadcast.putExtra(EXTRA_ERROR_CODE, errorCode);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    protected void showToast(final int messageResId) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(BleMulticonnectProfileService.this,
                        messageResId, Toast.LENGTH_SHORT).show();
            }
        });
    }

    protected void showToast(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(BleMulticonnectProfileService.this,
                        message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    protected BleManager<? extends BleManagerCallbacks> getBleManager(final BluetoothDevice device) {
        return mBleManagers.get(device);
    }

    protected List<BluetoothDevice> getManagedDevices() {
        return Collections.unmodifiableList(mManagedDevices);
    }

    protected List<BluetoothDevice> getConnectedDevices() {
        final List<BluetoothDevice> list = new ArrayList<>();
        for (BluetoothDevice device : mManagedDevices) {
            if (mBleManagers.get(device).isConnected())
                list.add(device);
        }
        return Collections.unmodifiableList(list);
    }

    protected boolean isConnected(final BluetoothDevice device) {
        final BleManager<BleManagerCallbacks> manager = mBleManagers.get(device);
        return manager != null && manager.isConnected();
    }

    public class LocalBinder extends Binder implements ILogger, IDeviceLogger {
        /**
         * Returns an unmodifiable list of devices managed by the service.
         * The returned devices do not need to be connected at tha moment. Each of them was however created
         * using {@link #connect(BluetoothDevice)} method so they might have been connected before and disconnected.
         *
         * @return unmodifiable list of devices managed by the service
         */
        public final List<BluetoothDevice> getManagedDevices() {
            return Collections.unmodifiableList(mManagedDevices);
        }

        /**
         * Connects to the given device. If the device is already connected this method does nothing.
         *
         * @param device target Bluetooth device
         */
        public void connect(final BluetoothDevice device) {
            connect(device, null);
        }

        /**
         * Adds the given device to managed and stars connecting to it. If the device is already connected this method does nothing.
         *
         * @param device  target Bluetooth device
         * @param session log session that has to be used by the device
         */
        @SuppressWarnings("unchecked")
        public void connect(final BluetoothDevice device, final ILogSession session) {
            // If a device is in managed devices it means that it's already connected, or was connected
            // using autoConnect and the link was lost but Android is already trying to connect to it.
            if (mManagedDevices.contains(device))
                return;
            mManagedDevices.add(device);

            BleManager<BleManagerCallbacks> manager = mBleManagers.get(device);
            if (manager != null) {
                if (session != null)
                    manager.setLogger(session);
                manager.connect(device);
            } else {
                mBleManagers.put(device, manager = initializeManager());
                manager.setGattCallbacks(BleMulticonnectProfileService.this);
                manager.setLogger(session);
                manager.connect(device);
            }
        }

        /**
         * Disconnects the given device and removes the associated BleManager object.
         * If the list of BleManagers is empty while the last activity unbinds from the service,
         * the service will stop itself.
         *
         * @param device target device to disconnect and forget
         */
        public void disconnect(final BluetoothDevice device) {
            final BleManager<BleManagerCallbacks> manager = mBleManagers.get(device);
            if (manager != null && manager.isConnected()) {
                manager.disconnect();
            }
            mManagedDevices.remove(device);
        }

        /**
         * Returns <code>true</code> if the device is connected to the sensor.
         *
         * @param device the target device
         * @return <code>true</code> if device is connected to the sensor, <code>false</code> otherwise
         */
        public final boolean isConnected(final BluetoothDevice device) {
            final BleManager<BleManagerCallbacks> manager = mBleManagers.get(device);
            return manager != null && manager.isConnected();
        }

        /**
         * Returns the connection state of given device.
         *
         * @param device the target device
         * @return the connection state, as in {@link BleManager#getConnectionState()}.
         */
        public final int getConnectionState(final BluetoothDevice device) {
            final BleManager<BleManagerCallbacks> manager = mBleManagers.get(device);
            return manager != null ? manager.getConnectionState() : BluetoothGatt.STATE_DISCONNECTED;
        }

        /**
         * Returns the last received battery level value.
         *
         * @param device the device of which battery level should be returned
         * @return battery value or -1 if no value was received or Battery Level characteristic was not found
         */
        public int getBatteryValue(final BluetoothDevice device) {
            final BleManager<BleManagerCallbacks> manager = mBleManagers.get(device);
            return manager.getBatteryValue();
        }

        /**
         * Sets whether the bound activity if changing configuration or not.
         * If <code>false</code>, we will turn off battery level notifications in onUnbind(..) method below.
         *
         * @param changing true if the bound activity is finishing
         */
        public final void setActivityIsChangingConfiguration(final boolean changing) {
            mActivityIsChangingConfiguration = changing;
        }

        @Override
        public void log(final BluetoothDevice device, final int level, final String message) {
            final BleManager<BleManagerCallbacks> manager = mBleManagers.get(device);
            if (manager != null)
                manager.log(level, message);
        }

        @Override
        public void log(final BluetoothDevice device, final int level, @StringRes final int messageRes, final Object... params) {
            final BleManager<BleManagerCallbacks> manager = mBleManagers.get(device);
            if (manager != null)
                manager.log(level, messageRes, params);
        }

        @Override
        public void log(final int level, final String message) {
            for (final BleManager<BleManagerCallbacks> manager : mBleManagers.values())
                manager.log(level, message);
        }

        @Override
        public void log(final int level, @StringRes final int messageRes, final Object... params) {
            for (final BleManager<BleManagerCallbacks> manager : mBleManagers.values())
                manager.log(level, messageRes, params);
        }
    }
}

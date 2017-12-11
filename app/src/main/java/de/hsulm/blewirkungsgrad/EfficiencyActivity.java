package de.hsulm.blewirkungsgrad;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;

public class EfficiencyActivity extends AppCompatActivity {
    public final static UUID UUID_CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_ELECTRICAL_POWER_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_ELECTRICAL_POWER_MEASUREMENT = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    // Cycling Power Service
    // Service UUID
    public final static UUID UUID_CYCLE_POWER_SERVICE = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb");
    // Mandatory Characteristic UUID
    public final static UUID UUID_CYCLE_POWER_MEASUREMENT = UUID.fromString("00002A63-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_CYCLE_POWER_FEATURE = UUID.fromString("00002A65-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_SENSOR_LOCATION = UUID.fromString("00002A5D-0000-1000-8000-00805f9b34fb");
    // Optional Characteristic UUID
    public final static UUID UUID_CYCLE_POWER_VECTOR = UUID.fromString("00002A64-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_CYCLE_POWER_CONTROL_POINT = UUID.fromString("00002A66-0000-1000-8000-00805f9b34fb");
    private static final String TAG = EfficiencyActivity.class.getSimpleName();
    private static final long SCAN_PERIOD = 5000;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final String CURRENT_SENSOR_ADDRESS = "3C:A3:08:95:55:F9";
    private static final String LEFT_PEDAL_ADDRESS = "D1:09:1D:D5:D5:42";
    private static final String RIGHT_PEDAL_ADDRESS = "CD:F4:AA:BC:6D:0F";
    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter;
    private TextView ePowerTV, lPowerTV, rPowerTV, eConn, lConn, rConn, wirkungsgradTV, mPowerTV;

    private BluetoothGatt mElectricGatt, mLeftGatt, mRightGatt;
    private int lPower = 0, rPower = 0;
    private float ePower = 0;
    private BluetoothGattCallback CurrentGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, getString(R.string.electric_connected) + gatt.getDevice().getName());
                Log.i(TAG, getString(R.string.service_discovery) +
                        gatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, gatt.getDevice().getName() + R.string.device_disconnected);
                mElectricGatt.close();
                mElectricGatt = null;
                eConn.setText(R.string.label_electrical_power);
                onConnectedDeviceCheck();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, gatt.getDevice().getName() + getString(R.string.service_discovered));
                final BluetoothGattService service = gatt.getService(UUID_ELECTRICAL_POWER_SERVICE);
                if (service != null) {
                    final BluetoothGattCharacteristic electricChar = service.getCharacteristic(UUID_ELECTRICAL_POWER_MEASUREMENT);
                    if (electricChar != null) {
                        mElectricGatt.setCharacteristicNotification(electricChar, true);
                        BluetoothGattDescriptor descriptor = electricChar.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            mElectricGatt.writeDescriptor(descriptor);
                            eConn.setText(gatt.getDevice().getName());
                        }
                    }
                } else {
                    Log.e(TAG, gatt.getDevice().getName() + getString(R.string.service_not_found));
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.i(TAG, gatt.getDevice().getName() + getString(R.string.data_received) + characteristic.getValue().toString());
            if (characteristic.getValue().length > 1) {
                onElectricalPowerReceived(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT32, 0));
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == 0) {
                Log.i(TAG, gatt.getDevice().getName() + getString(R.string.data_request));
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (descriptor.getValue().equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                Log.i(TAG, gatt.getDevice().getName() + getString(R.string.notification_enabled));
            }
        }
    };
    private BluetoothGattCallback CPSGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (gatt == mLeftGatt) {
                    Log.i(TAG, getString(R.string.left_connected) + gatt.getDevice().getName());
                } else if (gatt == mRightGatt) {
                    Log.i(TAG, getString(R.string.right_connected) + gatt.getDevice().getName());
                }
                Log.i(TAG, getString(R.string.service_discovery) +
                        gatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, gatt.getDevice().getName() + R.string.device_disconnected);
                if (gatt == mLeftGatt) {
                    mLeftGatt.close();
                    mLeftGatt = null;
                    lConn.setText(R.string.label_left);
                } else if (gatt == mRightGatt) {
                    mRightGatt.close();
                    mRightGatt = null;
                    rConn.setText(R.string.label_right);
                }
                onConnectedDeviceCheck();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, gatt.getDevice().getName() + getString(R.string.service_discovered));
                final BluetoothGattService service = gatt.getService(UUID_CYCLE_POWER_SERVICE);
                if (service != null) {
                    final BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID_CYCLE_POWER_MEASUREMENT);
                    gatt.setCharacteristicNotification(characteristic, true);
                    final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID_CLIENT_CHARACTERISTIC_CONFIG);
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                        if (gatt == mLeftGatt) {
                            lConn.setText(gatt.getDevice().getName());
                        } else if (gatt == mRightGatt) {
                            rConn.setText(gatt.getDevice().getName());
                        }
                    }
                } else {
                    Log.e(TAG, gatt.getDevice().getName() + getString(R.string.service_not_found));
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.i(TAG, gatt.getDevice().getName() + getString(R.string.data_received));
            final int instantPower = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 2);
            onInstantPowerReceived(gatt, instantPower);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == 0) {
                Log.i(TAG, gatt.getDevice().getName() + getString(R.string.notification_enabled));
            }
        }

    };
    // Diese Methode wertet
    private BluetoothAdapter.LeScanCallback mScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.d(TAG, device.getAddress());
            switch (device.getAddress()) {
                case LEFT_PEDAL_ADDRESS:
                    Log.i(TAG, getString(R.string.left_found));
                    startLeScanning(false);
                    mLeftGatt = device.connectGatt(getContext(), false, CPSGattCallback);
                    break;
                case RIGHT_PEDAL_ADDRESS:
                    Log.i(TAG, getString(R.string.right_found));
                    startLeScanning(false);
                    mRightGatt = device.connectGatt(getContext(), false, CPSGattCallback);
                    break;
                case CURRENT_SENSOR_ADDRESS:
                    Log.i(TAG, getString(R.string.electric_found));
                    startLeScanning(false);
                    mElectricGatt = device.connectGatt(getContext(), false, CurrentGattCallback);
                    break;
                default:
                    Log.d(TAG, device.getAddress());
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();

        // Nach BLE Funktion prüfen
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Ein BluetoothAdapter Objekt erzeugen
        final BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        // Ist der Bluetooth schon angeschaltet?
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            // Falls nicht, bitten wir der Benutzer, Bluetooth an zu schalten
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

        }

        // Wir brauchen diese Erlaubnis, sodass wir auf den BluetoothScanner zugreifen dürfen
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ENABLE_BT);
        }

        // Layout einstellen
        setContentView(R.layout.activity_efficiency);
        ePowerTV = (TextView) findViewById(R.id.textValueEPower);
        lPowerTV = (TextView) findViewById(R.id.textValueLeftPower);
        rPowerTV = (TextView) findViewById(R.id.textValueRightPower);
        mPowerTV = (TextView) findViewById(R.id.textValueTotalMPower);
        wirkungsgradTV = (TextView) findViewById(R.id.textValueEfficiency);
        eConn = (TextView) findViewById(R.id.textEPower);
        lConn = (TextView) findViewById(R.id.textLeft);
        rConn = (TextView) findViewById(R.id.textRight);

    }

    @Override
    protected void onResume() {
        super.onResume();
        onConnectedDeviceCheck();
    }

    @Override
    protected void onPause() {
        super.onPause();
        close();
    }

    // Nach Bluetooth LE Geräte suchen
    private void startLeScanning(final boolean enable) {
        if (enable) {
            // Falls true, dann stoppen wir nach 5 Sekunden, die Geräte nach zu suchen
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothAdapter.stopLeScan(mScanCallback);
                    // Wir prüfen, ob alle Geräte schon verbunden sind
                    onConnectedDeviceCheck();
                }
            }, SCAN_PERIOD);

            mBluetoothAdapter.startLeScan(mScanCallback);
        } else {
            // Falls false, dann stoppen wir jetzt, die Geräte nach zu suchen
            mBluetoothAdapter.stopLeScan(mScanCallback);
        }
    }

    private Context getContext() {
        return this;
    }

    // Diese Methode zeigt den Leistungswert für die Elektrischessystem
    private void onElectricalPowerReceived(int ePower100) {
        Log.i(TAG, getString(R.string.electrical_power_received) + ePower100);
        final float electricPower = ePower100 / 100.0f;

        ePowerTV.setText(String.valueOf(electricPower));
        ePower = electricPower;
    }

    // Diese Methode zeigt die Leistungswerte für jede Pedale auf der GUI
    private void onInstantPowerReceived(BluetoothGatt gatt, int instantPower) {
        Log.d(TAG, gatt.getDevice().getName() + getString(R.string.instant_power_received) +
                String.valueOf(instantPower) + getString(R.string.unit_power_watt));
        if (gatt == mLeftGatt) {
            lPowerTV.setText(String.valueOf(instantPower));
            lPower = instantPower;
        } else if (gatt == mRightGatt) {
            rPowerTV.setText(String.valueOf(instantPower));
            rPower = instantPower;
        }
        if (mElectricGatt != null) {
            requestElectricalPower();
            updateEfficiency();
        }
        final int mPower = lPower + rPower;
        mPowerTV.setText(String.valueOf(mPower));
    }

    // Diese Methode setzt die neue Daten ein
    private void updateEfficiency() {
        final int mPower = Integer.parseInt((String) mPowerTV.getText());

        int wirkungsgrad = 0;
        if (mPower != 0)
            wirkungsgrad = (int) ((ePower * 100.0f) / mPower);
        wirkungsgradTV.setText(String.valueOf(wirkungsgrad));
    }

    // Wir fordern vom Messgerät an, die Daten an App senden
    private void requestElectricalPower() {
        if (mElectricGatt != null) {
            final BluetoothGattService service = mElectricGatt.getService(UUID_ELECTRICAL_POWER_SERVICE);
            if (service != null) {
                final BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID_ELECTRICAL_POWER_MEASUREMENT);
                if (characteristic != null) {
                    characteristic.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT16, 0);
                    mElectricGatt.writeCharacteristic(characteristic);
                }
            }
        }
    }

    // Man prüft, ob alle Geräte schon verbunden sind
    private void onConnectedDeviceCheck() {
        Log.d(TAG, getString(R.string.connect_check));
        if (mLeftGatt != null && mRightGatt != null && mElectricGatt != null) {
            Log.i(TAG, getString(R.string.connect_all_success));
            // Wenn alle Geräte schon verbunden sind, stoppen wir die Scanner
            startLeScanning(false);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            // Wenn nicht alle verbunden sind, starten wir wieder nach 1 Sekunde, die Geräte zu suchen
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startLeScanning(true);
                }
            }, 1000);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    // Diese Methode schliesen die Verbindung von Bluetooth Geräte mit dem App
    private void close() {
        if (mElectricGatt != null) {
            mElectricGatt.close();
            mElectricGatt = null;
        }
        if (mLeftGatt != null) {
            mLeftGatt.close();
            mLeftGatt = null;
        }
        if (mRightGatt != null) {
            mRightGatt.close();
            mRightGatt = null;
        }
    }
}
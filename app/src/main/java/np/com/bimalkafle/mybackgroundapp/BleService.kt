package np.com.bimalkafle.mybackgroundapp

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

data class ContinuousGlucoseData(
    val sensor1: Int,
    val sensor2: Int,
    val sensor3: Int,
    val sensor4: Int,
    val sensorSignalCondition: Int,
    val lowBatteryWarningActive: Boolean
)

data class SensorErrorPacket(
    val faultBase: Int,
    val faultCondition: Int,
    val expectedValue: Int,
    val lowBatteryWarningActive: Boolean
)

@SuppressLint("MissingPermission")
class BleService : Service() {

    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null

    companion object {
        const val TAG = "BleService"
        const val CHANNEL_ID = "BleServiceChannel"
        const val ACTION_BLUETOOTH_DATA = "np.com.bimalkafle.mybackgroundapp.BLE_DATA"
        const val ACTION_BLUETOOTH_DISCONNECTED = "np.com.bimalkafle.mybackgroundapp.BLE_DISCONNECTED"
        const val ACTION_BLUETOOTH_CONNECTED = "np.com.bimalkafle.mybackgroundapp.BLE_CONNECTED"
        const val EXTRA_DATA = "extra_data"
        const val EXTRA_DEVICE_NAME = "extra_device_name"

        val SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server.")
                    val deviceName = gatt.device.name ?: "Unknown Device"
                    sendBroadcast(Intent(ACTION_BLUETOOTH_CONNECTED).apply {
                        putExtra(EXTRA_DEVICE_NAME, deviceName)
                    })
                    Log.i(TAG, "Attempting to start service discovery:" + gatt.discoverServices())
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server.")
                    sendBroadcast(Intent(ACTION_BLUETOOTH_DISCONNECTED))
                    gatt.close()
                    stopSelf()
                }
            }
        }

        @Suppress("DEPRECATION")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        Log.i(TAG, "Services discovered. Enabling notifications.")
                        // Enable notifications for the characteristic
                        gatt.setCharacteristicNotification(characteristic, true)

                        // Enable the CCCD to receive notifications
                        val descriptor = characteristic.getDescriptor(CCCD_UUID)
                        descriptor?.let {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                        } ?: Log.e(TAG, "CCCD descriptor not found for characteristic.")
                    } else {
                        Log.e(TAG, "Characteristic not found.")
                        gatt.disconnect()
                    }
                } else {
                    Log.e(TAG, "Service not found.")
                    gatt.disconnect()
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            // This callback is triggered when the sensor sends new data
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                Log.d(TAG, "Received new data: ${value.toHexString()}")
                val parsedData = parseBleData(value)
                Toast.makeText(this@BleService, "Packet Received: ${parsedData.toString()}", Toast.LENGTH_SHORT).show()
                val intent = Intent(ACTION_BLUETOOTH_DATA).apply {
                    putExtra(EXTRA_DATA, parsedData.toString())
                }
                sendBroadcast(intent)
                updateForegroundNotification(parsedData.toString())
            }
        }

        private fun ByteArray.toHexString(): String =
            joinToString(separator = " ", prefix = "[", postfix = "]") {
                String.format("%02X", it)
            }
    }

    private fun parseBleData(data: ByteArray): Any {
        // Here's the logic to parse the raw byte data into your data classes.
        // This is a crucial step and depends on your wearable's protocol.
        // The following is a speculative example based on your data classes.

        // Assuming a header byte to determine the packet type
        // Let's say byte 0 == 0x01 for ContinuousGlucoseData, 0x02 for SensorErrorPacket
        if (data.isNotEmpty()) {
            val packetType = data[0]

            when (packetType) {
                0x01.toByte() -> { // ContinuousGlucoseData
                    // Assuming a specific byte order and length for each field
                    val buffer = ByteBuffer.wrap(data.copyOfRange(1, data.size))
                        .order(ByteOrder.LITTLE_ENDIAN)

                    return ContinuousGlucoseData(
                        sensor1 = buffer.getShort(0).toInt(),
                        sensor2 = buffer.getShort(2).toInt(),
                        sensor3 = buffer.getShort(4).toInt(),
                        sensor4 = buffer.getShort(6).toInt(),
                        sensorSignalCondition = buffer.get(8).toInt(),
                        lowBatteryWarningActive = (buffer.get(9).toInt() == 1)
                    )
                }
                0x02.toByte() -> { // SensorErrorPacket
                    val buffer = ByteBuffer.wrap(data.copyOfRange(1, data.size))
                        .order(ByteOrder.LITTLE_ENDIAN)

                    return SensorErrorPacket(
                        faultBase = buffer.getShort(0).toInt(),
                        faultCondition = buffer.getShort(2).toInt(),
                        expectedValue = buffer.getShort(4).toInt(),
                        lowBatteryWarningActive = (buffer.get(6).toInt() == 1)
                    )
                }
            }
        }
        return "Unknown packet type received"
    }

    override fun onCreate() {
        super.onCreate()
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "BLE Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
        startForeground(1, createForegroundNotification())
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val device: BluetoothDevice? = intent?.getParcelableExtra("device")
        if (device == null) {
            Log.e(TAG, "No BluetoothDevice passed to service. Stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        connectedDevice = device
        Log.i(TAG, "Connecting to BLE device: ${device.address}")
        // Connect to the GATT server on the device
        bluetoothGatt = device.connectGatt(this, false, gattCallback)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service is being destroyed. Closing GATT.")
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE Connected")
            .setContentText("Listening for sensor data...")
            .setSmallIcon(R.drawable.ic_bluetooth)
            .build()
    }

    private fun updateForegroundNotification(newContent: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLE Data Received")
            .setContentText(newContent)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }
}
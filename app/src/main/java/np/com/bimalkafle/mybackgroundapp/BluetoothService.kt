package np.com.bimalkafle.mybackgroundapp

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.Executors

// Reusing these data classes from the BLE example for packet parsing
// Make sure these are accessible (e.g., in a common file or nested in this service)
// For simplicity, defining them here.
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

class BluetoothClassicService : Service() {

    private val TAG = "BluetoothClassicService"

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var readThread: Thread? = null

    // Binder given to clients
    private val binder = LocalBinder()

    // Standard SPP UUID (Serial Port Profile)
    // Your device MUST be configured to use this UUID for SPP.
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Notification Channel ID for Android O and above
    private val CHANNEL_ID = "BluetoothClassicServiceChannel"
    private val NOTIFICATION_ID = 2 // Different ID from BLE service

    // Callback interface for activities to receive data
    interface OnDataReceivedListener {
        fun onContinuousGlucoseDataReceived(data: ContinuousGlucoseData)
        fun onSensorErrorPacketReceived(error: SensorErrorPacket)
        fun onConnectionStateChange(isConnected: Boolean, deviceName: String?)
    }

    private var dataListener: OnDataReceivedListener? = null

    fun setOnDataReceivedListener(listener: OnDataReceivedListener) {
        this.dataListener = listener
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothClassicService = this@BluetoothClassicService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect() // Ensure resources are cleaned up
        return super.onUnbind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        initializeBluetooth()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Classic Bluetooth: Disconnected"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Classic Bluetooth Service started.")
        val deviceAddress = intent?.getStringExtra("device_address")
        if (deviceAddress != null && bluetoothSocket == null) {
            connect(deviceAddress)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        Log.d(TAG, "Classic Bluetooth Service destroyed.")
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     * @return true if the initialization is successful.
     */
    private fun initializeBluetooth(): Boolean {
        if (bluetoothManager == null) {
            bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            if (bluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.")
                return false
            }
        }

        bluetoothAdapter = bluetoothManager?.adapter
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to get BluetoothAdapter.")
            return false
        }
        return true
    }

    /**
     * Connects to the Bluetooth Classic device via SPP.
     * @param address The device address of the destination device.
     */
    @SuppressLint("MissingPermission") // Permissions handled by activity
    fun connect(address: String?) {
        if (bluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.")
            dataListener?.onConnectionStateChange(false, null)
            return
        }

        val device: BluetoothDevice? = try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid Bluetooth device address: $address", e)
            dataListener?.onConnectionStateChange(false, null)
            return
        }

        if (device == null) {
            Log.w(TAG, "Device not found. Unable to connect.")
            dataListener?.onConnectionStateChange(false, null)
            return
        }

        // Cancel any existing discovery, as it will slow down connection.
        bluetoothAdapter?.cancelDiscovery()

        // Close any existing socket
        disconnect()

        readThread?.interrupt() // Interrupt any previous read thread
        readThread = null

        Executors.newSingleThreadExecutor().execute {
            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used by the server code.
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                Log.d(TAG, "Attempting to connect to device: ${device.name ?: device.address}")

                bluetoothSocket?.connect()
                inputStream = bluetoothSocket?.inputStream
                Log.d(TAG, "Connected to ${device.name ?: device.address}")

                dataListener?.onConnectionStateChange(true, device.name ?: device.address)
                updateNotification("Connected to ${device.name ?: device.address}")

                // Start reading from the InputStream
                startReadingThread()

            } catch (e: IOException) {
                Log.e(TAG, "Socket connection failed", e)
                try {
                    bluetoothSocket?.close()
                } catch (e2: IOException) {
                    Log.e(TAG, "Could not close the client socket", e2)
                }
                dataListener?.onConnectionStateChange(false, null)
                updateNotification("Connection Failed.")
            }
        }
    }

    /**
     * Disconnects and closes the socket.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.d(TAG, "Disconnecting from device.")
        readThread?.interrupt()
        readThread = null
        try {
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the connect socket", e)
        } finally {
            inputStream = null
            bluetoothSocket = null
        }
        dataListener?.onConnectionStateChange(false, null)
        updateNotification("Disconnected.")
    }

    /**
     * Starts a new thread to continuously read data from the InputStream.
     */
    private fun startReadingThread() {
        readThread = Thread {
            val buffer = ByteArray(256) // Buffer for incoming bytes
            var bytes: Int // Bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (!Thread.currentThread().isInterrupted && bluetoothSocket?.isConnected == true) {
                try {
                    // Read from the InputStream
                    bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val receivedData = buffer.copyOfRange(0, bytes)
                        // In a real SPP scenario, you'd buffer bytes and look for start/end markers
                        // of your IMS CGM packet. For simplicity, we're assuming read() might
                        // give us a full packet or we'll process chunks.
                        // The parseImsCgmPacket expects a complete packet.
                        // You'll need more robust buffering here if packets can be fragmented.
                        parseImsCgmPacket(receivedData)
                    }
                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }
            }
            Log.d(TAG, "Reading thread stopped.")
            disconnect() // Disconnect if the read loop ends
        }.apply {
            start()
        }
    }

    /**
     * Parses the raw byte array into IMS CGM packet structure.
     * This logic is largely reused from the BLE service.
     */
    private fun parseImsCgmPacket(data: ByteArray?) {
        if (data == null || data.size < 3) { // Minimum size: Header (1) + PID (1) + Footer (1)
            Log.w(TAG, "Received invalid packet: null or too short.")
            return
        }

        // --- Important Note for SPP: ---
        // For stream-oriented protocols like SPP, you must implement robust
        // packet framing. This means buffering incoming bytes and looking for
        // the Header (0x55) and Footer (0x01) to correctly identify complete packets.
        // The current parseImsCgmPacket assumes a full packet is passed.
        // If data is fragmented (common in SPP), this function will fail.
        // A simple approach is to search for 0x55, then read expected length, then check 0x01.

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN) // Assuming Little Endian

        if (buffer.remaining() < 2) { // Need at least Header + PID to proceed
            Log.w(TAG, "Not enough bytes for Header and PID in packet fragment.")
            return
        }

        val header = buffer.get().toUByte().toInt()
        val pid = buffer.get().toUByte().toInt()

        if (header != 0x55) {
            Log.w(TAG, "Invalid IMS CGM Header: Expected 0x55, got 0x${String.format("%02X", header)}")
            // In a stream, you'd want to discard this byte and keep looking for 0x55
            return
        }

        when (pid) {
            0xCD -> { // Continuous Glucose Data Packet
                if (buffer.remaining() < 9) { // 4x uint16 (8 bytes) + 2x uint8 (2 bytes) = 10 bytes payload + 1 byte footer = 11 bytes total.
                    // Remaining after header/PID is 9 bytes for payload + footer.
                    Log.w(TAG, "Continuous Glucose Data Packet too short for 0xCD. Expected at least 9 bytes after header/PID. Remaining: ${buffer.remaining()}")
                    return
                }
                val sensor1 = buffer.getShort().toUShort().toInt()
                val sensor2 = buffer.getShort().toUShort().toInt()
                val sensor3 = buffer.getShort().toUShort().toInt()
                val sensor4 = buffer.getShort().toUShort().toInt()
                val sensorSignalCondition = buffer.get().toUByte().toInt()
                val lowBatteryWarningActive = buffer.get().toUByte().toInt()

                val footer = buffer.get().toUByte().toInt() // Read Footer (0x01)
                if (footer != 0x01) {
                    Log.w(TAG, "Invalid IMS CGM Footer for 0xCD packet: Expected 0x01, got 0x${String.format("%02X", footer)}")
                    return
                }

                val glucoseData = ContinuousGlucoseData(
                    sensor1, sensor2, sensor3, sensor4,
                    sensorSignalCondition, lowBatteryWarningActive == 1
                )
                Log.d(TAG, "Received Classic Glucose Data: $glucoseData")
                dataListener?.onContinuousGlucoseDataReceived(glucoseData)
                updateNotification("Classic Glucose: ${glucoseData.sensor1}")
            }
            0xFE -> { // Sensor Error Packet
                if (buffer.remaining() < 5) { // 4x uint8 (4 bytes) + 1 byte footer = 5 bytes total.
                    // Remaining after header/PID is 5 bytes for payload + footer.
                    Log.w(TAG, "Sensor Error Packet too short for 0xFE. Expected at least 5 bytes after header/PID. Remaining: ${buffer.remaining()}")
                    return
                }
                val faultBase = buffer.get().toUByte().toInt()
                val faultCondition = buffer.get().toUByte().toInt()
                val expectedValue = buffer.get().toUByte().toInt()
                val lowBatteryWarningActive = buffer.get().toUByte().toInt()

                val footer = buffer.get().toUByte().toInt() // Read Footer (0x01)
                if (footer != 0x01) {
                    Log.w(TAG, "Invalid IMS CGM Footer for 0xFE packet: Expected 0x01, got 0x${String.format("%02X", footer)}")
                    return
                }

                val errorPacket = SensorErrorPacket(
                    faultBase, faultCondition, expectedValue, lowBatteryWarningActive == 1
                )
                Log.d(TAG, "Received Classic Sensor Error: $errorPacket")
                dataListener?.onSensorErrorPacketReceived(errorPacket)
                updateNotification("Classic Error: 0x${String.format("%02X", errorPacket.faultCondition)}")
            }
            else -> {
                Log.w(TAG, "Unknown IMS CGM PID: 0x${String.format("%02X", pid)}")
            }
        }
    }

    /**
     * Creates a persistent notification for the foreground service.
     */
    private fun createNotification(message: String): Notification {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Classic Bluetooth Monitoring")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_bluetooth) // Use an appropriate icon
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Makes the notification non-dismissible
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority to be less intrusive
            .build()
    }

    /**
     * Creates a notification channel for Android O (API 26) and above.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Classic Bluetooth Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Updates the foreground service notification.
     */
    fun updateNotification(message: String) {
        val notification = createNotification(message)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
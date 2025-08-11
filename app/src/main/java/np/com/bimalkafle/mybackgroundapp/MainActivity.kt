package np.com.bimalkafle.mybackgroundapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import np.com.bimalkafle.mybackgroundapp.BleService.Companion.CHANNEL_ID
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val notificationId = 101

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerRecycler: RecyclerView
    private lateinit var toolbar: Toolbar
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var btnAskGlucose: Button
    private lateinit var tvGlucoseResult: TextView
    private lateinit var tts: TextToSpeech
    private lateinit var glucoseDbHelper: GlucoseDbHelper
    private lateinit var btnConnectBluetooth: Button
    private lateinit var tvBluetoothStatus: TextView
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var powerManager: PowerManager
    private var connectedDeviceName: String? = null

    private val VOICE_INPUT_REQUEST_CODE = 1002
    private var timeIndex = 0f
    private lateinit var heartRateChart: LineChart
    private lateinit var glucoseChartOne: LineChart
    private lateinit var glucoseChartTwo: LineChart
    private lateinit var glucoseChartThree: LineChart

    private lateinit var heartRateDataSet: LineDataSet
    private lateinit var glucoseOneDataSet: LineDataSet
    private lateinit var glucoseTwoDataSet: LineDataSet
    private lateinit var glucoseThreeDataSet: LineDataSet

    private var bleScanner: BluetoothLeScanner? = null
    private var scanning = false
    private val scanResults = mutableMapOf<String, BluetoothDevice>()
    private val SCAN_PERIOD: Long = 60000

    private val drawerItems = listOf(
        DrawerItem(R.drawable.ic_launcher_foreground, "Dashboard", "Real-time monitoring"),
        DrawerItem(R.drawable.ic_launcher_foreground, "Glucose Monitor", "Live glucose tracking"),
        DrawerItem(R.drawable.ic_launcher_foreground, "Sensors", "Device management", false),
        DrawerItem(R.drawable.ic_launcher_foreground, "Analytics", "Data insights", false),
        DrawerItem(R.drawable.ic_launcher_foreground, "History", "Historical data & trends", false),
        DrawerItem(R.drawable.ic_launcher_foreground, "Alarms", "Alert settings", false),
        DrawerItem(R.drawable.ic_launcher_foreground, "Settings", "App configuration", false),
        DrawerItem(R.drawable.ic_launcher_foreground, "Export Data", "Reports & sharing", false),
        DrawerItem(R.drawable.ic_launcher_foreground, "Privacy", "Data protection", false),
        DrawerItem(R.drawable.ic_launcher_foreground, "Help", "Support & guides", false)
    )

    private val dataReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BleService.ACTION_BLUETOOTH_DATA -> {
                    val data = intent.getStringExtra("Data received")
                    Toast.makeText(this@MainActivity, data, Toast.LENGTH_SHORT).show()
                }
                BleService.ACTION_BLUETOOTH_DISCONNECTED -> {
                    tvBluetoothStatus.text = "❌ Disconnected"
                    tvBluetoothStatus.setTextColor(getColor(R.color.red))
                }
                BleService.ACTION_BLUETOOTH_CONNECTED -> {
                    val deviceName = intent.getStringExtra(BleService.EXTRA_DEVICE_NAME)
                    tvBluetoothStatus.text = "✅ Connected to: $deviceName"
                    tvBluetoothStatus.setTextColor(getColor(R.color.green))
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        drawerRecycler = findViewById(R.id.drawer_recycler)

        val btnManualInput: Button = findViewById(R.id.btn_manual_input)
        btnManualInput.setOnClickListener {
            val intent = Intent(this, ManualInputActivity::class.java)
            startActivity(intent)
        }

        val btnViewEntries: Button = findViewById(R.id.btn_view_entries)
        btnViewEntries.setOnClickListener {
            val intent = Intent(this, ViewEntriesActivity::class.java)
            startActivity(intent)
        }

        // Setup Voice Input Button
        setupVoiceInputButton()

        drawerRecycler.layoutManager = LinearLayoutManager(this)
        drawerRecycler.adapter = DrawerAdapter(drawerItems) { position ->
            handleDrawerClick(position)
        }

        drawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        
        btnAskGlucose = findViewById(R.id.btn_ask_glucose)
        tvGlucoseResult = findViewById(R.id.tv_glucose_result)
        glucoseDbHelper = GlucoseDbHelper(this)

        btnConnectBluetooth = findViewById(R.id.btn_connect_bluetooth)
        tvBluetoothStatus = findViewById(R.id.tv_bluetooth_status)
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        connectedDeviceName = null
        updateBluetoothStatus()

        btnConnectBluetooth.setOnClickListener {
            handleBluetoothAndLocationFlow()
        }

        insertDummyGlucoseDataIfNeeded()

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale.getDefault()
            }
        }

        btnAskGlucose.setOnClickListener {
            val level = glucoseDbHelper.getLatestLevel()
            if (level != null) {
                val msg = "$level mg/dL."
                tvGlucoseResult.text = msg
                tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                val msg = "No data found."
                tvGlucoseResult.text = msg
                tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }

        val filter = IntentFilter().apply {
            addAction(BleService.ACTION_BLUETOOTH_DATA)
            addAction(BleService.ACTION_BLUETOOTH_DISCONNECTED)
            addAction(BleService.ACTION_BLUETOOTH_CONNECTED)
        }
        registerReceiver(dataReceiver, filter, RECEIVER_EXPORTED)

        createNotificationChannel()

        intializeCharts()
        sensorOneChart()
        sensorTwoChart()
        sensorThreeChart()
        sensorFourChart()
    }

    /*override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        unregisterReceiver(dataReceiver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            stopBleScan()
        }
    }*/

    private fun setupVoiceInputButton() {
        // Find your voice input button (adjust the ID to match your layout)
        val voiceInputButton = findViewById<Button>(R.id.btn_voice)

        voiceInputButton.setOnClickListener {
            // Check microphone permission first
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    VOICE_INPUT_REQUEST_CODE)
            } else {
                launchVoiceInputActivity()
            }
        }
    }

    private fun launchVoiceInputActivity() {
        val intent = Intent(this, VoiceInputActivity::class.java)
        startActivityForResult(intent, VOICE_INPUT_REQUEST_CODE)
        // Add animation if you have anim folder
        // overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private fun insertDummyGlucoseDataIfNeeded() {
        val db = glucoseDbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM glucose_entries", null)
        if (cursor.moveToFirst()) {
            val count = cursor.getInt(0)
            if (count == 0) {
                val now = System.currentTimeMillis()
                glucoseDbHelper.insertLevel(110, Date(now - 3600_000).toString())
                glucoseDbHelper.insertLevel(120, Date(now - 1800_000).toString())
                glucoseDbHelper.insertLevel(130, Date(now).toString())
            }
        }
        cursor.close()
    }

    private fun handleBluetoothAndLocationFlow() {
        if (bluetoothAdapter == null) {
            tvBluetoothStatus.text = getString(R.string.bluetooth_not_supported_message)
            return
        }
        if (!bluetoothAdapter!!.isEnabled) {
            val intentBt = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            startActivity(intentBt)
            tvBluetoothStatus.text = getString(R.string.enable_bluetooth_message)
            return
        }
        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        if (!locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
            && !locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        ) {
            val intentLoc = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intentLoc)
            tvBluetoothStatus.text = getString(R.string.enable_location_message)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothAndLocationPermissions()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestBluetoothAndLocationPermissions() {
        val permissions = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 2002)
        } else {
            startBleScan()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private val bleScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (result.device.name != null && result.device.name.startsWith("IMS")) {
                stopBleScan()
                connectToBleDevice(result.device)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (!scanning) {
            scanning = true
            scanResults.clear()
            bleScanner?.startScan(bleScanCallback)
            tvBluetoothStatus.text = "⏳ Scanning for 'IMS' devices..."
            tvBluetoothStatus.setTextColor(getColor(R.color.white))
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (scanning) {
            scanning = false
            bleScanner?.stopScan(bleScanCallback)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1001 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    launchSTTActivity()
                } else {
                    Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
                }
            }
            VOICE_INPUT_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    launchVoiceInputActivity()
                } else {
                    Toast.makeText(this, "Microphone permission is required for voice input", Toast.LENGTH_SHORT).show()
                }
            }
            2002 -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startBleScan()
                } else {
                    tvBluetoothStatus.text = "❌ Bluetooth/Location permissions denied."
                }
            }
        }
    }

    // Handle the result from voice input activity
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VOICE_INPUT_REQUEST_CODE && resultCode == RESULT_OK) {
            val recognizedText = data?.getStringExtra("insulin_input")
            if (!recognizedText.isNullOrEmpty()) {
                handleVoiceInputResult(recognizedText)
            }
        }
    }

    private fun handleVoiceInputResult(voiceText: String) {
        // Process the recognized text and show result
        Toast.makeText(this, "Voice input received: $voiceText", Toast.LENGTH_LONG).show()

        // Parse the insulin input
        parseInsulinInput(voiceText)

        // Optionally speak the confirmation
        val confirmationMsg = "Received insulin input: $voiceText"
        tts.speak(confirmationMsg, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun parseInsulinInput(input: String) {
        // Simple parsing to extract insulin information
        val lowerInput = input.lowercase()

        // Extract numbers (insulin units)
        val numbers = Regex("\\d+").findAll(lowerInput).map { it.value.toInt() }.toList()

        // Determine insulin type
        val insulinType = when {
            lowerInput.contains("rapid") || lowerInput.contains("fast") -> "Rapid Acting"
            lowerInput.contains("long") || lowerInput.contains("slow") -> "Long Acting"
            lowerInput.contains("regular") -> "Regular"
            else -> "Unknown Type"
        }

        if (numbers.isNotEmpty()) {
            val units = numbers.first()
            showInsulinEntry(units, insulinType, input)
        } else {
            Toast.makeText(this, "Could not extract insulin units from: $input", Toast.LENGTH_LONG).show()
        }
    }

    private fun showInsulinEntry(units: Int, type: String, originalText: String) {
        // Create a formatted message
        val message = """
            📋 Insulin Entry Recorded:
            💉 Units: $units
            🏷️ Type: $type
            🎤 Voice: "$originalText"
        """.trimIndent()

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Here you can:
        // 1. Save to your insulin database
        // 2. Update your UI
        // 3. Add to insulin log/history
        // 4. Set reminders or alerts

        // Example: Save to database (you'll need to implement this)
        // saveInsulinEntry(units, type, originalText)

        // Update UI or trigger any other actions
        updateUIWithNewInsulinEntry(units, type)
    }

    private fun updateUIWithNewInsulinEntry(units: Int, type: String) {
        // Update your UI to reflect the new insulin entry
        // For example, update a TextView or refresh a list

        // Example: Update glucose result TextView to show last insulin entry
        tvGlucoseResult.text = "Last insulin entry: $units mg/dl"
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        unregisterReceiver(dataReceiver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            stopBleScan()
        }
    }

    private fun handleDrawerClick(position: Int) {
        when (position) {
            1 -> {
                checkMicPermissionAndLaunchSTT()
            }
        }
        drawerLayout.closeDrawers()
    }

    private fun checkMicPermissionAndLaunchSTT() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1001
            )
        } else {
            launchSTTActivity()
        }
    }

    private fun launchSTTActivity() {
        val intent = Intent(this, STTActivity::class.java)
        startActivity(intent)
    }

    private fun updateBluetoothStatus() {
        if (connectedDeviceName != null) {
            tvBluetoothStatus.text =
                getString(R.string.connected_to_device_message, connectedDeviceName)
            tvBluetoothStatus.setTextColor(getColor(R.color.green))
        } else {
            tvBluetoothStatus.text = getString(R.string.no_device_connected_message)
            tvBluetoothStatus.setTextColor(getColor(R.color.white))
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.S)
    private fun connectToBleDevice(device: BluetoothDevice) {
        connectedDeviceName = device.name
        updateBluetoothStatus()

        val serviceIntent = Intent(this, BleService::class.java).apply {
            putExtra("device", device)
        }
        startForegroundService(serviceIntent)

        Toast.makeText(this, "Connecting to BLE device: ${device.name}", Toast.LENGTH_SHORT).show()
    }

    private fun createNotificationChannel() {
        val name = "Bluetooth Packets"
        val descriptionText = "Notifications for incoming Bluetooth data"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("MainActivity", "POST_NOTIFICATIONS permission not granted.")
                return
            }
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_bluetooth)

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, builder.build())
        }
    }

    fun intializeCharts() {
        heartRateChart = findViewById<LineChart>(R.id.heartRateChart)
        heartRateChart.setExtraOffsets(0f, 16f, 0f, 0f)

        glucoseChartOne = findViewById<LineChart>(R.id.glucoseLevelChartOne)
        glucoseChartOne.setExtraOffsets(0f, 16f, 0f, 0f)

        glucoseChartTwo = findViewById<LineChart>(R.id.glucoseLevelChartTwo)
        glucoseChartTwo.setExtraOffsets(0f, 16f, 0f, 0f)

        glucoseChartThree = findViewById<LineChart>(R.id.glucoseLevelChartThree)
        glucoseChartThree.setExtraOffsets(0f, 16f, 0f, 0f)
    }

    fun sensorOneChart() {
        val entries = mutableListOf<Entry>()
        heartRateDataSet = LineDataSet(entries, "Heart Rate")
        heartRateDataSet.color = ContextCompat.getColor(this@MainActivity, R.color.heart_rate_color)
        heartRateDataSet.fillColor =
            ContextCompat.getColor(this@MainActivity, R.color.heart_rate_color)
        heartRateDataSet.setDrawCircles(false)
        heartRateDataSet.lineWidth = 2f
        heartRateDataSet.setDrawFilled(true)

        val lineData = LineData(heartRateDataSet)
        heartRateChart.data = lineData
        heartRateChart.description.text = "Heartbeat over 3 hours"
        heartRateChart.xAxis.labelRotationAngle = -45f
        heartRateChart.animateX(1500)

        val yAxis = heartRateChart.axisLeft
        yAxis.axisMinimum = 40f
        yAxis.axisMaximum = 180f
        heartRateChart.axisRight.isEnabled = false

        val restingLimit = LimitLine(60f, "Resting HR").apply {
            lineColor = Color.GRAY
            lineWidth = 2f
            textColor = Color.DKGRAY
            textSize = 12f
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
        }

        val maxLimit = LimitLine(160f, "Max HR").apply {
            lineColor = Color.RED
            lineWidth = 2f
            textColor = Color.RED
            textSize = 12f
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }

        yAxis.removeAllLimitLines()
        yAxis.addLimitLine(restingLimit)
        yAxis.addLimitLine(maxLimit)
        yAxis.setDrawLimitLinesBehindData(true)
        heartRateChart.invalidate()

        startLiveUpdates()
    }

    fun startLiveUpdates() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val randomHeartRate = (60..160).random().toFloat()
                heartRateDataSet.addEntry(Entry(timeIndex, randomHeartRate))
                heartRateChart.data.notifyDataChanged()
                heartRateChart.notifyDataSetChanged()
                heartRateChart.invalidate()

                timeIndex += 5f / 60f // 5 seconds as fraction of an hour

                handler.postDelayed(this, 2000)
            }
        }
        handler.post(runnable)
    }

    fun sensorTwoChart() {
        val entries = mutableListOf<Entry>()
        glucoseOneDataSet = LineDataSet(entries, "Glucose Level")
        glucoseOneDataSet.color =
            ContextCompat.getColor(this@MainActivity, R.color.glucose_one_color)
        glucoseOneDataSet.fillColor =
            ContextCompat.getColor(this@MainActivity, R.color.glucose_one_color)
        glucoseOneDataSet.setDrawCircles(false)
        glucoseOneDataSet.lineWidth = 2f
        glucoseOneDataSet.setDrawFilled(true)

        val lineData = LineData(glucoseOneDataSet)
        glucoseChartOne.data = lineData
        glucoseChartOne.description.text = "Glucose Level over 3 hours"
        glucoseChartOne.xAxis.labelRotationAngle = -45f
        glucoseChartOne.animateX(1500)

        val yAxis = glucoseChartOne.axisLeft
        yAxis.axisMinimum = 40f
        yAxis.axisMaximum = 180f
        glucoseChartOne.axisRight.isEnabled = false

        val restingLimit = LimitLine(60f, "Min Limit").apply {
            lineColor = Color.GRAY
            lineWidth = 2f
            textColor = Color.DKGRAY
            textSize = 12f
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
        }

        val maxLimit = LimitLine(160f, "Max Limit").apply {
            lineColor = Color.RED
            lineWidth = 2f
            textColor = Color.RED
            textSize = 12f
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }

        yAxis.removeAllLimitLines()
        yAxis.addLimitLine(restingLimit)
        yAxis.addLimitLine(maxLimit)
        yAxis.setDrawLimitLinesBehindData(true)
        glucoseChartOne.invalidate()

        startLiveUpdatesGlucose()
    }

    fun sensorThreeChart() {
        val entries = mutableListOf<Entry>()
        glucoseTwoDataSet = LineDataSet(entries, "Glucose Level")
        glucoseTwoDataSet.color =
            ContextCompat.getColor(this@MainActivity, R.color.glucose_two_color)
        glucoseTwoDataSet.fillColor =
            ContextCompat.getColor(this@MainActivity, R.color.glucose_two_color)
        glucoseTwoDataSet.setDrawCircles(false)
        glucoseTwoDataSet.lineWidth = 2f
        glucoseTwoDataSet.setDrawFilled(true)

        val lineData = LineData(glucoseOneDataSet)
        glucoseChartTwo.data = lineData
        glucoseChartTwo.description.text = "Glucose Level over 3 hours"
        glucoseChartTwo.xAxis.labelRotationAngle = -45f
        glucoseChartTwo.animateX(1500)

        val yAxis = glucoseChartTwo.axisLeft
        yAxis.axisMinimum = 40f
        yAxis.axisMaximum = 180f
        glucoseChartTwo.axisRight.isEnabled = false

        val restingLimit = LimitLine(60f, "Min Limit").apply {
            lineColor = Color.GRAY
            lineWidth = 2f
            textColor = Color.DKGRAY
            textSize = 12f
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
        }

        val maxLimit = LimitLine(160f, "Max Limit").apply {
            lineColor = Color.RED
            lineWidth = 2f
            textColor = Color.RED
            textSize = 12f
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }

        yAxis.removeAllLimitLines()
        yAxis.addLimitLine(restingLimit)
        yAxis.addLimitLine(maxLimit)
        yAxis.setDrawLimitLinesBehindData(true)
        glucoseChartTwo.invalidate()

        startLiveUpdatesGlucose()
    }

    fun sensorFourChart() {
        val entries = mutableListOf<Entry>()
        glucoseThreeDataSet = LineDataSet(entries, "Glucose Level")
        glucoseThreeDataSet.color =
            ContextCompat.getColor(this@MainActivity, R.color.glucose_three_color)
        glucoseThreeDataSet.fillColor =
            ContextCompat.getColor(this@MainActivity, R.color.glucose_three_color)
        glucoseThreeDataSet.setDrawCircles(false)
        glucoseThreeDataSet.lineWidth = 2f
        glucoseThreeDataSet.setDrawFilled(true)

        val lineData = LineData(glucoseOneDataSet)
        glucoseChartThree.data = lineData
        glucoseChartThree.description.text = "Glucose Level over 3 hours"
        glucoseChartThree.xAxis.labelRotationAngle = -45f
        glucoseChartThree.animateX(1500)

        val yAxis = glucoseChartThree.axisLeft
        yAxis.axisMinimum = 40f
        yAxis.axisMaximum = 180f
        glucoseChartThree.axisRight.isEnabled = false

        val restingLimit = LimitLine(60f, "Min Limit").apply {
            lineColor = Color.GRAY
            lineWidth = 2f
            textColor = Color.DKGRAY
            textSize = 12f
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
        }

        val maxLimit = LimitLine(160f, "Max Limit").apply {
            lineColor = Color.RED
            lineWidth = 2f
            textColor = Color.RED
            textSize = 12f
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }

        yAxis.removeAllLimitLines()
        yAxis.addLimitLine(restingLimit)
        yAxis.addLimitLine(maxLimit)
        yAxis.setDrawLimitLinesBehindData(true)
        glucoseChartThree.invalidate()

        startLiveUpdatesGlucose()
    }

    fun startLiveUpdatesGlucose() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val randomHeartRate = (80..120).random().toFloat()
                glucoseOneDataSet.addEntry(Entry(timeIndex, randomHeartRate))
                glucoseChartOne.data.notifyDataChanged()
                glucoseChartOne.notifyDataSetChanged()
                glucoseChartOne.invalidate()

                glucoseTwoDataSet.addEntry(Entry(timeIndex, randomHeartRate))
                glucoseChartTwo.data.notifyDataChanged()
                glucoseChartTwo.notifyDataSetChanged()
                glucoseChartTwo.invalidate()

                glucoseThreeDataSet.addEntry(Entry(timeIndex, randomHeartRate))
                glucoseChartThree.data.notifyDataChanged()
                glucoseChartThree.notifyDataSetChanged()
                glucoseChartThree.invalidate()

                timeIndex += 5f / 60f // 5 seconds as fraction of an hour

                handler.postDelayed(this, 2000)
            }
        }
        handler.post(runnable)
    }
}
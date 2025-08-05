package np.com.bimalkafle.mybackgroundapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.app.ActionBarDrawerToggle
import java.util.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings

class MainActivity : AppCompatActivity() {

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

    // Voice Input Activity Request Code
    private val VOICE_INPUT_REQUEST_CODE = 1002

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

    private val bluetoothReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val deviceName = device?.name ?: ""
                if (deviceName.startsWith("IMS")) {
                    connectedDeviceName = deviceName
                    updateBluetoothStatus()
                    bluetoothAdapter?.cancelDiscovery()
                    try { unregisterReceiver(this) } catch (_: Exception) {}
                }
            }
        }
    }

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

        // Don't open drawer automatically - only when dashboard button is clicked

        btnAskGlucose = findViewById(R.id.btn_ask_glucose)
        tvGlucoseResult = findViewById(R.id.tv_glucose_result)
        glucoseDbHelper = GlucoseDbHelper(this)

        btnConnectBluetooth = findViewById(R.id.btn_connect_bluetooth)
        tvBluetoothStatus = findViewById(R.id.tv_bluetooth_status)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        connectedDeviceName = null
        updateBluetoothStatus()

        checkAndRequestBackgroundPermission()

        btnConnectBluetooth.setOnClickListener {
            handleBluetoothAndLocationFlow()
        }

        // Insert dummy glucose data if table is empty
        insertDummyGlucoseDataIfNeeded()

        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.ERROR) {
                tts.language = Locale.getDefault()
            }
        }

        btnAskGlucose.setOnClickListener {
            val level = glucoseDbHelper.getLatestLevel()
            if (level != null) {
                val msg = "Your latest glucose level is $level mg/dL."
                tvGlucoseResult.text = msg
                tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                val msg = "No glucose data found."
                tvGlucoseResult.text = msg
                tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

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

    private fun checkAndRequestBackgroundPermission() {
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Please allow background permission for proper functioning.", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleBluetoothAndLocationFlow() {
        if (bluetoothAdapter == null) {
            tvBluetoothStatus.text = "❌ Bluetooth not supported."
            return
        }
        if (!bluetoothAdapter!!.isEnabled) {
            val intentBt = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            startActivity(intentBt)
            tvBluetoothStatus.text = "Please enable Bluetooth."
            return
        }
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        if (!locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
            && !locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
            val intentLoc = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intentLoc)
            tvBluetoothStatus.text = "Please enable Location."
            return
        }
        requestBluetoothAndLocationPermissions()
    }

    private fun requestBluetoothAndLocationPermissions() {
        val permissions = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 2002)
        } else {
            startBluetoothDiscovery()
        }
    }

    private fun startBluetoothDiscovery() {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(bluetoothReceiver, filter)
        bluetoothAdapter?.startDiscovery()
        tvBluetoothStatus.text = "\uD83D\uDD0D Scanning for IMS devices..."
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
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
                    startBluetoothDiscovery()
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
        try { unregisterReceiver(bluetoothReceiver) } catch (_: Exception) {}
    }

    private fun handleDrawerClick(position: Int) {
        when (position) {
            1 -> { // Glucose Monitor
                checkMicPermissionAndLaunchSTT()
            }
            // Add other navigation actions here
        }
        drawerLayout.closeDrawers()
    }

    private fun checkMicPermissionAndLaunchSTT() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
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
            tvBluetoothStatus.text = "\uD83D\uDFE2 Connected to: $connectedDeviceName"
        } else {
            tvBluetoothStatus.text = "No device connected."
        }
    }
}
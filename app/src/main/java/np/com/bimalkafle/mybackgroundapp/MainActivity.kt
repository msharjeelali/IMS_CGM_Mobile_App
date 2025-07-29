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
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

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

    private var timeIndex = 0f
    private lateinit var heartRateChart: LineChart
    private lateinit var glucoseChartOne: LineChart
    private lateinit var glucoseChartTwo: LineChart
    private lateinit var glucoseChartThree: LineChart

    private lateinit var heartRateDataSet: LineDataSet
    private lateinit var glucoseOneDataSet: LineDataSet
    private lateinit var glucoseTwoDataSet: LineDataSet
    private lateinit var glucoseThreeDataSet: LineDataSet

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

        // Open the drawer automatically on startup for testing
        drawerLayout.openDrawer(findViewById(R.id.nav_drawer))

        btnAskGlucose = findViewById(R.id.btn_ask_glucose)
        tvGlucoseResult = findViewById(R.id.tv_glucose_result)
        glucoseDbHelper = GlucoseDbHelper(this)

        btnConnectBluetooth = findViewById(R.id.btn_connect_bluetooth)
        tvBluetoothStatus = findViewById(R.id.tv_bluetooth_status)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        connectedDeviceName = null
        updateBluetoothStatus()

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

        intializeCharts()

        sensorOneChart()
        sensorTwoChart()
        sensorThreeChart()
        sensorFourChart()
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
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchSTTActivity()
        } else if (requestCode == 1001) {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
        } else if (requestCode == 2002) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startBluetoothDiscovery()
            } else {
                tvBluetoothStatus.text = "❌ Bluetooth/Location permissions denied."
            }
        }
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

    fun intializeCharts() {
        heartRateChart = findViewById<LineChart>(R.id.heartRateChart)
        heartRateChart.setExtraOffsets(0f,16f,0f,0f)

        glucoseChartOne = findViewById<LineChart>(R.id.glucoseLevelChartOne)
        glucoseChartOne.setExtraOffsets(0f,16f,0f,0f)

        glucoseChartTwo = findViewById<LineChart>(R.id.glucoseLevelChartTwo)
        glucoseChartTwo.setExtraOffsets(0f,16f,0f,0f)

        glucoseChartThree = findViewById<LineChart>(R.id.glucoseLevelChartThree)
        glucoseChartThree.setExtraOffsets(0f,16f,0f,0f)

    }

    fun sensorOneChart() {

        val entries = mutableListOf<Entry>()
        heartRateDataSet = LineDataSet(entries, "Heart Rate")
        heartRateDataSet.color = ContextCompat.getColor(this@MainActivity,R.color.heart_rate_color)
        heartRateDataSet.fillColor = ContextCompat.getColor(this@MainActivity,R.color.heart_rate_color)
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
        glucoseOneDataSet.color = ContextCompat.getColor(this@MainActivity,R.color.glucose_one_color)
        glucoseOneDataSet.fillColor = ContextCompat.getColor(this@MainActivity,R.color.glucose_one_color)
        glucoseOneDataSet.setDrawCircles(false)
        glucoseOneDataSet.lineWidth = 2f
        glucoseOneDataSet.setDrawFilled(true)

        val lineData = LineData(glucoseOneDataSet)
        glucoseChartOne.data = lineData
        glucoseChartOne.description.text = "Glucose Level over 3 hours"
        glucoseChartOne.xAxis.labelRotationAngle = -45f
        glucoseChartOne.animateX(1500)

        // Set Y-axis range and limit lines
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
        glucoseTwoDataSet.color = ContextCompat.getColor(this@MainActivity,R.color.glucose_two_color)
        glucoseTwoDataSet.fillColor = ContextCompat.getColor(this@MainActivity,R.color.glucose_two_color)
        glucoseTwoDataSet.setDrawCircles(false)
        glucoseTwoDataSet.lineWidth = 2f
        glucoseTwoDataSet.setDrawFilled(true)

        val lineData = LineData(glucoseOneDataSet)
        glucoseChartTwo.data = lineData
        glucoseChartTwo.description.text = "Glucose Level over 3 hours"
        glucoseChartTwo.xAxis.labelRotationAngle = -45f
        glucoseChartTwo.animateX(1500)

        // Set Y-axis range and limit lines
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
        glucoseThreeDataSet.color = ContextCompat.getColor(this@MainActivity,R.color.glucose_three_color)
        glucoseThreeDataSet.fillColor = ContextCompat.getColor(this@MainActivity,R.color.glucose_three_color)
        glucoseThreeDataSet.setDrawCircles(false)
        glucoseThreeDataSet.lineWidth = 2f
        glucoseThreeDataSet.setDrawFilled(true)

        val lineData = LineData(glucoseOneDataSet)
        glucoseChartThree.data = lineData
        glucoseChartThree.description.text = "Glucose Level over 3 hours"
        glucoseChartThree.xAxis.labelRotationAngle = -45f
        glucoseChartThree.animateX(1500)

        // Set Y-axis range and limit lines
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

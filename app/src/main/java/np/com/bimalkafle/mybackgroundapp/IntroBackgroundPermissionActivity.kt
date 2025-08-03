package np.com.bimalkafle.mybackgroundapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri

class IntroBackgroundPermissionActivity : AppCompatActivity() {
    private lateinit var powerManager: PowerManager

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_intro_background_permission)
        val grantPermissionButton: Button = findViewById(R.id.grantPermissionButton)
        powerManager = getSystemService(POWER_SERVICE) as PowerManager

        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            navigateToNextActivity()
        } else {
            grantPermissionButton.setOnClickListener {
                val intent = Intent()
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = "package:$packageName".toUri()
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            navigateToNextActivity()
        }
    }
    private fun navigateToNextActivity() {
        val intent = Intent(this, IntroPermissionsActivity::class.java)
        startActivity(intent)
        finish()
    }
}
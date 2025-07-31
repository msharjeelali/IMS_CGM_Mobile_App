package np.com.bimalkafle.mybackgroundapp

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class IntroBackgroundPermissionActivity : AppCompatActivity() {
    private lateinit var powerManager: PowerManager

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
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
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
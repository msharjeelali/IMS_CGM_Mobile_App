package np.com.bimalkafle.mybackgroundapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class IntroPermissionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_intro_other_permissions)
        val nextButton: Button = findViewById(R.id.nextButton)

        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstRun = sharedPref.getBoolean("isFirstRun", true)

        if (!isFirstRun) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        nextButton.setOnClickListener {
            val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
            with(sharedPref.edit()) {
                putBoolean("isFirstRun", false)
                apply()
            }
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
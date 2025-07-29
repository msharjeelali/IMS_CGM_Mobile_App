package np.com.bimalkafle.mybackgroundapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class IntroActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro) // This line inflates your XML layout

        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isFirstRun = sharedPref.getBoolean("isFirstRun", true)

        if (!isFirstRun) {
            val intent = Intent(this, BackgroundPermissionActivity::class.java)
            startActivity(intent)
            finish()
        }

        val nextButton: Button = findViewById(R.id.nextButton)
        nextButton.setOnClickListener {
            val intent = Intent(this, BackgroundPermissionActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
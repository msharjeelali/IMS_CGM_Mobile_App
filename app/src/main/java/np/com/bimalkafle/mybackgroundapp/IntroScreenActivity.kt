package np.com.bimalkafle.mybackgroundapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class IntroScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro_screen)

        val sharedPref = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstRun = sharedPref.getBoolean("isFirstRun", true)

        if (!isFirstRun) {
            val intent = Intent(this, IntroBackgroundPermissionActivity::class.java)
            startActivity(intent)
            finish()
        }

        val nextButton: Button = findViewById(R.id.nextButton)
        nextButton.setOnClickListener {
            val intent = Intent(this, IntroBackgroundPermissionActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
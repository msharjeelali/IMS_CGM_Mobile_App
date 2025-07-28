package np.com.bimalkafle.mybackgroundapp

import ManualInputDbHelper
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ViewEntriesActivity : AppCompatActivity() {

    private lateinit var dbHelper: ManualInputDbHelper
    private lateinit var entriesContainer: LinearLayout
    private lateinit var tvNoEntries: TextView
    private lateinit var tvMealCount: TextView
    private lateinit var tvInsulinCount: TextView
    private lateinit var tvSymptomCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_entries)

        dbHelper = ManualInputDbHelper(this)

        // Initialize views
        entriesContainer = findViewById(R.id.ll_entries_container)
        tvNoEntries = findViewById(R.id.tv_no_entries)
        tvMealCount = findViewById(R.id.tv_meal_count)
        tvInsulinCount = findViewById(R.id.tv_insulin_count)
        tvSymptomCount = findViewById(R.id.tv_symptom_count)

        loadEntries()
    }

    private fun loadEntries() {
        val db = dbHelper.readableDatabase

        try {
            val cursor = db.rawQuery("SELECT * FROM manual_inputs ORDER BY timestamp DESC", null)

            // Clear existing entries (except the "no entries" message)
            entriesContainer.removeAllViews()

            if (cursor.count == 0) {
                // Show no entries message
                entriesContainer.addView(tvNoEntries)
                updateStats(0, 0, 0)
            } else {
                // Hide no entries message and populate with data
                var mealCount = 0
                var insulinCount = 0
                var symptomCount = 0

                while (cursor.moveToNext()) {
                    val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                    val value = cursor.getString(cursor.getColumnIndexOrThrow("value"))
                    val timestamp = cursor.getString(cursor.getColumnIndexOrThrow("timestamp"))

                    // Count entries by type
                    when (type) {
                        "Meal" -> mealCount++
                        "Insulin" -> insulinCount++
                        "Symptom" -> symptomCount++
                    }

                    // Create entry card
                    createEntryCard(type, value, timestamp)
                }

                updateStats(mealCount, insulinCount, symptomCount)
            }

            cursor.close()

        } catch (e: Exception) {
            // Show error in a card
            createErrorCard("Error loading entries: ${e.message}")
        } finally {
            db.close()
        }
    }

    private fun createEntryCard(type: String, value: String, timestamp: String) {
        // Create main card layout
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@ViewEntriesActivity, android.R.color.white)
            setPadding(40, 30, 40, 30)

            // Set margins
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 24)
            layoutParams = params
        }

        // Create header layout for type and emoji
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }

        // Add emoji and type
        val emoji = when (type) {
            "Meal" -> "🍽️"
            "Insulin" -> "💉"
            "Symptom" -> "🩺"
            else -> "📝"
        }

        val typeText = TextView(this).apply {
            text = "$emoji $type"
            textSize = 18f
            setTextColor(Color.parseColor("#152037"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // Add timestamp
        val timeText = TextView(this).apply {
            text = formatTimestamp(timestamp)
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            gravity = android.view.Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        headerLayout.addView(typeText)
        headerLayout.addView(timeText)

        // Add value text
        val valueText = TextView(this).apply {
            text = value
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 8, 0, 0)
        }

        // Add divider line
        val divider = android.view.View(this).apply {
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            )
        }

        // Assemble card
        cardLayout.addView(headerLayout)
        cardLayout.addView(divider)
        cardLayout.addView(valueText)

        // Add to container
        entriesContainer.addView(cardLayout)
    }

    private fun createErrorCard(message: String) {
        val errorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFEBEE"))
            setPadding(40, 30, 40, 30)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 24)
            layoutParams = params
        }

        val errorText = TextView(this).apply {
            text = "⚠️ $message"
            textSize = 16f
            setTextColor(Color.parseColor("#C62828"))
        }

        errorCard.addView(errorText)
        entriesContainer.addView(errorCard)
    }

    private fun updateStats(meals: Int, insulin: Int, symptoms: Int) {
        tvMealCount.text = meals.toString()
        tvInsulinCount.text = insulin.toString()
        tvSymptomCount.text = symptoms.toString()
    }

    private fun formatTimestamp(timestamp: String): String {
        return try {
            // Extract just the time part (HH:mm) from "yyyy-MM-dd HH:mm:ss"
            val parts = timestamp.split(" ")
            if (parts.size >= 2) {
                val timePart = parts[1].substring(0, 5) // Get HH:mm
                val datePart = parts[0]
                "$timePart • $datePart"
            } else {
                timestamp
            }
        } catch (e: Exception) {
            timestamp
        }
    }
}
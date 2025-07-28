package np.com.bimalkafle.mybackgroundapp

import ManualInputDbHelper
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ManualInputActivity : AppCompatActivity() {

    private lateinit var spinnerType: Spinner
    private lateinit var etInput: EditText
    private lateinit var btnSave: Button
    private lateinit var dbHelper: ManualInputDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_input)

        spinnerType = findViewById(R.id.spinnerType)
        etInput = findViewById(R.id.etInput)
        btnSave = findViewById(R.id.btnSave)

        dbHelper = ManualInputDbHelper(this)
        // Insert dummy entries (ONLY FOR TESTING — remove later!)



        setupSpinner()
        setupSaveButton()
    }

    private fun setupSpinner() {
        val types = arrayOf("Select Type", "Meal", "Insulin", "Symptom")

        // Create custom adapter with proper text colors
        val adapter = object : ArrayAdapter<String>(this, R.layout.spinner_item, types) {

            override fun isEnabled(position: Int): Boolean {
                // Disable first item (placeholder)
                return position != 0
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view as TextView

                if (position == 0) {
                    // Placeholder text in gray
                    textView.setTextColor(Color.GRAY)
                } else {
                    // Regular items in dark color
                    textView.setTextColor(Color.parseColor("#152037"))
                }

                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view as TextView

                if (position == 0) {
                    // Placeholder text in gray
                    textView.setTextColor(Color.GRAY)
                } else {
                    // Regular items in dark color
                    textView.setTextColor(Color.parseColor("#152037"))
                }

                return view
            }
        }

        // Set dropdown layout
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)

        // Apply adapter to spinner
        spinnerType.adapter = adapter
    }

    private fun setupSaveButton() {
        btnSave.setOnClickListener {
            val selectedPosition = spinnerType.selectedItemPosition

            if (selectedPosition == 0) {
                Toast.makeText(this, "Please select a type", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val type = spinnerType.selectedItem.toString()
            val detail = etInput.text.toString().trim()

            if (detail.isNotEmpty()) {
                dbHelper.insertRecord(type, detail)

                Toast.makeText(this, "$type saved!", Toast.LENGTH_SHORT).show()
                etInput.text.clear()
                // Reset spinner to first item
                spinnerType.setSelection(0)
            } else {
                Toast.makeText(this, "Please enter details", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
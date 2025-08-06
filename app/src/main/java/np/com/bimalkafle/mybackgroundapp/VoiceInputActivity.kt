package np.com.bimalkafle.mybackgroundapp

import ManualInputDbHelper
import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class VoiceInputActivity : AppCompatActivity() {

    private lateinit var btnMicrophone: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnCancel: Button
    private lateinit var btnConfirm: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var tvResult: TextView
    private lateinit var cardResult: CardView
    private lateinit var pulseRingOuter: View
    private lateinit var pulseRingMiddle: View
    private lateinit var pulseRingInner: View

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private lateinit var dbHelper: ManualInputDbHelper
    private var isRecording = false
    private var recognizedText = ""
    private var parsedInsulinUnits: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_input)

        dbHelper = ManualInputDbHelper(this)
        initViews()
        setupSpeechRecognizer()
        setupClickListeners()
        startPulseAnimation()
    }

    private fun initViews() {
        btnMicrophone = findViewById(R.id.btn_microphone)
        btnBack = findViewById(R.id.btn_back)
        btnCancel = findViewById(R.id.btn_cancel)
        btnConfirm = findViewById(R.id.btn_confirm)
        tvStatus = findViewById(R.id.tv_status)
        tvInstruction = findViewById(R.id.tv_instruction)
        tvResult = findViewById(R.id.tv_result)
        cardResult = findViewById(R.id.card_result)
        pulseRingOuter = findViewById(R.id.pulse_ring_outer)
        pulseRingMiddle = findViewById(R.id.pulse_ring_middle)
        pulseRingInner = findViewById(R.id.pulse_ring_inner)
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your insulin dosage (e.g., 3 units, taking 5 units)")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateUI(RecordingState.LISTENING)
            }

            override fun onBeginningOfSpeech() {
                updateUI(RecordingState.SPEAKING)
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Update visual feedback based on voice level
                animatePulseIntensity(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                updateUI(RecordingState.PROCESSING)
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Recognition error"
                }
                updateUI(RecordingState.ERROR, errorMessage)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedText = matches[0]
                    // Show what was recognized for debugging
                    updateUI(RecordingState.PROCESSING, "Recognized: $recognizedText")
                    
                    parsedInsulinUnits = parseVoiceCommand(recognizedText)
                    
                    if (parsedInsulinUnits != null) {
                        // Store in database
                        dbHelper.insertRecord("Insulin", "${parsedInsulinUnits} units")
                        updateUI(RecordingState.SUCCESS, "Insulin: ${parsedInsulinUnits} units")
                        Toast.makeText(this@VoiceInputActivity, "Insulin dosage saved!", Toast.LENGTH_SHORT).show()
                    } else {
                        updateUI(RecordingState.ERROR, "Could not parse insulin dosage from: '$recognizedText'. Please try again.")
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun parseVoiceCommand(command: String): Int? {
        val lowerCommand = command.lowercase()
        
        // First, try to extract any number from the command
        val numberPattern = Regex("(\\d+)")
        val numberMatch = numberPattern.find(lowerCommand)
        
        if (numberMatch != null) {
            val units = numberMatch.groupValues[1].toIntOrNull()
            if (units != null && units > 0 && units <= 100) { // Reasonable range
                return units
            }
        }
        
        // If no number found, try to convert written numbers
        val writtenNumbers = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
            "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
            "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20
        )
        
        for ((word, number) in writtenNumbers) {
            if (lowerCommand.contains(word)) {
                return number
            }
        }
        
        return null
    }

    private fun setupClickListeners() {
        btnMicrophone.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        btnBack.setOnClickListener {
            finish()
            // Remove animation if you don't have anim folder
            // overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        btnCancel.setOnClickListener {
            finish()
            // Remove animation if you don't have anim folder
            // overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        btnConfirm.setOnClickListener {
            if (parsedInsulinUnits != null) {
                // Already saved in database, just return
                val resultIntent = Intent().apply {
                    putExtra("insulin_input", "${parsedInsulinUnits} units")
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, "No valid insulin dosage detected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE)
            return
        }

        isRecording = true
        speechRecognizer.startListening(speechRecognizerIntent)
        updateUI(RecordingState.INITIALIZING)
    }

    private fun stopRecording() {
        isRecording = false
        speechRecognizer.stopListening()
        updateUI(RecordingState.IDLE)
    }

    private fun updateUI(state: RecordingState, message: String = "") {
        runOnUiThread {
            when (state) {
                RecordingState.IDLE -> {
                    tvStatus.text = "Tap to start recording"
                    tvInstruction.text = "Say your insulin dosage (e.g., '3 units', 'taking 5 units')"
                    btnMicrophone.setImageResource(R.drawable.ic_microphone)
                    cardResult.visibility = View.GONE
                    btnConfirm.visibility = View.GONE
                    stopPulseAnimation()
                }
                RecordingState.INITIALIZING -> {
                    tvStatus.text = "Initializing..."
                    btnMicrophone.setImageResource(R.drawable.ic_microphone_active)
                    startIntensePulseAnimation()
                }
                RecordingState.LISTENING -> {
                    tvStatus.text = "Listening..."
                    btnMicrophone.setImageResource(R.drawable.ic_microphone_active)
                    startIntensePulseAnimation()
                }
                RecordingState.SPEAKING -> {
                    tvStatus.text = "Speaking detected"
                    startVoiceActivePulseAnimation()
                }
                RecordingState.PROCESSING -> {
                    tvStatus.text = "Processing..."
                    stopPulseAnimation()
                }
                RecordingState.SUCCESS -> {
                    tvStatus.text = "Recognition complete"
                    tvResult.text = message
                    cardResult.visibility = View.VISIBLE
                    btnConfirm.visibility = View.VISIBLE
                    btnMicrophone.setImageResource(R.drawable.ic_check)
                    stopPulseAnimation()
                }
                RecordingState.ERROR -> {
                    tvStatus.text = "Error: $message"
                    btnMicrophone.setImageResource(R.drawable.ic_microphone)
                    stopPulseAnimation()
                }
            }
        }
    }

    private fun startPulseAnimation() {
        val pulseAnimator = ObjectAnimator.ofFloat(pulseRingOuter, "alpha", 0.3f, 0.7f).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        pulseAnimator.start()
    }

    private fun startIntensePulseAnimation() {
        // Animate all pulse rings
        listOf(pulseRingOuter, pulseRingMiddle, pulseRingInner).forEachIndexed { index, ring ->
            val scaleAnimator = ObjectAnimator.ofPropertyValuesHolder(
                ring,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.2f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.2f),
                PropertyValuesHolder.ofFloat("alpha", 0.7f, 0.3f)
            ).apply {
                duration = 800 + (index * 200L)
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
            }
            scaleAnimator.start()
        }
    }

    private fun startVoiceActivePulseAnimation() {
        // Rapid pulse animation when voice is detected
        val rapidPulse = ObjectAnimator.ofFloat(pulseRingInner, "alpha", 0.8f, 0.2f).apply {
            duration = 300
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        rapidPulse.start()
    }

    private fun stopPulseAnimation() {
        listOf(pulseRingOuter, pulseRingMiddle, pulseRingInner).forEach { ring ->
            ring.clearAnimation()
            ring.alpha = 0.3f
            ring.scaleX = 1f
            ring.scaleY = 1f
        }
    }

    private fun animatePulseIntensity(rmsdB: Float) {
        // Convert RMS dB to scale factor (0-10 dB range)
        val normalizedLevel = (rmsdB + 2f) / 12f
        val scaleFactor = 1f + (normalizedLevel * 0.3f)

        pulseRingInner.scaleX = scaleFactor
        pulseRingInner.scaleY = scaleFactor
        pulseRingInner.alpha = 0.3f + (normalizedLevel * 0.5f)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 1001
    }

    enum class RecordingState {
        IDLE, INITIALIZING, LISTENING, SPEAKING, PROCESSING, SUCCESS, ERROR
    }
}
package np.com.bimalkafle.mybackgroundapp

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class STTActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var intentRecognizer: Intent
    private lateinit var tts: TextToSpeech
    private lateinit var dbHelper: GlucoseDbHelper
    private lateinit var tvResult: TextView
    private lateinit var btnStart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stt)

        tvResult = findViewById(R.id.tvResult)
        btnStart = findViewById(R.id.btnStart)

        // ✅ Using correct DB Helper
        dbHelper = GlucoseDbHelper(this)

        // ❌ REMOVE this in production (for testing only)
        // dbHelper.insertLevel(150, "2025-07-27 13:00:00")

        // ✅ Text-to-Speech
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
            }
        }

        // ✅ Speech Recognizer setup
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        intentRecognizer = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.get(0) ?: ""
                tvResult.text = spokenText

                if (spokenText.lowercase().contains("glucose")) {
                    val level = dbHelper.getLatestLevel()
                    val response = if (level != null) {
                        "Your glucose level is $level milligrams per deciliter."
                    } else {
                        "I could not find any glucose data."
                    }
                    tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
                } else {
                    tts.speak("Sorry, I didn't understand. Try saying 'What is my glucose level?'", TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {
                tvResult.text = "Listening..."
            }

            override fun onError(error: Int) {
                tvResult.text = "Error: $error"
            }

            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {}
        })

        btnStart.setOnClickListener {
            tvResult.text = ""
            speechRecognizer.startListening(intentRecognizer)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
        speechRecognizer.destroy()
    }
}

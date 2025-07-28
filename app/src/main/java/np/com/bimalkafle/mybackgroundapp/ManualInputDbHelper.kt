import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.*

class ManualInputDbHelper(context: Context) :
    SQLiteOpenHelper(context, "ManualInputDB", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
        CREATE TABLE IF NOT EXISTS manual_inputs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            type TEXT NOT NULL,
            value TEXT NOT NULL,
            timestamp TEXT NOT NULL
        );
    """.trimIndent()

        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS manual_inputs")
        onCreate(db)
    }

    fun insertRecord(type: String, detail: String) {
        val db = writableDatabase

        // Generate current timestamp
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())

        val values = ContentValues().apply {
            put("type", type)
            put("value", detail)  // Changed from "detail" to "value"
            put("timestamp", currentTime)  // Add timestamp
        }

        // Use correct table name "manual_inputs"
        val result = db.insert("manual_inputs", null, values)
        db.close()

        // Optional: Log success/failure
        if (result == -1L) {
            android.util.Log.e("DB_INSERT", "Failed to insert record")
        } else {
            android.util.Log.d("DB_INSERT", "Record inserted successfully with ID: $result")
        }
    }
}
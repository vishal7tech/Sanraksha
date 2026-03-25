package com.example.sanraksha
import androidx.core.content.edit
import android.Manifest
import android.content.Context
import android.content.Intent
import android.util.Log
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaPlayer

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognizerIntent
import android.telephony.PhoneNumberUtils
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.core.app.NotificationCompat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.SharedPreferences

class MainActivity : AppCompatActivity() {

    private lateinit var switchSafetyMode: SwitchMaterial
    private lateinit var switchSos: SwitchMaterial
    private lateinit var editTextKeyword: TextInputEditText
    private lateinit var buttonTriggerSos: Button
    private lateinit var textStatus: TextView
    private lateinit var buttonContacts: Button
    private lateinit var buttonSettings: Button
    private lateinit var buttonVoiceListen: Button
    private lateinit var buttonCancelSos: Button
    private lateinit var sensorManager: SensorManager
    private var accel = 0f
    private var accelCurrent = 0f
    private var accelLast = 0f

    private var isSafetyModeActive = false
    private var isSosActive = false
    private var isListening = false
    private var countdown = 30  // 30 seconds cancel timer
    private var countdownHandler: Handler? = null
    private var countdownRunnable: Runnable? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mediaPlayer: MediaPlayer? = null

    private val REQUEST_PERMISSIONS = 1001
    private val KEYWORDS = listOf("help help", "bacho bacho", "हेल्प हेल्प", "बचो बचो")

    private val speechResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (isListening) {
            Handler(Looper.getMainLooper()).postDelayed({
                startVoiceListeningLoop()
            }, 1500)
        }

        if (result.resultCode == RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.let { text ->
                if (KEYWORDS.any { it.equals(text.trim(), ignoreCase = true) }) {
                    // 🔥 Trigger SOS from voice
                    if (!isSosActive && isSafetyModeActive) {
                        switchSos.isChecked = true
                    } else if (!isSafetyModeActive) {
                        Toast.makeText(this@MainActivity, "Please enable Safety Mode first", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        switchSafetyMode = findViewById(R.id.switchSafetyMode)
        switchSos = findViewById(R.id.switchSos)
        editTextKeyword = findViewById(R.id.editTextKeyword)
        buttonTriggerSos = findViewById(R.id.buttonTriggerSos)
        textStatus = findViewById(R.id.textStatus)
        buttonContacts = findViewById(R.id.buttonContacts)
        buttonSettings = findViewById(R.id.buttonSettings)
        buttonVoiceListen = findViewById(R.id.buttonVoiceListen)
        buttonCancelSos = findViewById(R.id.buttonCancelSos)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initially disable SOS switch until Safety Mode is enabled
        switchSos.isEnabled = false

        // Request permissions
        requestPermissions()

        // Shake sensor setup
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(sensorListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_UI)

        accel = 10f
        accelCurrent = SensorManager.GRAVITY_EARTH
        accelLast = SensorManager.GRAVITY_EARTH

        // Check for queued SMS
        sendQueuedSms()

        // Safety Mode Toggle
        switchSafetyMode.setOnCheckedChangeListener { _, isChecked ->
            isSafetyModeActive = isChecked
            if (isChecked) {
                // Enable SOS switch when Safety Mode is on
                switchSos.isEnabled = true
                startForegroundService() // ← Start service
            } else {
                // Disable SOS switch and turn it off when Safety Mode is off
                switchSos.isEnabled = false
                switchSos.isChecked = false
                stopForegroundService() // ← Stop service
            }
        }

        // SOS Mode Toggle - Make it dependent on Safety Mode
        switchSos.setOnCheckedChangeListener { _, isChecked ->
            if (!isSafetyModeActive) {
                // If Safety Mode is not active, don't allow SOS to be enabled
                switchSos.isChecked = false
                Toast.makeText(this, "Please enable Safety Mode first", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }

            isSosActive = isChecked
            if (isChecked) {
                startSosMode()  // This handles alarm, SMS, vibration
            } else {
                stopSosMode()
            }
        }

        val buttonProfile = findViewById<Button>(R.id.buttonProfile)
        buttonProfile.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }

        // Manual SOS Trigger (Button)
        buttonTriggerSos.setOnClickListener {
            if (!isSafetyModeActive) {
                Toast.makeText(this, "Please enable Safety Mode first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val keyword = editTextKeyword.text.toString().trim().lowercase()
            if (isSosActive || keyword in KEYWORDS) {
                if (!isSosActive) {
                    switchSos.isChecked = true
                }
            } else {
                Toast.makeText(this, getString(R.string.enable_sos_or_keywords), Toast.LENGTH_SHORT).show()
            }
        }

        // Voice Listening Button (Manual)
        buttonVoiceListen.setOnClickListener {
            if (!isSafetyModeActive) {
                Toast.makeText(this, "Please enable Safety Mode first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startVoiceRecognition()
        }

        // Open Contacts Screen
        buttonContacts.setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }

        // Open Settings
        buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Cancel SOS Button
        buttonCancelSos.setOnClickListener {
            cancelSos()
        }

        // Register broadcast receiver for SOS keyword
        val filter = IntentFilter().apply {
            addAction("START_VOICE_LISTENING")
            addAction("STOP_VOICE_LISTENING")
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "START_VOICE_LISTENING" -> startVoiceListeningLoop()
                    "STOP_VOICE_LISTENING" -> isListening = false
                }
            }
        }, filter)

        // Debug: Check contacts on startup
        debugContacts()
    }

    private val sensorListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            val x = event?.values?.get(0) ?: 0f
            val y = event?.values?.get(1) ?: 0f
            val z = event?.values?.get(2) ?: 0f

            accelLast = accelCurrent
            accelCurrent = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = accelCurrent - accelLast
            accel = accel * 0.9f + delta // low-pass filter

            if (accel > 12) { // sensitivity threshold
                if (isSafetyModeActive && !isSosActive) {
                    switchSos.isChecked = true
                    Toast.makeText(this@MainActivity, "Shake detected! SOS Activated", Toast.LENGTH_SHORT).show()
                } else if (!isSafetyModeActive) {
                    Toast.makeText(this@MainActivity, "Shake detected! Please enable Safety Mode first", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun startForegroundService() {
        val serviceIntent = Intent(this, SosForegroundService::class.java)
        serviceIntent.action = "START_LISTENING"
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopForegroundService() {
        val serviceIntent = Intent(this, SosForegroundService::class.java)
        serviceIntent.action = "STOP_LISTENING"
        stopService(serviceIntent)
    }

    private fun startVoiceListeningLoop() {
        if (!isListening || isSosActive) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
        }

        speechResultLauncher.launch(intent)
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_prompt))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechResultLauncher.launch(intent)
    }

    private fun startSosMode() {
        countdown = 30  // Reset to 30 seconds
        isSosActive = true

        playSosSound()
        startVibration()
        updateStatusText(getString(R.string.sos_active), R.color.red)

        buttonCancelSos.visibility = View.VISIBLE
        buttonCancelSos.text = getString(R.string.cancel_sos, countdown)
        startCancelCountdown()

        // Start listening (for accidental cancellation)
        if (!isListening) {
            isListening = true
            startVoiceListeningLoop()
        }
    }

    private fun stopSosMode() {
        isSosActive = false
        isListening = false
        stopSosSound()
        stopVibration()
        updateStatusText(getString(R.string.sos_off), R.color.green)
        buttonCancelSos.visibility = View.GONE

        // Stop the countdown
        countdownHandler?.removeCallbacks(countdownRunnable!!)
    }

    private fun cancelSos() {
        // Stop the SOS mode immediately
        isSosActive = false
        stopSosSound()
        stopVibration()
        updateStatusText(getString(R.string.sos_cancelled), R.color.green)
        buttonCancelSos.visibility = View.GONE

        // Also turn off the SOS switch but keep Safety Mode as is
        switchSos.isChecked = false

        // Stop the countdown
        countdownHandler?.removeCallbacks(countdownRunnable!!)

        Toast.makeText(this, getString(R.string.sos_cancelled_toast), Toast.LENGTH_SHORT).show()
    }

    private fun playSosSound() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.sos_alert)
            mediaPlayer?.isLooping = true
        }
        mediaPlayer?.start()
    }

    private fun stopSosSound() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = android.os.VibrationEffect.createOneShot(5000, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(5000)
        }
    }

    private fun stopVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.cancel()
    }

    private fun startCancelCountdown() {
        countdownHandler = Handler(Looper.getMainLooper())
        countdownRunnable = object : Runnable {
            override fun run() {
                countdown--
                if (countdown > 0 && isSosActive) {
                    buttonCancelSos.text = getString(R.string.cancel_sos, countdown)
                    // Update status to show remaining time
                    updateStatusText(getString(R.string.sos_waiting, countdown), R.color.red)
                    countdownHandler?.postDelayed(this, 1000)
                } else if (isSosActive && countdown <= 0) {
                    // Time's up, send SMS
                    sendSosAlert()
                    buttonCancelSos.visibility = View.GONE
                } else {
                    buttonCancelSos.visibility = View.GONE
                }
            }
        }
        countdownHandler?.post(countdownRunnable!!)
    }

    private fun getEmergencyNumbers(): List<String> {
        val sp = getSharedPreferences("EmergencyContacts", Context.MODE_PRIVATE)
        val contacts = mutableListOf<String>()

        for (i in 1..3) {
            val phone = sp.getString("phone$i", "")?.trim()
            if (!phone.isNullOrEmpty()) {
                // Clean phone number - remove any non-digit characters except +
                val cleanedPhone = phone.replace(Regex("[^\\d+]"), "")
                if (cleanedPhone.length >= 10) {
                    contacts.add(cleanedPhone)
                    Log.d("EmergencyContacts", "Loaded contact $i: $cleanedPhone")
                }
            }
        }

        Log.d("EmergencyContacts", "Total contacts loaded: ${contacts.size}")
        return contacts
    }

    private fun sendSosAlert() {
        // First check SMS permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            showError("SMS permission not granted")
            return
        }

        updateStatusText(getString(R.string.sending_sos), R.color.red)
        Toast.makeText(this, getString(R.string.fetching_location), Toast.LENGTH_SHORT).show()

        // Get emergency contacts first
        val contacts = getEmergencyNumbers()
        if (contacts.isEmpty()) {
            showError("No emergency contacts set. Please add contacts first.")
            return
        }

        // Create base message without location
        val profilePref = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)
        val name = profilePref.getString("name", "Someone") ?: "Someone"
        val gender = when (profilePref.getString("gender", "") ?: "") {
            "M" -> "Male"
            "F" -> "Female"
            else -> "N/A"
        }
        val blood = profilePref.getString("blood_group", "") ?: "Unknown"
        val medical = profilePref.getString("medical", "")
        val medicalInfo = if (!medical.isNullOrEmpty()) "Medical: $medical. " else ""

        var baseMessage = "🆘 EMERGENCY! I need help!\n" +
                "From: $name ($gender, Blood: $blood)\n" +
                medicalInfo

        // Try to get location with timeout
        val locationTimeout = Handler(Looper.getMainLooper())
        val locationRunnable = Runnable {
            // Location timed out - send without location
            val noLocationMessage = baseMessage + "\nLocation: Could not determine - please check app"
            sendToAllContacts(contacts, noLocationMessage)
        }
        locationTimeout.postDelayed(locationRunnable, 10000) // 10 second timeout

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                locationTimeout.removeCallbacks(locationRunnable) // Cancel timeout

                if (location != null) {
                    val lat = "%.6f".format(location.latitude)
                    val lng = "%.6f".format(location.longitude)
                    val mapLink = "https://www.google.com/maps?q=$lat,$lng"
                    val locationMessage = baseMessage + "\nMy location: $mapLink\n$lat, $lng"
                    sendToAllContacts(contacts, locationMessage)
                } else {
                    // No location available
                    val noLocationMessage = baseMessage + "\nLocation: Not available - please check app"
                    sendToAllContacts(contacts, noLocationMessage)
                }
            }.addOnFailureListener { e ->
                locationTimeout.removeCallbacks(locationRunnable)
                showError("Location error: ${e.message}")
                val errorMessage = baseMessage + "\nLocation: Error - please check app"
                sendToAllContacts(contacts, errorMessage)
            }
        } else {
            locationTimeout.removeCallbacks(locationRunnable)
            val noPermMessage = baseMessage + "\nLocation: Permission denied - please check app"
            sendToAllContacts(contacts, noPermMessage)
        }
    }

    private fun sendToAllContacts(contacts: List<String>, message: String) {
        val smsManager = SmsManager.getDefault()
        var sentCount = 0
        var failedCount = 0

        Log.d("SMS", "Attempting to send to ${contacts.size} contacts")

        for (phone in contacts) {
            try {
                Log.d("SMS", "Sending to: $phone")

                // Split long messages into parts if needed
                if (message.length > 160) {
                    val parts = smsManager.divideMessage(message)
                    smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(phone, null, message, null, null)
                }
                sentCount++
                Log.d("SMS", "Successfully sent to $phone")

                // Show individual success toast
                runOnUiThread {
                    Toast.makeText(this, "SOS sent to $phone", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                failedCount++
                Log.e("SMS", "Failed to send to $phone: ${e.message}", e)
                queueSms(phone, message)

                runOnUiThread {
                    Toast.makeText(this, "Failed to send to $phone", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Final summary
        runOnUiThread {
            if (sentCount > 0) {
                Toast.makeText(this, "SOS sent to $sentCount contacts", Toast.LENGTH_LONG).show()
                updateStatusText("SOS sent to $sentCount contacts", R.color.green)
            }
            if (failedCount > 0) {
                Toast.makeText(this, "Failed to send to $failedCount contacts", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendSms(phoneNumber: String, message: String) {
        try {
            Log.d("SMS", "Attempting to send to $phoneNumber: $message")
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d("SMS", "Successfully sent to $phoneNumber")
            Toast.makeText(this, "SOS SMS sent to $phoneNumber", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SMS", "Failed to send to $phoneNumber", e)
            queueSms(phoneNumber, message)
            Toast.makeText(this, "SMS failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun debugContacts() {
        val sp = getSharedPreferences("EmergencyContacts", Context.MODE_PRIVATE)
        for (i in 1..3) {
            val phone = sp.getString("phone$i", "") ?: ""
            val name = sp.getString("name$i", "") ?: ""
            Log.d("ContactsDebug", "Contact $i - Name: $name, Phone: $phone")
        }
    }

    private fun queueSms(phoneNumber: String, message: String) {
        val sp = getSharedPreferences("SmsQueue", Context.MODE_PRIVATE)
        sp.edit {
            val queue = sp.getStringSet("queue", setOf())?.toMutableList() ?: mutableListOf()
            queue.add("$phoneNumber||$message")
            putStringSet("queue", queue.toSet())
        }
    }

    private fun sendQueuedSms() {
        val sp = getSharedPreferences("SmsQueue", Context.MODE_PRIVATE)
        val queue = sp.getStringSet("queue", setOf())?.toMutableList() ?: return
        for (item in queue) {
            val parts = item.split("||")
            if (parts.size == 2) {
                sendSms(parts[0], parts[1])
            }
        }
        sp.edit().remove("queue").apply()
    }

    private fun showError(msg: String) {
        updateStatusText("Error: $msg", R.color.red)
        Toast.makeText(this, "Error: $msg", Toast.LENGTH_LONG).show()
    }

    private fun updateStatusText(text: String, colorResId: Int) {
        textStatus.text = text
        textStatus.setTextColor(ContextCompat.getColor(this, colorResId))
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.VIBRATE
        )
        val needsPermission = permissions.any { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (needsPermission) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS)
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        val langPref = newBase?.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val lang = langPref?.getString("language", "en") ?: "en"
        val wrappedContext = MyContextWrapper.wrap(newBase, lang)
        super.attachBaseContext(wrappedContext)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorListener)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(sensorListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_UI)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, getString(R.string.permissions_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.permissions_denied), Toast.LENGTH_LONG).show()
                // Check if SMS permission was denied
                val smsDenied = permissions.indexOf(Manifest.permission.SEND_SMS).let { index ->
                    index != -1 && grantResults[index] != PackageManager.PERMISSION_GRANTED
                }
                if (smsDenied) {
                    Toast.makeText(this, "SMS permission is required for SOS feature", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

package com.example.sanraksha

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast

class SettingsActivity : AppCompatActivity() {

    private lateinit var radioGroup: RadioGroup
    private lateinit var radioEnglish: RadioButton
    private lateinit var radioHindi: RadioButton
    private lateinit var buttonSave: Button

    private lateinit var sharedPreferences: SharedPreferences

    override fun attachBaseContext(newBase: Context?) {
        val langPref = newBase?.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val lang = langPref?.getString("language", "en") ?: "en"
        val wrappedContext = MyContextWrapper.wrap(newBase, lang)
        super.attachBaseContext(wrappedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        radioGroup = findViewById(R.id.radioGroupLanguage)
        radioEnglish = findViewById(R.id.radioEnglish)
        radioHindi = findViewById(R.id.radioHindi)
        buttonSave = findViewById(R.id.buttonSaveLang)

        sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)

        // Load saved language
        val lang = sharedPreferences.getString("language", "en")
        if (lang == "hi") {
            radioHindi.isChecked = true
        } else {
            radioEnglish.isChecked = true
        }

        buttonSave.setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId
            val langCode = if (selectedId == R.id.radioHindi) "hi" else "en"

            val editor = sharedPreferences.edit()
            editor.putString("language", langCode)
            editor.apply()

            Toast.makeText(this, "Language saved!", Toast.LENGTH_SHORT).show()

            // Restart the app to apply language changes
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }



    }
}

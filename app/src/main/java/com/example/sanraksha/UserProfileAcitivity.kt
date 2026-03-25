package com.example.sanraksha

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class UserProfileActivity : AppCompatActivity() {

    private lateinit var editTextName: EditText
    private lateinit var radioGroupGender: RadioGroup
    private lateinit var radioMale: RadioButton
    private lateinit var radioFemale: RadioButton
    private lateinit var radioOther: RadioButton
    private lateinit var editTextBloodGroup: EditText
    private lateinit var editTextMedical: EditText
    private lateinit var buttonSave: Button
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        // Bind views
        editTextName = findViewById(R.id.editTextName)
        radioGroupGender = findViewById(R.id.radioGroupGender)
        radioMale = findViewById(R.id.radioMale)
        radioFemale = findViewById(R.id.radioFemale)
        radioOther = findViewById(R.id.radioOther)
        editTextBloodGroup = findViewById(R.id.editTextBloodGroup)
        editTextMedical = findViewById(R.id.editTextMedical)
        buttonSave = findViewById(R.id.buttonSaveProfile)

        sharedPreferences = getSharedPreferences("UserProfile", Context.MODE_PRIVATE)

        // Load saved profile
        loadProfile()

        buttonSave.setOnClickListener {
            saveProfile()
            Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveProfile() {
        val editor = sharedPreferences.edit()

        editor.putString("name", editTextName.text.toString().trim())

        val gender = when (radioGroupGender.checkedRadioButtonId) {
            R.id.radioMale -> "M"
            R.id.radioFemale -> "F"
            R.id.radioOther -> "Other"
            else -> ""
        }
        editor.putString("gender", gender)
        editor.putString("blood_group", editTextBloodGroup.text.toString().trim())
        editor.putString("medical", editTextMedical.text.toString().trim())

        editor.apply()
        Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show()
        finish() // Close UserProfileActivity and return to MainActivity
    }

    private fun loadProfile() {
        val name = sharedPreferences.getString("name", "")
        val gender = sharedPreferences.getString("gender", "")
        val bloodGroup = sharedPreferences.getString("blood_group", "")
        val medical = sharedPreferences.getString("medical", "")

        editTextName.setText(name)
        when (gender) {
            "M" -> radioMale.isChecked = true
            "F" -> radioFemale.isChecked = true
            "Other" -> radioOther.isChecked = true
        }
        editTextBloodGroup.setText(bloodGroup)
        editTextMedical.setText(medical)
    }

    override fun attachBaseContext(newBase: Context?) {
        val langPref = newBase?.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val lang = langPref?.getString("language", "en") ?: "en"
        super.attachBaseContext(MyContextWrapper.wrap(newBase, lang))
    }
}


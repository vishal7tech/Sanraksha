package com.example.sanraksha

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class ContactsActivity : AppCompatActivity() {

    private lateinit var editContact1Name: TextInputEditText
    private lateinit var editContact1Phone: TextInputEditText
    private lateinit var editContact2Name: TextInputEditText
    private lateinit var editContact2Phone: TextInputEditText
    private lateinit var editContact3Name: TextInputEditText
    private lateinit var editContact3Phone: TextInputEditText
    private lateinit var buttonSave: Button

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)

        // Initialize views
        editContact1Name = findViewById(R.id.editContact1Name)
        editContact1Phone = findViewById(R.id.editContact1Phone)
        editContact2Name = findViewById(R.id.editContact2Name)
        editContact2Phone = findViewById(R.id.editContact2Phone)
        editContact3Name = findViewById(R.id.editContact3Name)
        editContact3Phone = findViewById(R.id.editContact3Phone)
        buttonSave = findViewById(R.id.buttonSaveContacts)

        sharedPreferences = getSharedPreferences("EmergencyContacts", Context.MODE_PRIVATE)

        loadContacts()
        buttonSave.setOnClickListener { saveContacts() }
    }

    private fun loadContacts() {
        editContact1Name.setText(sharedPreferences.getString("name1", ""))
        editContact1Phone.setText(sharedPreferences.getString("phone1", ""))
        editContact2Name.setText(sharedPreferences.getString("name2", ""))
        editContact2Phone.setText(sharedPreferences.getString("phone2", ""))
        editContact3Name.setText(sharedPreferences.getString("name3", ""))
        editContact3Phone.setText(sharedPreferences.getString("phone3", ""))
    }

    private fun saveContacts() {
        val editor = sharedPreferences.edit()
        editor.putString("name1", editContact1Name.text.toString())
        editor.putString("phone1", editContact1Phone.text.toString())
        editor.putString("name2", editContact2Name.text.toString())
        editor.putString("phone2", editContact2Phone.text.toString())
        editor.putString("name3", editContact3Name.text.toString())
        editor.putString("phone3", editContact3Phone.text.toString())
        editor.apply()
        Toast.makeText(this, "Contacts saved successfully!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
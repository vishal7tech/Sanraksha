package com.example.sanraksha

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object MyContextWrapper {
    fun wrap(context: Context?, language: String): Context {
        val config = Configuration(context?.resources?.configuration)
        config.setLocale(Locale(language))
        return context?.createConfigurationContext(config) ?: context!!
    }
}

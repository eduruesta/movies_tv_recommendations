package com.safepal.agent.utils

import android.content.Context
import java.util.Locale

object RegionUtils {
    
    fun getRegionAndLanguage(context: Context): Pair<String, String> {
        val locale = Locale.getDefault()
        
        val country = when (locale.country.uppercase()) {
            "AR" -> "AR" // Argentina
            "MX" -> "MX" // Mexico
            "ES" -> "ES" // Spain
            "CO" -> "CO" // Colombia
            "CL" -> "CL" // Chile
            "PE" -> "PE" // Peru
            "VE" -> "VE" // Venezuela
            "EC" -> "EC" // Ecuador
            "BO" -> "BO" // Bolivia
            "PY" -> "PY" // Paraguay
            "UY" -> "UY" // Uruguay
            "CR" -> "CR" // Costa Rica
            "PA" -> "PA" // Panama
            "GT" -> "GT" // Guatemala
            "HN" -> "HN" // Honduras
            "SV" -> "SV" // El Salvador
            "NI" -> "NI" // Nicaragua
            "DO" -> "DO" // Dominican Republic
            "CU" -> "CU" // Cuba
            "PR" -> "PR" // Puerto Rico
            else -> "US" // Default to US for other countries
        }
        
        val language = when (locale.language.lowercase()) {
            "es" -> "es-$country"
            "pt" -> if (country == "BR") "pt-BR" else "pt-$country"
            else -> "en-US"
        }
        
        return Pair(country, language)
    }
    
    fun isSpanishSpeakingRegion(country: String): Boolean {
        return country in listOf(
            "AR", "MX", "ES", "CO", "CL", "PE", "VE", "EC", 
            "BO", "PY", "UY", "CR", "PA", "GT", "HN", "SV", 
            "NI", "DO", "CU", "PR"
        )
    }
}
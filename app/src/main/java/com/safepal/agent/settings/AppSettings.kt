package com.safepal.agent.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

// Create DataStore as extension property for singleton behavior
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Serializable
data class Settings(
    val openAiToken: String = ""
)

class AppSettings(private val context: Context) {
    
    companion object {
        private val OPENAI_TOKEN_KEY = stringPreferencesKey("openai_token")
    }
    
    suspend fun getCurrentSettings(): Settings {
        return context.dataStore.data.map { preferences ->
            Settings(
                openAiToken = preferences[OPENAI_TOKEN_KEY] ?: ""
            )
        }.first()
    }
    
    suspend fun updateOpenAiToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[OPENAI_TOKEN_KEY] = token
        }
    }
}
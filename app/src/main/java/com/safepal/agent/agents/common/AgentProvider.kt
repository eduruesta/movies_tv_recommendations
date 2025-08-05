package com.safepal.agent.agents.common

import ai.koog.agents.core.agent.AIAgent
import com.safepal.agent.settings.AppSettings

interface AgentProvider {
    val title: String
    val description: String

    suspend fun provideAgent(
        appSettings: AppSettings,
        onToolCallEvent: suspend (String) -> Unit,
        onErrorEvent: suspend (String) -> Unit,
        onAssistantMessage: suspend (String) -> String
    ): AIAgent<String, String>
}
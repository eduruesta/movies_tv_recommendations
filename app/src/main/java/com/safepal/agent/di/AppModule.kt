package com.safepal.agent.di

import com.safepal.agent.agents.common.AgentProvider
import com.safepal.agent.agents.movie.MovieAgentProvider
import com.safepal.agent.api.TMDBClient
import com.safepal.agent.settings.AppSettings
import com.safepal.agent.ui.MovieRecommendationViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Settings
    single { AppSettings(androidContext()) }

    // TMDB Client
    single { TMDBClient("ce2eb742633db1119130842dff34c3eb") }

    // Agent Provider
    single { MovieAgentProvider }

    // ViewModels
    viewModel {
        MovieRecommendationViewModel(
            application = androidContext().applicationContext as android.app.Application,
            agentProvider = get()
        )
    }
}
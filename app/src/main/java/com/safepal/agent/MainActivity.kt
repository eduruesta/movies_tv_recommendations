package com.safepal.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.safepal.agent.ui.screens.MovieRecommendationScreen
import com.safepal.agent.ui.screens.SettingsScreen
import com.safepal.agent.ui.screens.DetailScreen
import com.safepal.agent.ui.theme.SafePalAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SafePalAgentTheme {
                MovieRecommendationApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieRecommendationApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MovieRecommendationScreen(
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToDetail = { movieId, movieType ->
                    navController.navigate("detail/$movieId/$movieType")
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            "detail/{movieId}/{movieType}",
            arguments = listOf(
                navArgument("movieId") { type = NavType.IntType },
                navArgument("movieType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getInt("movieId") ?: 0
            val movieType = backStackEntry.arguments?.getString("movieType") ?: "movie"
            
            DetailScreen(
                movieId = movieId,
                movieType = movieType,
                onBackClick = {
                    navController.popBackStack()
                },
                onMovieClick = { id, type ->
                    navController.navigate("detail/$id/$type") {
                        // Replace current detail screen
                        popUpTo("detail/$movieId/$movieType") { inclusive = true }
                    }
                }
            )
        }
    }
}
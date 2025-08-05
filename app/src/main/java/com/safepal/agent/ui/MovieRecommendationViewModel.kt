package com.safepal.agent.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safepal.agent.agents.common.AgentProvider
import com.safepal.agent.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import com.safepal.agent.agents.movie.MovieAgentProvider
import java.util.Locale

fun extractYear(dateString: String?): String? {
    return dateString?.take(4)  // Extract first 4 characters (year) from date
}

sealed class Message {
    data class UserMessage(val text: String) : Message()
    data class AgentMessage(val text: String) : Message()
    data class SystemMessage(val text: String) : Message()
    data class ErrorMessage(val text: String) : Message()
    data class ResultMessage(val text: String) : Message()
    data class MovieRecommendationsMessage(val movies: List<ContentRecommendation>, val message: String, val composeCode: String? = null, val uiType: String? = null) : Message()
    data class MovieDetailsMessage(val movieDetails: MovieDetails, val message: String) : Message()
}

// Data classes for JSON response parsing
@Serializable
data class MovieAgentResponse(
    val type: String,
    val message: String,
    val platforms: List<PlatformData>? = null,
    val movies: List<MovieData>? = null,
    val movie: MovieData? = null,
    val similar: List<MovieData>? = null,
    val recommendations: List<ContextualMovieData>? = null, // Para contextual_recommendations
    @SerialName("context_analysis") val contextAnalysis: String? = null, // Para contextual_recommendations
    val composeCode: String? = null,
    val uiType: String? = null
)

@Serializable
data class PlatformData(
    val id: Int,
    val name: String,
    @SerialName("logo_url") val logoUrl: String? = null
)

@Serializable
data class MovieData(
    val id: Int,
    val title: String,
    val overview: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("vote_average") val voteAverage: Double,
    @SerialName("release_date") val releaseDate: String? = null,
    val type: String,
    val runtime: Int? = null,
    val genres: List<String>? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    val platforms: List<PlatformData>? = null
)

@Serializable
data class ContextualMovieData(
    val id: Int,
    val title: String,
    val overview: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("vote_average") val voteAverage: Double,
    @SerialName("release_date") val releaseDate: String? = null,
    val type: String,
    val genres: List<String>? = null,
    val ageRating: String? = null,
    @SerialName("contextualReason") val contextualReason: String? = null, // Made optional
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    val platforms: List<PlatformData>? = null
)

// JSON parsing utility
private val json = Json { ignoreUnknownKeys = true }

private fun parseAgentResponse(text: String): MovieAgentResponse? {
    println("[DEBUG_PARSE] Original text: ${text.take(200)}...")
    
    return try {
        // First try to parse the entire text as JSON
        json.decodeFromString<MovieAgentResponse>(text)
    } catch (e: Exception) {
        try {
            // Check if this is an OpenAI API response format
            val openAIResponse = json.decodeFromString<JsonObject>(text)
            
            // Extract the content from OpenAI response structure
            val choices = openAIResponse["choices"]?.jsonArray
            val message = choices?.get(0)?.jsonObject?.get("message")?.jsonObject
            val content = message?.get("content")?.jsonPrimitive?.content
            
            if (content != null) {
                println("[DEBUG_PARSE] Extracted content from OpenAI response: ${content.take(200)}...")
                return parseAgentResponse(content) // Recursive call with extracted content
            }
            
            // Look for the consistent JSON pattern starting with {"type":"contextual_recommendations"
            val jsonStartPattern = """{"type":"contextual_recommendations""""
            val startIndex = text.indexOf(jsonStartPattern)
            
            if (startIndex != -1) {
                println("[DEBUG_PARSE] Found consistent JSON pattern at index: $startIndex")
                // Extract from the start pattern to the end
                val jsonText = text.substring(startIndex)
                // Find the matching closing brace for the JSON object
                val extractedJson = extractCompleteJsonObject(jsonText)
                if (extractedJson != null) {
                    println("[DEBUG_PARSE] Extracted consistent JSON: ${extractedJson.take(200)}...")
                    return json.decodeFromString<MovieAgentResponse>(extractedJson)
                }
            }
            
            // Fallback: try to extract JSON from within the text
            val extractedJson = extractJsonFromText(text)
            if (extractedJson != null) {
                println("[DEBUG_PARSE] Extracted JSON: ${extractedJson.take(200)}...")
                json.decodeFromString<MovieAgentResponse>(extractedJson)
            } else {
                println("[DEBUG_PARSE] Could not extract JSON from text")
                null
            }
        } catch (e2: Exception) {
            println("[DEBUG_PARSE] All parsing attempts failed: ${e2.message}")
            null
        }
    }
}

private fun extractCompleteJsonObject(text: String): String? {
    if (!text.startsWith("{")) return null
    
    var braceCount = 0
    var inString = false
    var escaped = false
    
    for (i in text.indices) {
        val char = text[i]
        
        when {
            escaped -> escaped = false
            char == '\\' && inString -> escaped = true
            char == '"' -> inString = !inString
            !inString && char == '{' -> braceCount++
            !inString && char == '}' -> {
                braceCount--
                if (braceCount == 0) {
                    return text.substring(0, i + 1)
                }
            }
        }
    }
    
    return null // No complete JSON object found
}

private fun extractJsonFromText(text: String): String? {
    // First, try to unescape the text in case it's escaped JSON
    val unescapedText = text.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r")
    
    // Look for JSON objects that contain type field with movie response types
    val typePattern = Regex(""""type"\s*:\s*"(platform_selection|movie_recommendations|movie_details|search_results|contextual_recommendations|error)""")
    var typeMatch = typePattern.find(unescapedText)
    
    // If no type field found, try to find JSON with recommendations field (for contextual responses)
    if (typeMatch == null) {
        val recommendationsPattern = Regex(""""recommendations"\s*:\s*\[""")
        typeMatch = recommendationsPattern.find(unescapedText)
    }
    
    // If still no match, try to find JSON with results field (for search responses)
    if (typeMatch == null) {
        val resultsPattern = Regex(""""results"\s*:\s*\[""")
        typeMatch = resultsPattern.find(unescapedText)
    }
    
    if (typeMatch == null) return null
    
    val textToSearch = unescapedText

    // Find the start of the JSON object before the match
    var startIndex = typeMatch.range.first
    while (startIndex > 0 && textToSearch[startIndex] != '{') {
        startIndex--
    }

    if (startIndex == 0 && textToSearch[0] != '{') return null

    // Find the end of the JSON object by counting braces
    var braceCount = 0
    var endIndex = startIndex
    var inString = false
    var escaped = false

    for (i in startIndex until textToSearch.length) {
        val char = textToSearch[i]

        when {
            escaped -> escaped = false
            char == '\\' && inString -> escaped = true
            char == '"' -> inString = !inString
            !inString && char == '{' -> braceCount++
            !inString && char == '}' -> {
                braceCount--
                if (braceCount == 0) {
                    endIndex = i
                    break
                }
            }
        }
    }

    return if (braceCount == 0 && endIndex > startIndex) {
        val extractedJson = textToSearch.substring(startIndex, endIndex + 1)
        // If it doesn't have a type field, add one based on content
        if (!extractedJson.contains("\"type\"")) {
            when {
                extractedJson.contains("\"recommendations\"") -> extractedJson.replaceFirst("{", "{\"type\":\"contextual_recommendations\",")
                extractedJson.contains("\"results\"") -> extractedJson.replaceFirst("{", "{\"type\":\"search_results\",")
                extractedJson.contains("\"movies\"") -> extractedJson.replaceFirst("{", "{\"type\":\"movie_recommendations\",")
                else -> extractedJson
            }
        } else {
            extractedJson
        }
    } else {
        null
    }
}

data class MovieRecommendationUiState(
    val title: String = "Movie Recommendation Assistant",
    val messages: List<Message> = listOf(Message.SystemMessage("¡Hola! Soy tu asistente de recomendaciones de películas y series.")),
    val inputText: String = "",
    val isInputEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val isChatEnded: Boolean = false,
    val userResponseRequested: Boolean = false,
    val currentUserResponse: String? = null,
    val platformSelectionActive: Boolean = false,
    val availablePlatforms: List<StreamingPlatform> = emptyList(),
    val selectedPlatforms: List<Int> = emptyList()
)

data class StreamingPlatform(
    val id: Int,
    val name: String
)

data class ContentRecommendation(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val voteAverage: Double,
    val releaseDate: String?,
    val type: String,
    val contextualReason: String? = null,
    val platforms: List<StreamingPlatformInfo> = emptyList(),
    val backdropUrl: String? = null
)

data class StreamingPlatformInfo(
    val id: Int,
    val name: String,
    val logoUrl: String?
)

data class MovieDetails(
    val movie: MovieDetailInfo,
    val similar: List<ContentRecommendation>
)

data class MovieDetailInfo(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val voteAverage: Double,
    val releaseDate: String?,
    val type: String,
    val runtime: Int? = null,
    val genres: List<String> = emptyList(),
    val backdropUrl: String? = null,
    val platforms: List<StreamingPlatformInfo> = emptyList()
)

class MovieRecommendationViewModel(
    application: Application,
    private val agentProvider: MovieAgentProvider
) : AndroidViewModel(application) {

    // Get device language in TMDB format
    private fun getDeviceLanguage(): String {
        val locale = Locale.getDefault()
        val language = locale.language
        val country = locale.country
        
        // Return language-country format for TMDB API
        return when {
            language == "es" -> if (country.isNotEmpty()) "$language-$country" else "es-ES"
            language == "en" -> if (country.isNotEmpty()) "$language-$country" else "en-US"
            else -> "$language-${country.ifEmpty { language.uppercase() }}"
        }
    }

    // Get device region for agent API calls
    private fun getDeviceRegion(): String {
        val locale = Locale.getDefault()
        val country = locale.country
        return country.ifEmpty { 
            when (locale.language) {
                "es" -> "ES"
                "en" -> "US"
                else -> "US"
            }
        }
    }

    private val _uiState = MutableStateFlow(
        MovieRecommendationUiState(
            title = agentProvider.title,
            messages = listOf(Message.SystemMessage(agentProvider.description))
        )
    )
    val uiState: StateFlow<MovieRecommendationUiState> = _uiState.asStateFlow()

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun togglePlatform(platformId: Int) {
        _uiState.update { currentState ->
            val updatedSelection = if (currentState.selectedPlatforms.contains(platformId)) {
                currentState.selectedPlatforms - platformId
            } else {
                currentState.selectedPlatforms + platformId
            }
            currentState.copy(selectedPlatforms = updatedSelection)
        }
    }

    fun confirmPlatformSelection() {
        val selectedIds = _uiState.value.selectedPlatforms
        if (selectedIds.isNotEmpty()) {
            val platformNames = _uiState.value.availablePlatforms
                .filter { it.id in selectedIds }
                .joinToString(", ") { it.name }

            sendMessage("Tengo estas plataformas: $platformNames. IDs: ${selectedIds.joinToString(",")}")

            _uiState.update { 
                it.copy(
                    platformSelectionActive = false,
                    availablePlatforms = emptyList()
                ) 
            }
        }
    }

    fun sendMessage(message: String? = null) {
        val userInput = message ?: _uiState.value.inputText.trim()
        if (userInput.isEmpty()) return

        if (_uiState.value.userResponseRequested) {
            _uiState.update {
                it.copy(
                    messages = it.messages + Message.UserMessage(userInput),
                    inputText = "",
                    isLoading = true,
                    userResponseRequested = false,
                    currentUserResponse = userInput
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    messages = it.messages + Message.UserMessage(userInput),
                    inputText = "",
                    isInputEnabled = false,
                    isLoading = true
                )
            }

            viewModelScope.launch {
                runAgent(userInput)
            }
        }
    }

    private suspend fun runAgent(userInput: String) {
        withContext(Dispatchers.IO) {
            try {
                println("[DEBUG_LOG] runAgent started. Input: $userInput")

                val agent = agentProvider.provideAgent(
                    appSettings = AppSettings(getApplication()),
                    onToolCallEvent = { message ->
                        println("[DEBUG_LOG] ToolCall: $message")
                    },
                    onErrorEvent = { errorMessage ->
                        println("[DEBUG_LOG] ErrorEvent: $errorMessage")
                        viewModelScope.launch {
                            addMessage(Message.ErrorMessage(errorMessage))
                            _uiState.update {
                                it.copy(
                                    isInputEnabled = true,
                                    isLoading = false
                                )
                            }
                        }
                    },
                    onAssistantMessage = { assistantResponse ->
                        println("[DEBUG_LOG] Assistant responded: $assistantResponse")

                        handleAgentResult(assistantResponse)
                        _uiState.update {
                            it.copy(
                                isInputEnabled = true,
                                isLoading = false,
                                userResponseRequested = false
                            )
                        }

                        val userResponse = _uiState
                            .first { it.currentUserResponse != null }
                            .currentUserResponse
                            ?: throw IllegalArgumentException("User response is null")

                        println("[DEBUG_LOG] User responded: $userResponse")
                        _uiState.update { it.copy(currentUserResponse = null) }
                        userResponse
                    },
                )

                println("[DEBUG_LOG] Agent created, calling run...")
                val result = agent.run(userInput)
                println("[DEBUG_LOG] Agent resultado: $result")

                handleAgentResult(result)
                _uiState.update {
                    it.copy(
                        isInputEnabled = true,
                        isLoading = false
                    )
                }

                println("[DEBUG_LOG] runAgent completed successfully")
            } catch (e: Exception) {
                println("[DEBUG_LOG] Exception in runAgent: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                addMessage(Message.ErrorMessage("Error: ${e.message}"))
                _uiState.update {
                    it.copy(
                        isInputEnabled = true,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun handleAgentResult(result: String) {
        println("[DEBUG_LOG] handleAgentResult called with: $result")

        val parsedResponse = parseAgentResponse(result)

        if (parsedResponse != null) {
            println("[DEBUG_LOG] Parsed response type: ${parsedResponse.type}")

            when (parsedResponse.type) {
                "platform_selection" -> {
                    val platforms = parsedResponse.platforms?.map {
                        StreamingPlatform(it.id, it.name)
                    } ?: emptyList()

                    _uiState.update {
                        it.copy(
                            platformSelectionActive = true,
                            availablePlatforms = platforms,
                            isInputEnabled = true,
                            isLoading = false
                        )
                    }
                }

                "movie_recommendations", "search_results" -> {
                    val recommendations = parsedResponse.movies?.map {
                        ContentRecommendation(
                            it.id, it.title, it.overview, it.posterUrl,
                            it.voteAverage, it.releaseDate, it.type,
                            platforms = it.platforms?.map { platform ->
                                StreamingPlatformInfo(platform.id, platform.name, platform.logoUrl)
                            } ?: emptyList(),
                            backdropUrl = it.backdropUrl
                        )
                    } ?: emptyList()

                    addMessage(Message.MovieRecommendationsMessage(
                        movies = recommendations,
                        message = parsedResponse.message,
                        composeCode = parsedResponse.composeCode,
                        uiType = parsedResponse.uiType
                    ))

                    _uiState.update {
                        it.copy(
                            isInputEnabled = true,
                            isLoading = false
                        )
                    }
                }

                "contextual_recommendations" -> {
                    val recommendations = parsedResponse.recommendations?.map {
                        ContentRecommendation(
                            it.id, it.title, it.overview, it.posterUrl,
                            it.voteAverage, it.releaseDate, it.type,
                            contextualReason = null, // Removed as per requirements
                            platforms = it.platforms?.map { platform ->
                                StreamingPlatformInfo(platform.id, platform.name, platform.logoUrl)
                            } ?: emptyList(),
                            backdropUrl = it.backdropUrl
                        )
                    } ?: emptyList()

                    // Usar el context_analysis si está disponible, sino usar el mensaje normal
                    val finalMessage = if (parsedResponse.contextAnalysis != null) {
                        "${parsedResponse.message}\n\n${parsedResponse.contextAnalysis}"
                    } else {
                        parsedResponse.message
                    }

                    addMessage(Message.MovieRecommendationsMessage(
                        movies = recommendations,
                        message = finalMessage,
                        composeCode = parsedResponse.composeCode,
                        uiType = parsedResponse.uiType
                    ))

                    _uiState.update {
                        it.copy(
                            isInputEnabled = true,
                            isLoading = false
                        )
                    }
                }

                "movie_details" -> {
                    val movieDetails = parsedResponse.movie?.let { movie ->
                        val similar = parsedResponse.similar?.map {
                            ContentRecommendation(
                                it.id, it.title, it.overview, it.posterUrl,
                                it.voteAverage, it.releaseDate, it.type,
                                platforms = it.platforms?.map { platform ->
                                    StreamingPlatformInfo(platform.id, platform.name, platform.logoUrl)
                                } ?: emptyList(),
                                backdropUrl = it.backdropUrl
                            )
                        } ?: emptyList()

                        MovieDetails(
                            movie = MovieDetailInfo(
                                movie.id, movie.title, movie.overview, movie.posterUrl,
                                movie.voteAverage, movie.releaseDate, movie.type,
                                movie.runtime, movie.genres ?: emptyList(),
                                backdropUrl = movie.backdropUrl,
                                platforms = movie.platforms?.map { platform ->
                                    StreamingPlatformInfo(platform.id, platform.name, platform.logoUrl)
                                } ?: emptyList()
                            ),
                            similar = similar
                        )
                    }

                    if (movieDetails != null) {
                        addMessage(Message.MovieDetailsMessage(movieDetails, parsedResponse.message))
                    } else {
                        addMessage(Message.AgentMessage(parsedResponse.message))
                    }

                    _uiState.update {
                        it.copy(
                            isInputEnabled = true,
                            isLoading = false
                        )
                    }
                }

                "error" -> {
                    _uiState.update {
                        it.copy(
                            isInputEnabled = true,
                            isLoading = false
                        )
                    }
                    addMessage(Message.ErrorMessage(parsedResponse.message))
                }

                else -> {
                    // Fallback for unknown types
                    _uiState.update {
                        it.copy(
                            isInputEnabled = true,
                            isLoading = false
                        )
                    }
                    addMessage(Message.AgentMessage(result))
                }
            }
        } else {
            // Fallback for non-JSON responses
            println("[DEBUG_LOG] Could not parse JSON, showing as text")
            _uiState.update {
                it.copy(
                    isInputEnabled = true,
                    isLoading = false
                )
            }
            addMessage(Message.AgentMessage(result))
        }
    }

    private fun addMessage(message: Message) {
        _uiState.update {
            it.copy(messages = it.messages + message)
        }
    }

    fun restartChat() {
        _uiState.update {
            MovieRecommendationUiState(
                title = agentProvider.title,
                messages = listOf(Message.SystemMessage(agentProvider.description)),
                selectedPlatforms = emptyList()
            )
        }
    }

    fun onMovieClick(movieId: Int, type: String, onNavigateToDetail: ((Int, String) -> Unit)? = null) {
        // Navigate to detail screen instead of continuing chat
        onNavigateToDetail?.invoke(movieId, type)
    }
    
    // Method for getting movie details directly from TMDB API (used in DetailScreen)
    suspend fun getMovieDetails(movieId: Int, type: String): MovieDetails? {
        return withContext(Dispatchers.IO) {
            try {
                val tmdbClient = com.safepal.agent.api.TMDBClient("ce2eb742633db1119130842dff34c3eb")
                val deviceLanguage = getDeviceLanguage()
                
                if (type == "tv") {
                    // Get TV show details in device language, fallback to English if overview is empty
                    var tvDetails = tmdbClient.getTVDetails(movieId, language = deviceLanguage)
                    println("[DEBUG] TV Details - $deviceLanguage overview length: ${tvDetails.overview.length}")
                    if (tvDetails.overview.isBlank() && deviceLanguage != "en-US") {
                        println("[DEBUG] TV overview in $deviceLanguage is empty, trying English...")
                        tvDetails = tmdbClient.getTVDetails(movieId, language = "en-US")
                        println("[DEBUG] TV Details - EN overview length: ${tvDetails.overview.length}")
                    }
                    val similarTVShows = tmdbClient.getTVRecommendations(movieId)
                    
                    // Get platform information
                    val watchProviders = try {
                        tmdbClient.getWatchProviders("tv", movieId)
                    } catch (e: Exception) {
                        null
                    }
                    
                    val platforms = watchProviders?.results?.get("US")?.flatrate?.map { provider ->
                        StreamingPlatformInfo(
                            provider.providerId,
                            provider.providerName,
                            provider.getLogoUrl()
                        )
                    } ?: emptyList()
                    
                    val similar = similarTVShows.results.take(5).map { tv ->
                        ContentRecommendation(
                            tv.id, tv.name, tv.overview, tv.getPosterUrl(),
                            tv.voteAverage, tv.firstAirDate, "tv",
                            backdropUrl = tv.getBackdropUrl()
                        )
                    }
                    
                    MovieDetails(
                        movie = MovieDetailInfo(
                            tvDetails.id, tvDetails.name, tvDetails.overview, tvDetails.getPosterUrl(),
                            tvDetails.voteAverage, tvDetails.firstAirDate, "tv",
                            runtime = tvDetails.episodeRunTime.firstOrNull(),
                            genres = tvDetails.getGenreNames(),
                            backdropUrl = tvDetails.getBackdropUrl(),
                            platforms = platforms
                        ),
                        similar = similar
                    )
                } else {
                    // Get movie details in device language, fallback to English if overview is empty
                    var movieDetails = tmdbClient.getMovieDetails(movieId, language = deviceLanguage)
                    println("[DEBUG] Movie Details - $deviceLanguage overview length: ${movieDetails.overview.length}")
                    if (movieDetails.overview.isBlank() && deviceLanguage != "en-US") {
                        println("[DEBUG] Movie overview in $deviceLanguage is empty, trying English...")
                        movieDetails = tmdbClient.getMovieDetails(movieId, language = "en-US")
                        println("[DEBUG] Movie Details - EN overview length: ${movieDetails.overview.length}")
                    }
                    val similarMovies = tmdbClient.getMovieRecommendations(movieId)
                    
                    // Get platform information
                    val watchProviders = try {
                        tmdbClient.getWatchProviders("movie", movieId)
                    } catch (e: Exception) {
                        null
                    }
                    
                    val platforms = watchProviders?.results?.get("US")?.flatrate?.map { provider ->
                        StreamingPlatformInfo(
                            provider.providerId,
                            provider.providerName,
                            provider.getLogoUrl()
                        )
                    } ?: emptyList()
                    
                    val similar = similarMovies.results.take(5).map { movie ->
                        ContentRecommendation(
                            movie.id, movie.title, movie.overview, movie.getPosterUrl(),
                            movie.voteAverage, movie.releaseDate, "movie",
                            backdropUrl = movie.getBackdropUrl()
                        )
                    }
                    
                    MovieDetails(
                        movie = MovieDetailInfo(
                            movieDetails.id, movieDetails.title, movieDetails.overview, movieDetails.getPosterUrl(),
                            movieDetails.voteAverage, movieDetails.releaseDate, "movie",
                            runtime = movieDetails.runtime,
                            genres = movieDetails.getGenreNames(),
                            backdropUrl = movieDetails.getBackdropUrl(),
                            platforms = platforms
                        ),
                        similar = similar
                    )
                }
            } catch (e: Exception) {
                println("[DEBUG] Error getting movie details: ${e.message}")
                null
            }
        }
    }
}

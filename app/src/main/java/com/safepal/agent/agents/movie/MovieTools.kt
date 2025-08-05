package com.safepal.agent.agents.movie

import ai.koog.agents.core.tools.*
import com.safepal.agent.api.TMDBClient
import com.safepal.agent.api.StreamingPlatform
import com.safepal.agent.api.MovieDetails
import com.safepal.agent.api.TVDetails
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import com.safepal.agent.utils.GenreMapper
import java.util.Locale

object MovieTools {
    
    private val tmdbClient = TMDBClient("ce2eb742633db1119130842dff34c3eb")
    
    // Helper function to get device language for API calls
    private fun getDeviceLanguage(): String {
        val locale = Locale.getDefault()
        val language = locale.language
        val country = locale.country
        
        return when {
            language == "es" -> if (country.isNotEmpty()) "$language-$country" else "es-ES"
            language == "en" -> if (country.isNotEmpty()) "$language-$country" else "en-US"
            else -> "$language-${country.ifEmpty { language.uppercase() }}"
        }
    }
    
    // Helper function to get device region for API calls
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
    
    // Helper function to get platform information for content
    private suspend fun getPlatformInfo(contentId: Int, contentType: String, region: String): List<GetMovieRecommendationsTool.PlatformInfo> {
        return try {
            val watchProviders = tmdbClient.getWatchProviders(contentType, contentId)
            val regionData = watchProviders.results[region]
            
            val platforms = mutableListOf<GetMovieRecommendationsTool.PlatformInfo>()
            
            // Add flatrate providers (streaming)
            regionData?.flatrate?.forEach { provider ->
                platforms.add(
                    GetMovieRecommendationsTool.PlatformInfo(
                        id = provider.providerId,
                        name = provider.providerName,
                        logo_url = provider.getLogoUrl()
                    )
                )
            }
            
            // Add rent providers if no flatrate
            if (platforms.isEmpty()) {
                regionData?.rent?.forEach { provider ->
                    platforms.add(
                        GetMovieRecommendationsTool.PlatformInfo(
                            id = provider.providerId,
                            name = provider.providerName,
                            logo_url = provider.getLogoUrl()
                        )
                    )
                }
            }
            
            platforms.distinctBy { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    object GetMovieRecommendationsTool : Tool<GetMovieRecommendationsTool.Args, GetMovieRecommendationsTool.Result>() {
        @Serializable
        data class Args(
            val platforms: String,
            val type: String = "movie",
            val region: String = "US",
            val language: String = "en-US"
        ) : ToolArgs
        
        @Serializable
        data class PlatformInfo(
            val id: Int,
            val name: String,
            val logo_url: String?
        )

        @Serializable
        data class ContentInfo(
            val id: Int,
            val title: String,
            val overview: String,
            val poster_url: String?,
            val vote_average: Double,
            val release_date: String?,
            val type: String,
            val platforms: List<GetMovieRecommendationsTool.PlatformInfo> = emptyList(),
            val backdrop_url: String? = null
        )
        
        @Serializable
        data class RecommendationResponse(
            val type: String = "recommendations_response",
            val message: String,
            val recommendations: List<ContentInfo>
        )

        @Serializable
        data class Result(val response: RecommendationResponse) : ToolResult {
            override fun toStringDefault(): String = Json.encodeToString(response)
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "get_movie_recommendations",
            description = "Get movie or TV show recommendations based on user's streaming platforms",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "platforms",
                    description = "Comma-separated list of platform IDs",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "type",
                    description = "Type of content: 'movie' or 'tv'",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            return try {
                val platformIds = args.platforms.split(",").map { it.trim().toInt() }
                val providersString = platformIds.joinToString("|")
                
                val recommendations = if (args.type == "tv") {
                    val tvResponse = tmdbClient.discoverTVShowsWithRegion(
                        withWatchProviders = providersString,
                        watchRegion = args.region,
                        language = args.language,
                        page = 1
                    )
                    coroutineScope {
                        tvResponse.results.take(8).map { tv ->
                            async {
                                val platforms = getPlatformInfo(tv.id, "tv", args.region)
                                ContentInfo(
                                    id = tv.id,
                                    title = tv.name,
                                    overview = tv.overview,
                                    poster_url = tv.getPosterUrl(),
                                    vote_average = tv.voteAverage,
                                    release_date = tv.firstAirDate,
                                    type = "tv",
                                    platforms = platforms,
                                    backdrop_url = tv.getBackdropUrl()
                                )
                            }
                        }.map { it.await() }
                    }
                } else {
                    val movieResponse = tmdbClient.discoverMoviesWithRegion(
                        withWatchProviders = providersString,
                        watchRegion = args.region,
                        language = args.language,
                        page = 1
                    )
                    coroutineScope {
                        movieResponse.results.take(8).map { movie ->
                            async {
                                val platforms = getPlatformInfo(movie.id, "movie", args.region)
                                ContentInfo(
                                    id = movie.id,
                                    title = movie.title,
                                    overview = movie.overview,
                                    poster_url = movie.getPosterUrl(),
                                    vote_average = movie.voteAverage,
                                    release_date = movie.releaseDate,
                                    type = "movie",
                                    platforms = platforms,
                                    backdrop_url = movie.getBackdropUrl()
                                )
                            }
                        }.map { it.await() }
                    }
                }
                
                val finalMessage = if (recommendations.isEmpty()) {
                    "No se encontraron ${if (args.type == "tv") "series" else "películas"} disponibles en las plataformas especificadas."
                } else {
                    "Aquí tienes ${if (args.type == "tv") "series" else "películas"} recomendadas para tus plataformas:"
                }
                
                val response = RecommendationResponse(
                    type = "movie_recommendations",
                    message = finalMessage,
                    recommendations = recommendations
                )
                
                Result(response)
                
            } catch (e: Exception) {
                val errorResponse = RecommendationResponse(
                    type = "error",
                    message = "Error obteniendo recomendaciones: ${e.message}",
                    recommendations = emptyList()
                )
                Result(errorResponse)
            }
        }
    }
    
    object SearchContentTool : Tool<SearchContentTool.Args, SearchContentTool.Result>() {
        @Serializable
        data class Args(
            val query: String,
            val type: String = "movie",
            val platforms: List<Int> = emptyList(),
            val region: String = "US"
        ) : ToolArgs
        
        @Serializable
        data class ContentInfo(
            val id: Int,
            val title: String,
            val overview: String,
            val poster_url: String?,
            val vote_average: Double,
            val release_date: String?,
            val type: String,
            val platforms: List<GetMovieRecommendationsTool.PlatformInfo> = emptyList()
        )
        
        @Serializable
        data class SearchResponse(
            val type: String = "search_results",
            val message: String,
            val results: List<ContentInfo>
        )

        @Serializable
        data class Result(val response: SearchResponse) : ToolResult {
            override fun toStringDefault(): String = Json.encodeToString(response)
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "search_content",
            description = "Search for specific movies or TV shows by name and get their platform availability",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "query",
                    description = "Search query for movie or TV show name (e.g., 'Iron Man', 'Avatar', 'Breaking Bad')",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "type",
                    description = "Type of content: 'movie' or 'tv'",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "platforms",
                    description = "Optional: Array of platform IDs to highlight (e.g., [337] for Disney+)",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "region",
                    description = "Region for platform availability (e.g., 'US', 'AR')",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            return try {
                val results = if (args.type == "tv") {
                    val tvResponse = tmdbClient.searchTVShows(args.query)
                    coroutineScope {
                        tvResponse.results.take(5).map { tv ->
                            async {
                                val platforms = getPlatformInfo(tv.id, "tv", args.region)
                                ContentInfo(
                                    id = tv.id,
                                    title = tv.name,
                                    overview = tv.overview,
                                    poster_url = tv.getPosterUrl(),
                                    vote_average = tv.voteAverage,
                                    release_date = tv.firstAirDate,
                                    type = "tv",
                                    platforms = platforms
                                )
                            }
                        }.awaitAll()
                    }
                } else {
                    val movieResponse = tmdbClient.searchMovies(args.query)
                    coroutineScope {
                        movieResponse.results.take(5).map { movie ->
                            async {
                                val platforms = getPlatformInfo(movie.id, "movie", args.region)
                                ContentInfo(
                                    id = movie.id,
                                    title = movie.title,
                                    overview = movie.overview,
                                    poster_url = movie.getPosterUrl(),
                                    vote_average = movie.voteAverage,
                                    release_date = movie.releaseDate,
                                    type = "movie",
                                    platforms = platforms
                                )
                            }
                        }.awaitAll()
                    }
                }
                
                val finalMessage = if (results.isEmpty()) {
                    "No se encontraron resultados para '${args.query}'. Intenta con un término de búsqueda diferente."
                } else {
                    "Resultados de búsqueda para '${args.query}':"
                }
                
                val response = SearchResponse(
                    type = "search_results",
                    message = finalMessage,
                    results = results
                )
                
                Result(response)
                
            } catch (e: Exception) {
                val errorResponse = SearchResponse(
                    type = "error",
                    message = "Error en la búsqueda: ${e.message}",
                    results = emptyList()
                )
                Result(errorResponse)
            }
        }
    }
    
    object GetSimilarContentTool : Tool<GetSimilarContentTool.Args, GetSimilarContentTool.Result>() {
        @Serializable
        data class Args(
            val contentId: Int,
            val type: String = "movie"
        ) : ToolArgs
        
        @Serializable
        data class ContentInfo(
            val id: Int,
            val title: String,
            val overview: String,
            val poster_url: String?,
            val vote_average: Double,
            val release_date: String?,
            val type: String
        )
        
        @Serializable
        data class SimilarResponse(
            val type: String = "similar_content",
            val message: String,
            val similar: List<ContentInfo>
        )

        @Serializable
        data class Result(val response: SimilarResponse) : ToolResult {
            override fun toStringDefault(): String = Json.encodeToString(response)
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "get_similar_content",
            description = "Get similar movies or TV shows based on a specific content ID",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "contentId",
                    description = "ID of the movie or TV show",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "type",
                    description = "Type of content: 'movie' or 'tv'",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            return try {
                val similar = if (args.type == "tv") {
                    val tvResponse = tmdbClient.getTVRecommendations(args.contentId)
                    tvResponse.results.take(5).map { tv ->
                        ContentInfo(
                            id = tv.id,
                            title = tv.name,
                            overview = tv.overview,
                            poster_url = tv.getPosterUrl(),
                            vote_average = tv.voteAverage,
                            release_date = tv.firstAirDate,
                            type = "tv"
                        )
                    }
                } else {
                    val movieResponse = tmdbClient.getMovieRecommendations(args.contentId)
                    movieResponse.results.take(5).map { movie ->
                        ContentInfo(
                            id = movie.id,
                            title = movie.title,
                            overview = movie.overview,
                            poster_url = movie.getPosterUrl(),
                            vote_average = movie.voteAverage,
                            release_date = movie.releaseDate,
                            type = "movie"
                        )
                    }
                }
                
                val finalMessage = if (similar.isEmpty()) {
                    "No se encontró contenido similar disponible."
                } else {
                    "Contenido similar encontrado:"
                }
                
                val response = SimilarResponse(
                    type = "similar_content",
                    message = finalMessage,
                    similar = similar
                )
                
                Result(response)
                
            } catch (e: Exception) {
                val errorResponse = SimilarResponse(
                    type = "error",
                    message = "Error obteniendo contenido similar: ${e.message}",
                    similar = emptyList()
                )
                Result(errorResponse)
            }
        }
    }
    
    object GetMovieDetailsTool : Tool<GetMovieDetailsTool.Args, GetMovieDetailsTool.Result>() {
        @Serializable
        data class Args(
            val movieId: Int,
            val type: String = "movie",
            val region: String = "US",
            val language: String = "en-US"
        ) : ToolArgs
        
        @Serializable
        data class MovieDetail(
            val id: Int,
            val title: String,
            val overview: String,
            val poster_url: String?,
            val vote_average: Double,
            val release_date: String?,
            val type: String,
            val runtime: Int? = null,
            val genres: List<String> = emptyList(),
            val backdrop_url: String? = null,
            val platforms: List<GetMovieRecommendationsTool.PlatformInfo> = emptyList()
        )
        
        @Serializable
        data class ContentInfo(
            val id: Int,
            val title: String,
            val overview: String,
            val poster_url: String?,
            val vote_average: Double,
            val release_date: String?,
            val type: String
        )

        @Serializable
        data class MovieDetailsResponse(
            val type: String = "movie_details",
            val message: String,
            val movie: MovieDetail,
            val similar: List<ContentInfo>
        )

        @Serializable
        data class Result(val response: MovieDetailsResponse) : ToolResult {
            override fun toStringDefault(): String = Json.encodeToString(response)
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "get_movie_details",
            description = "Get detailed information about a specific movie or TV show and similar content",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "movieId",
                    description = "ID of the movie or TV show",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "type",
                    description = "Type of content: 'movie' or 'tv'",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            return try {
                // Get detailed information from TMDB API and platform info concurrently
                val movieDetail = coroutineScope {
                    val platformsDeferred = async { getPlatformInfo(args.movieId, args.type, args.region) }
                    
                    val detail = if (args.type == "tv") {
                        val tvDetails = tmdbClient.getTVDetails(args.movieId)
                        MovieDetail(
                            id = tvDetails.id,
                            title = tvDetails.name,
                            overview = tvDetails.overview,
                            poster_url = tvDetails.getPosterUrl(),
                            vote_average = tvDetails.voteAverage,
                            release_date = tvDetails.firstAirDate,
                            type = "tv",
                            runtime = tvDetails.episodeRunTime.firstOrNull(),
                            genres = tvDetails.getGenreNames(),
                            backdrop_url = tvDetails.getBackdropUrl(),
                            platforms = platformsDeferred.await()
                        )
                    } else {
                        val movieDetails = tmdbClient.getMovieDetails(args.movieId)
                        MovieDetail(
                            id = movieDetails.id,
                            title = movieDetails.title,
                            overview = movieDetails.overview,
                            poster_url = movieDetails.getPosterUrl(),
                            vote_average = movieDetails.voteAverage,
                            release_date = movieDetails.releaseDate,
                            type = "movie",
                            runtime = movieDetails.runtime,
                            genres = movieDetails.getGenreNames(),
                            backdrop_url = movieDetails.getBackdropUrl(),
                            platforms = platformsDeferred.await()
                        )
                    }
                    detail
                }
                
                // Get similar content
                val similar = if (args.type == "tv") {
                    val tvResponse = tmdbClient.getTVRecommendations(args.movieId)
                    tvResponse.results.take(3).map { tv ->
                        ContentInfo(
                            id = tv.id,
                            title = tv.name,
                            overview = tv.overview,
                            poster_url = tv.getPosterUrl(),
                            vote_average = tv.voteAverage,
                            release_date = tv.firstAirDate,
                            type = "tv"
                        )
                    }
                } else {
                    val movieResponse = tmdbClient.getMovieRecommendations(args.movieId)
                    movieResponse.results.take(3).map { movie ->
                        ContentInfo(
                            id = movie.id,
                            title = movie.title,
                            overview = movie.overview,
                            poster_url = movie.getPosterUrl(),
                            vote_average = movie.voteAverage,
                            release_date = movie.releaseDate,
                            type = "movie"
                        )
                    }
                }
                
                val response = MovieDetailsResponse(
                    message = "Detalles de ${movieDetail.title}:",
                    movie = movieDetail,
                    similar = similar
                )
                
                Result(response)
                
            } catch (e: Exception) {
                val errorResponse = MovieDetailsResponse(
                    type = "error",
                    message = "Error obteniendo detalles: ${e.message}",
                    movie = MovieDetail(
                        id = args.movieId,
                        title = "Error",
                        overview = "No se pudieron obtener los detalles",
                        poster_url = null,
                        vote_average = 0.0,
                        release_date = null,
                        type = args.type
                    ),
                    similar = emptyList()
                )
                Result(errorResponse)
            }
        }
    }

    object GetContextualRecommendationsTool : Tool<GetContextualRecommendationsTool.Args, GetContextualRecommendationsTool.Result>() {
        @Serializable
        data class Args(
            val userQuery: String,
            val platforms: List<Int> = emptyList(),
            val viewingContext: String = "", // e.g., "familia", "niños", "noche", "fin de semana"
            val ageGroup: String = "", // e.g., "niños", "adolescentes", "adultos", "familia"
            val genre: String = "", // género preferido
            val mood: String = "", // e.g., "relajante", "acción", "comedia", "educativo"
            val region: String = "US",
            val language: String = "en-US"
        ) : ToolArgs
        
        @Serializable
        data class ContextualContentInfo(
            val id: Int,
            val title: String,
            val overview: String,
            val poster_url: String?,
            val vote_average: Double,
            val release_date: String?,
            val type: String,
            val genres: List<String> = emptyList(),
            val ageRating: String? = null,
            val contextualReason: String, // Razón específica de por qué se recomienda
            val backdrop_url: String? = null,
            val platforms: List<GetMovieRecommendationsTool.PlatformInfo> = emptyList()
        )
        
        @Serializable
        data class ContextualRecommendationResponse(
            val type: String = "contextual_recommendations",
            val message: String,
            val context_analysis: String, // Análisis del contexto del usuario
            val recommendations: List<ContextualContentInfo>
        )

        @Serializable
        data class Result(val response: ContextualRecommendationResponse) : ToolResult {
            override fun toStringDefault(): String = Json.encodeToString(response)
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "get_contextual_recommendations",
            description = "Get intelligent movie/TV recommendations based on user context, family situation, mood, and viewing circumstances",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "userQuery",
                    description = "Original user query with context (e.g., 'quiero ver algo en Netflix con mis hijos de 7 años')",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "platforms",
                    description = "Array of platform IDs where user wants to watch (e.g., [8] for Netflix, [9] for Amazon Prime)",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "viewingContext",
                    description = "Viewing situation: 'familia', 'niños', 'noche', 'fin_de_semana', 'solo', 'pareja'",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "ageGroup",
                    description = "Target age group: 'preescolar' (3-5), 'niños' (6-12), 'adolescentes' (13-17), 'adultos', 'familia'",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "genre",
                    description = "Preferred genre if specified",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "mood",
                    description = "Desired mood: 'relajante', 'divertido', 'educativo', 'aventura', 'emocional'",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            return try {
                // Use device language/region if defaults are used
                val language = if (args.language == "en-US") getDeviceLanguage() else args.language
                val region = if (args.region == "US") getDeviceRegion() else args.region
                
                println("[DeviceLocale] Using language: $language, region: $region")
                
                // Detectar tipo de contenido solicitado específicamente
                val requestedContentType = detectContentType(args.userQuery)
                println("[ContentTypeDetection] User query: '${args.userQuery}' -> detected type: $requestedContentType")
                
                // Initialize genres based on detected content type
                GenreMapper.initializeGenres(tmdbClient, language, requestedContentType)
                
                // Análisis del contexto
                val contextAnalysis = buildString {
                    append("Basado en tu consulta: '${args.userQuery}', ")
                    when {
                        args.ageGroup.contains("niños") || args.ageGroup.contains("preescolar") -> 
                            append("he identificado que buscas contenido apropiado para niños. ")
                        args.viewingContext.contains("familia") -> 
                            append("he identificado que buscas contenido familiar. ")
                        args.mood.isNotEmpty() -> 
                            append("he notado que buscas algo ${args.mood}. ")
                    }
                    append("He seleccionado contenido que se adapta perfectamente a estas necesidades.")
                }

                // Definir filtros inteligentes basados en el contexto usando GenreMapper
                val genreFilters = getSmartGenreFilters(args.userQuery, args.ageGroup, args.mood, args.genre)
                val platformFilter = if (args.platforms.isNotEmpty()) args.platforms.joinToString("|") else ""
                
                // Buscar contenido basado en lo solicitado específicamente
                val recommendations = coroutineScope {
                    val genreFilterString = if (genreFilters.isNotEmpty()) genreFilters.joinToString(",") else null
                    
                    when (requestedContentType) {
                        "movie" -> searchMoviesOnly(platformFilter, args.region, args.language, genreFilterString, args)
                        "tv" -> searchTVShowsOnly(platformFilter, args.region, args.language, genreFilterString, args)
                        else -> searchBothTypes(platformFilter, args.region, args.language, genreFilterString, args)
                    }
                }
                
                // Verificar si se encontraron recomendaciones
                val finalMessage = if (recommendations.isEmpty()) {
                    when (requestedContentType) {
                        "movie" -> "No se encontraron películas que coincidan con tus criterios."
                        "tv" -> "No se encontraron series que coincidan con tus criterios." 
                        else -> "No se encontraron recomendaciones que coincidan con tus criterios."
                    }
                } else {
                    "Recomendaciones personalizadas basadas en tu situación:"
                }
                
                val finalContextAnalysis = if (recommendations.isEmpty()) {
                    "$contextAnalysis Lamentablemente, no se encontraron resultados disponibles que cumplan con estos criterios específicos. Intenta con criterios más amplios o diferentes plataformas."
                } else {
                    contextAnalysis
                }
                
                val response = ContextualRecommendationResponse(
                    type = "contextual_recommendations",
                    message = finalMessage,
                    context_analysis = finalContextAnalysis,
                    recommendations = recommendations
                )
                
                Result(response)
            } catch (e: Exception) {
                val errorResponse = ContextualRecommendationResponse(
                    type = "error",
                    message = "Error obteniendo recomendaciones contextuales: ${e.message}",
                    context_analysis = "No se pudo analizar el contexto debido a un error.",
                    recommendations = emptyList()
                )
                Result(errorResponse)
            }
        }

        private fun getSmartGenreFilters(userQuery: String, ageGroup: String, mood: String, preferredGenre: String): List<Int> {
            val genres = mutableSetOf<Int>()
            
            // 1. Prioridad máxima: género explícito
            if (preferredGenre.isNotEmpty()) {
                genres.addAll(GenreMapper.findGenreIds(preferredGenre))
            }
            
            // 2. Buscar géneros en la consulta del usuario
            if (genres.isEmpty()) {
                val queryGenres = GenreMapper.findGenreIds(userQuery)
                if (queryGenres.isNotEmpty()) {
                    genres.addAll(queryGenres)
                    println("[GenreFilters] Found genres from query '$userQuery': ${queryGenres.joinToString(",")}")
                }
            }
            
            // 3. Fallback basado en edad si no se encontraron géneros
            if (genres.isEmpty()) {
                when (ageGroup.lowercase()) {
                    "preescolar", "niños" -> {
                        genres.addAll(GenreMapper.findGenreIds("animación familia"))
                    }
                    "adolescentes" -> {
                        genres.addAll(GenreMapper.findGenreIds("comedia acción aventura"))
                    }
                    "familia" -> {
                        genres.addAll(GenreMapper.findGenreIds("familia comedia aventura"))
                    }
                    "adultos", "pareja" -> {
                        genres.addAll(GenreMapper.findGenreIds("drama acción comedia romance"))
                    }
                }
            }
            
            // 4. Mood como filtro complementario
            when (mood.lowercase()) {
                "relajante" -> if (genres.isEmpty()) genres.addAll(GenreMapper.findGenreIds("comedia romance drama"))
                "divertido" -> if (genres.isEmpty()) genres.addAll(GenreMapper.findGenreIds("comedia aventura"))  
                "educativo" -> if (genres.isEmpty()) genres.addAll(GenreMapper.findGenreIds("documental historia"))
                "emocionante" -> if (genres.isEmpty()) genres.addAll(GenreMapper.findGenreIds("acción thriller aventura"))
                "emocional" -> if (genres.isEmpty()) genres.addAll(GenreMapper.findGenreIds("drama romance"))
            }
            
            val result = genres.toList()
            println("[GenreFilters] Final genre IDs: ${result.joinToString(",")} from query: '$userQuery', genre: '$preferredGenre', age: '$ageGroup', mood: '$mood'")
            
            // Debug: Show which genre names were found
            result.forEach { genreId ->
                val genreName = GenreMapper.getGenreName(genreId)
                println("[GenreFilters] Genre ID $genreId = $genreName")
            }
            
            return result
        }

        private fun isContentAppropriate(title: String, overview: String, ageGroup: String, mood: String): Boolean {
            val content = "$title $overview".lowercase()
            
            // Filtros para niños
            if (ageGroup.contains("niños") || ageGroup.contains("preescolar")) {
                val inappropriateKeywords = listOf(
                    "horror", "terror", "violence", "violent", "murder", "kill", "death", "blood",
                    "war", "gun", "weapon", "drug", "alcohol", "sex", "adult", "mature",
                    "violencia", "muerte", "sangre", "guerra", "arma", "droga", "alcohol", "adulto"
                )
                
                if (inappropriateKeywords.any { content.contains(it) }) {
                    return false
                }
            }
            
            // Filtros para mood educativo
            if (mood.contains("educativo")) {
                val educationalKeywords = listOf(
                    "learn", "education", "science", "history", "nature", "documentary",
                    "aprende", "educación", "ciencia", "historia", "naturaleza", "documental"
                )
                
                return educationalKeywords.any { content.contains(it) }
            }
            
            return true
        }

        private fun detectContentType(userQuery: String): String {
            val query = userQuery.lowercase()
            
            // Palabras específicas para películas
            val movieKeywords = listOf(
                "pelicula", "película", "peliculas", "películas", "peli", "pelis", 
                "film", "filme", "films", "filmes", "movie", "movies",
                "largometraje", "largometrajes", "cine"
            )
            
            // Palabras específicas para series
            val tvKeywords = listOf(
                "serie", "series", "programa", "programas", "show", "shows",
                "temporada", "temporadas", "episodio", "episodios", "capitulo", "capítulo", "capitulos", "capítulos",
                "miniserie", "miniseries", "docuserie", "docuseries", "tv show", "tv shows"
            )
            
            // Contar ocurrencias de palabras clave
            val movieCount = movieKeywords.count { query.contains(it) }
            val tvCount = tvKeywords.count { query.contains(it) }
            
            return when {
                movieCount > tvCount -> {
                    println("[ContentTypeDetection] Detected 'movie' - movie keywords: $movieCount, tv keywords: $tvCount")
                    "movie"
                }
                tvCount > movieCount -> {
                    println("[ContentTypeDetection] Detected 'tv' - movie keywords: $movieCount, tv keywords: $tvCount")
                    "tv"
                }
                else -> {
                    println("[ContentTypeDetection] No specific type detected - searching both - movie keywords: $movieCount, tv keywords: $tvCount")
                    "both" // Buscar ambos tipos si no hay una preferencia clara
                }
            }
        }

        private suspend fun searchMoviesOnly(platformFilter: String, region: String, language: String, genreFilterString: String?, args: Args): List<ContextualContentInfo> {
            val movieResponse = if (platformFilter.isNotEmpty()) {
                tmdbClient.discoverMoviesWithRegion(
                    withWatchProviders = platformFilter,
                    watchRegion = region,
                    language = language,
                    withGenres = genreFilterString,
                    page = 1
                )
            } else {
                tmdbClient.discoverMoviesWithRegion(
                    watchRegion = region,
                    language = language,
                    withGenres = genreFilterString,
                    page = 1
                )
            }
            
            return movieResponse.results
                .filter { isContentAppropriate(it.title, it.overview, args.ageGroup, args.mood) }
                .take(8) // 8 películas ya que no incluimos series
                .map { movie ->
                    val platforms = createPlatformInfo(args.platforms)
                    val reason = generateContextualReason(movie.title, movie.overview, args.ageGroup, args.mood, args.viewingContext)
                    ContextualContentInfo(
                        id = movie.id,
                        title = movie.title,
                        overview = movie.overview,
                        poster_url = movie.getPosterUrl(),
                        vote_average = movie.voteAverage,
                        release_date = movie.releaseDate,
                        type = "movie",
                        contextualReason = reason,
                        backdrop_url = movie.getBackdropUrl(),
                        platforms = platforms
                    )
                }
        }

        private suspend fun searchTVShowsOnly(platformFilter: String, region: String, language: String, genreFilterString: String?, args: Args): List<ContextualContentInfo> {
            val tvResponse = if (platformFilter.isNotEmpty()) {
                tmdbClient.discoverTVShowsWithRegion(
                    withWatchProviders = platformFilter,
                    watchRegion = region,
                    language = language,
                    withGenres = genreFilterString,
                    page = 1
                )
            } else {
                tmdbClient.discoverTVShowsWithRegion(
                    watchRegion = region,
                    language = language,
                    withGenres = genreFilterString,
                    page = 1
                )
            }
            
            return tvResponse.results
                .filter { isContentAppropriate(it.name, it.overview, args.ageGroup, args.mood) }
                .take(8) // 8 series ya que no incluimos películas
                .map { tv ->
                    val platforms = createPlatformInfo(args.platforms)
                    val reason = generateContextualReason(tv.name, tv.overview, args.ageGroup, args.mood, args.viewingContext)
                    ContextualContentInfo(
                        id = tv.id,
                        title = tv.name,
                        overview = tv.overview,
                        poster_url = tv.getPosterUrl(),
                        vote_average = tv.voteAverage,
                        release_date = tv.firstAirDate,
                        type = "tv",
                        contextualReason = reason,
                        backdrop_url = tv.getBackdropUrl(),
                        platforms = platforms
                    )
                }
        }

        private suspend fun searchBothTypes(platformFilter: String, region: String, language: String, genreFilterString: String?, args: Args): List<ContextualContentInfo> = coroutineScope {
            val moviesDeferred = async {
                val movieResponse = if (platformFilter.isNotEmpty()) {
                    tmdbClient.discoverMoviesWithRegion(
                        withWatchProviders = platformFilter,
                        watchRegion = region,
                        language = language,
                        withGenres = genreFilterString,
                        page = 1
                    )
                } else {
                    tmdbClient.discoverMoviesWithRegion(
                        watchRegion = region,
                        language = language,
                        withGenres = genreFilterString,
                        page = 1
                    )
                }
                
                movieResponse.results
                    .filter { isContentAppropriate(it.title, it.overview, args.ageGroup, args.mood) }
                    .take(4) // 4 películas máximo para dar espacio a series
                    .map { movie ->
                        val platforms = createPlatformInfo(args.platforms)
                        val reason = generateContextualReason(movie.title, movie.overview, args.ageGroup, args.mood, args.viewingContext)
                        ContextualContentInfo(
                            id = movie.id,
                            title = movie.title,
                            overview = movie.overview,
                            poster_url = movie.getPosterUrl(),
                            vote_average = movie.voteAverage,
                            release_date = movie.releaseDate,
                            type = "movie",
                            contextualReason = reason,
                            backdrop_url = movie.getBackdropUrl(),
                            platforms = platforms
                        )
                    }
            }
            
            val tvShowsDeferred = async {
                val tvResponse = if (platformFilter.isNotEmpty()) {
                    tmdbClient.discoverTVShowsWithRegion(
                        withWatchProviders = platformFilter,
                        watchRegion = region,
                        language = language,
                        withGenres = genreFilterString,
                        page = 1
                    )
                } else {
                    tmdbClient.discoverTVShowsWithRegion(
                        watchRegion = region,
                        language = language,
                        withGenres = genreFilterString,
                        page = 1
                    )
                }
                
                tvResponse.results
                    .filter { isContentAppropriate(it.name, it.overview, args.ageGroup, args.mood) }
                    .take(4) // 4 series máximo para balancear con películas
                    .map { tv ->
                        val platforms = createPlatformInfo(args.platforms)
                        val reason = generateContextualReason(tv.name, tv.overview, args.ageGroup, args.mood, args.viewingContext)
                        ContextualContentInfo(
                            id = tv.id,
                            title = tv.name,
                            overview = tv.overview,
                            poster_url = tv.getPosterUrl(),
                            vote_average = tv.voteAverage,
                            release_date = tv.firstAirDate,
                            type = "tv",
                            contextualReason = reason,
                            backdrop_url = tv.getBackdropUrl(),
                            platforms = platforms
                        )
                    }
            }
            
            // Combinar películas y series
            val movies = moviesDeferred.await()
            val tvShows = tvShowsDeferred.await()
            (movies + tvShows).shuffled().take(8) // Mezclar y tomar máximo 8
        }

        private fun createPlatformInfo(platformIds: List<Int>): List<GetMovieRecommendationsTool.PlatformInfo> {
            return if (platformIds.isNotEmpty()) {
                platformIds.map { platformId ->
                    GetMovieRecommendationsTool.PlatformInfo(
                        id = platformId,
                        name = when (platformId) {
                            8 -> "Netflix"
                            9 -> "Amazon Prime Video"
                            337 -> "Disney Plus"
                            384 -> "HBO Max"
                            15 -> "Hulu"
                            else -> "Platform $platformId"
                        },
                        logo_url = null
                    )
                }
            } else emptyList()
        }

        private fun generateContextualReason(title: String, overview: String, ageGroup: String, mood: String, viewingContext: String): String {
            return when {
                viewingContext.contains("pareja") && mood.contains("emocionante") -> "Perfecto para una noche de terror en pareja: suspenso y emociones fuertes garantizadas"
                viewingContext.contains("pareja") -> "Ideal para ver en pareja: entretenimiento perfecto para dos"
                viewingContext.contains("amigos") -> "Excelente para ver con amigos: diversión grupal asegurada"
                ageGroup.contains("niños") -> "Perfecto para ver con niños: contenido familiar y apropiado para su edad"
                ageGroup.contains("familia") -> "Ideal para disfrutar en familia: entretenimiento para todas las edades"
                mood.contains("relajante") -> "Perfecto para relajarse: historia tranquila y atmosfera calmante"
                mood.contains("educativo") -> "Contenido educativo: aprenderás algo nuevo mientras te entretienes"
                mood.contains("emocionante") -> "Lleno de emociones: te mantendrá al borde del asiento"
                mood.contains("aventura") -> "Lleno de aventuras: acción y emoción de principio a fin"
                viewingContext.contains("noche") -> "Ideal para ver por la noche: ambiente perfecto para el horario"
                viewingContext.contains("solo") -> "Perfecto para ver solo: experiencia cinematográfica personal"
                else -> "Altamente recomendado: excelente calificación y gran popularidad"
            }
        }
    }
}
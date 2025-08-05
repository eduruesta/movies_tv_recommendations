package com.safepal.agent.utils

import com.safepal.agent.api.Genre
import com.safepal.agent.api.TMDBClient

object GenreMapper {
    private var movieGenres: List<Genre> = emptyList()
    private var tvGenres: List<Genre> = emptyList()
    private var lastUpdated: Long = 0
    private val cacheValidityMs = 24 * 60 * 60 * 1000L // 24 hours
    
    suspend fun initializeGenres(tmdbClient: TMDBClient, language: String = "es-ES", contentType: String? = null) {
        val currentTime = System.currentTimeMillis()
        val cacheExpired = (currentTime - lastUpdated) > cacheValidityMs
        
        when (contentType) {
            "movie" -> {
                // Solo cargar géneros de películas
                if (movieGenres.isEmpty() || cacheExpired) {
                    try {
                        movieGenres = tmdbClient.getMovieGenres(language).genres
                        lastUpdated = currentTime
                        println("[GenreMapper] Loaded ${movieGenres.size} movie genres only")
                    } catch (e: Exception) {
                        println("[GenreMapper] Error loading movie genres: ${e.message}")
                        if (language != "en-US") {
                            try {
                                movieGenres = tmdbClient.getMovieGenres("en-US").genres
                                lastUpdated = currentTime
                            } catch (e2: Exception) {
                                println("[GenreMapper] Fallback to English also failed: ${e2.message}")
                            }
                        }
                    }
                }
            }
            "tv" -> {
                // Solo cargar géneros de series
                if (tvGenres.isEmpty() || cacheExpired) {
                    try {
                        tvGenres = tmdbClient.getTVGenres(language).genres
                        lastUpdated = currentTime
                        println("[GenreMapper] Loaded ${tvGenres.size} TV genres only")
                    } catch (e: Exception) {
                        println("[GenreMapper] Error loading TV genres: ${e.message}")
                        if (language != "en-US") {
                            try {
                                tvGenres = tmdbClient.getTVGenres("en-US").genres
                                lastUpdated = currentTime
                            } catch (e2: Exception) {
                                println("[GenreMapper] Fallback to English also failed: ${e2.message}")
                            }
                        }
                    }
                }
            }
            else -> {
                // Cargar ambos tipos (comportamiento por defecto)
                if (movieGenres.isEmpty() || tvGenres.isEmpty() || cacheExpired) {
                    try {
                        movieGenres = tmdbClient.getMovieGenres(language).genres
                        tvGenres = tmdbClient.getTVGenres(language).genres
                        lastUpdated = currentTime
                        println("[GenreMapper] Loaded ${movieGenres.size} movie genres and ${tvGenres.size} TV genres")
                    } catch (e: Exception) {
                        println("[GenreMapper] Error loading genres: ${e.message}")
                        if (language != "en-US") {
                            try {
                                movieGenres = tmdbClient.getMovieGenres("en-US").genres
                                tvGenres = tmdbClient.getTVGenres("en-US").genres
                                lastUpdated = currentTime
                            } catch (e2: Exception) {
                                println("[GenreMapper] Fallback to English also failed: ${e2.message}")
                            }
                        }
                    }
                }
            }
        }
    }
    
    fun findGenreIds(genreQuery: String): List<Int> {
        val query = genreQuery.lowercase().trim()
        val matchedGenres = mutableSetOf<Int>()
        
        println("[GenreMapper] Searching for genre query: '$query'")
        println("[GenreMapper] Available movie genres: ${movieGenres.map { "${it.name}(${it.id})" }}")
        println("[GenreMapper] Available TV genres: ${tvGenres.map { "${it.name}(${it.id})" }}")
        
        // Comprehensive Spanish-English genre mappings with synonyms and variations
        val spanishMappings = mapOf(
            // Action variations
            "accion" to listOf("action", "adventure", "action & adventure", "thriller"),
            "acción" to listOf("action", "adventure", "action & adventure", "thriller"),
            "aventura" to listOf("adventure", "action & adventure", "action"),
            "aventuras" to listOf("adventure", "action & adventure", "action"),
            "adrenalina" to listOf("action", "thriller", "adventure"),
            "emocion" to listOf("action", "thriller", "adventure"),
            "emoción" to listOf("action", "thriller", "adventure"),
            
            // Comedy variations
            "comedia" to listOf("comedy", "family"),
            "comedias" to listOf("comedy", "family"),
            "comico" to listOf("comedy"),
            "cómico" to listOf("comedy"),
            "divertido" to listOf("comedy", "family"),
            "divertida" to listOf("comedy", "family"),
            "gracioso" to listOf("comedy"),
            "graciosa" to listOf("comedy"),
            "humor" to listOf("comedy"),
            "risa" to listOf("comedy"),
            "risas" to listOf("comedy"),
            
            // Horror variations
            "terror" to listOf("horror", "thriller"),
            "horror" to listOf("horror", "thriller"),
            "miedo" to listOf("horror", "thriller"),
            "suspenso" to listOf("thriller", "horror", "mystery"),
            "suspense" to listOf("thriller", "horror", "mystery"),
            "escalofriante" to listOf("horror", "thriller"),
            "escalofriantes" to listOf("horror", "thriller"),
            
            // Drama variations
            "drama" to listOf("drama"),
            "dramas" to listOf("drama"),
            "dramatico" to listOf("drama"),
            "dramático" to listOf("drama"),
            "emotivo" to listOf("drama", "romance"),
            "emotiva" to listOf("drama", "romance"),
            "sentimental" to listOf("drama", "romance"),
            
            // Romance variations
            "romance" to listOf("romance", "drama"),
            "romantica" to listOf("romance", "drama"),
            "romántica" to listOf("romance", "drama"),
            "romantico" to listOf("romance", "drama"),
            "romántico" to listOf("romance", "drama"),
            "amor" to listOf("romance", "drama"),
            "amorosa" to listOf("romance", "drama"),
            "amoroso" to listOf("romance", "drama"),
            "pareja" to listOf("romance", "drama"),
            
            // Sci-Fi variations
            "ciencia ficcion" to listOf("science fiction", "fantasy", "action"),
            "ciencia ficción" to listOf("science fiction", "fantasy", "action"),
            "sci-fi" to listOf("science fiction", "fantasy", "action"),
            "scifi" to listOf("science fiction", "fantasy", "action"),
            "futurista" to listOf("science fiction", "action"),
            "futuristas" to listOf("science fiction", "action"),
            "espacial" to listOf("science fiction", "adventure"),
            "espaciales" to listOf("science fiction", "adventure"),
            "aliens" to listOf("science fiction", "thriller"),
            "extraterrestres" to listOf("science fiction", "thriller"),
            
            // Fantasy variations
            "fantasia" to listOf("fantasy", "adventure"),
            "fantasía" to listOf("fantasy", "adventure"),
            "magia" to listOf("fantasy", "adventure"),
            "magico" to listOf("fantasy", "adventure"),
            "mágico" to listOf("fantasy", "adventure"),
            "magica" to listOf("fantasy", "adventure"),
            "mágica" to listOf("fantasy", "adventure"),
            "superheroes" to listOf("action", "adventure", "fantasy", "science fiction"),
            "superhéroes" to listOf("action", "adventure", "fantasy", "science fiction"),
            
            // Animation variations
            "animacion" to listOf("animation", "family", "comedy"),
            "animación" to listOf("animation", "family", "comedy"),
            "animada" to listOf("animation", "family", "comedy"),
            "animado" to listOf("animation", "family", "comedy"),
            "caricaturas" to listOf("animation", "family", "comedy"),
            "dibujos" to listOf("animation", "family", "comedy"),
            
            // Family variations
            "familia" to listOf("family", "comedy", "animation"),
            "familiar" to listOf("family", "comedy", "animation"),
            "familiares" to listOf("family", "comedy", "animation"),
            "niños" to listOf("family", "animation", "comedy"),
            "infantil" to listOf("family", "animation", "comedy"),
            "infantiles" to listOf("family", "animation", "comedy"),
            
            // Crime/Mystery variations
            "crimen" to listOf("crime", "thriller", "mystery"),
            "crimenes" to listOf("crime", "thriller", "mystery"),
            "crímenes" to listOf("crime", "thriller", "mystery"),
            "policial" to listOf("crime", "thriller", "mystery"),
            "policiales" to listOf("crime", "thriller", "mystery"),
            "detective" to listOf("crime", "thriller", "mystery"),
            "detectives" to listOf("crime", "thriller", "mystery"),
            "misterio" to listOf("mystery", "thriller", "crime"),
            "misterios" to listOf("mystery", "thriller", "crime"),
            "investigacion" to listOf("crime", "mystery", "thriller"),
            "investigación" to listOf("crime", "mystery", "thriller"),
            
            // Thriller variations
            "thriller" to listOf("thriller", "action", "crime", "mystery"),
            "thrillers" to listOf("thriller", "action", "crime", "mystery"),
            "tension" to listOf("thriller", "horror", "mystery"),
            "tensión" to listOf("thriller", "horror", "mystery"),
            
            // Documentary variations
            "documental" to listOf("documentary"),
            "documentales" to listOf("documentary"),
            "educativo" to listOf("documentary"),
            "educativa" to listOf("documentary"),
            "educativos" to listOf("documentary"),
            "educativas" to listOf("documentary"),
            
            // War variations
            "guerra" to listOf("war", "war & politics", "action", "drama"),
            "guerras" to listOf("war", "war & politics", "action", "drama"),
            "belico" to listOf("war", "war & politics", "action"),
            "bélico" to listOf("war", "war & politics", "action"),
            "belica" to listOf("war", "war & politics", "action"),
            "bélica" to listOf("war", "war & politics", "action"),
            "militar" to listOf("war", "war & politics", "action"),
            "militares" to listOf("war", "war & politics", "action"),
            
            // Music variations
            "musica" to listOf("music"),
            "música" to listOf("music"),
            "musical" to listOf("music", "comedy"),
            "musicales" to listOf("music", "comedy"),
            "concierto" to listOf("music"),
            "conciertos" to listOf("music"),
            
            // History variations
            "historia" to listOf("history", "drama", "war"),
            "historias" to listOf("history", "drama", "war"),
            "historico" to listOf("history", "drama", "war"),
            "histórico" to listOf("history", "drama", "war"),
            "historica" to listOf("history", "drama", "war"),
            "histórica" to listOf("history", "drama", "war"),
            "epoca" to listOf("history", "drama"),
            "época" to listOf("history", "drama"),
            
            // Biography variations
            "biografia" to listOf("biography", "drama", "history"),
            "biografía" to listOf("biography", "drama", "history"),
            "biografico" to listOf("biography", "drama", "history"),
            "biográfico" to listOf("biography", "drama", "history"),
            "biografica" to listOf("biography", "drama", "history"),
            "biográfica" to listOf("biography", "drama", "history"),
            
            // Western variations
            "western" to listOf("western", "action", "adventure"),
            "westerns" to listOf("western", "action", "adventure"),
            "vaqueros" to listOf("western", "action", "adventure"),
            "oeste" to listOf("western", "action", "adventure"),
            
            // Sports variations
            "deportes" to listOf("sport", "drama"),
            "deporte" to listOf("sport", "drama"),
            "deportivo" to listOf("sport", "drama"),
            "deportiva" to listOf("sport", "drama"),
            "deportivos" to listOf("sport", "drama"),
            "deportivas" to listOf("sport", "drama")
        )
        
        // Check Spanish mappings first
        spanishMappings[query]?.forEach { englishGenre ->
            val found = findGenresByName(englishGenre)
            println("[GenreMapper] Spanish mapping '$query' -> '$englishGenre' found IDs: $found")
            found.forEach { matchedGenres.add(it) }
        }
        
        // Direct search in both movie and TV genres
        val directFound = findGenresByName(query)
        println("[GenreMapper] Direct search for '$query' found IDs: $directFound")
        directFound.forEach { matchedGenres.add(it) }
        
        // Fuzzy matching for Spanish mappings if no exact match
        if (matchedGenres.isEmpty()) {
            spanishMappings.keys.forEach { spanishKey ->
                if (isFuzzyMatch(query, spanishKey)) {
                    spanishMappings[spanishKey]?.forEach { englishGenre ->
                        val found = findGenresByName(englishGenre)
                        println("[GenreMapper] Fuzzy Spanish mapping '$query' ~= '$spanishKey' -> '$englishGenre' found IDs: $found")
                        found.forEach { matchedGenres.add(it) }
                    }
                }
            }
        }
        
        // Partial matching for compound queries
        if (matchedGenres.isEmpty()) {
            val words = query.split(" ", "-", "_", "de", "del", "la", "los", "las")
                .filter { it.length > 3 } // Avoid short words and articles
            
            words.forEach { word ->
                val cleanWord = word.trim().lowercase()
                
                // Try exact mapping first
                spanishMappings[cleanWord]?.forEach { englishGenre ->
                    val found = findGenresByName(englishGenre)
                    println("[GenreMapper] Word mapping '$cleanWord' -> '$englishGenre' found IDs: $found")
                    found.forEach { matchedGenres.add(it) }
                }
                
                // Try fuzzy matching on words
                if (matchedGenres.isEmpty()) {
                    spanishMappings.keys.forEach { spanishKey ->
                        if (isFuzzyMatch(cleanWord, spanishKey)) {
                            spanishMappings[spanishKey]?.forEach { englishGenre ->
                                val found = findGenresByName(englishGenre)
                                println("[GenreMapper] Fuzzy word mapping '$cleanWord' ~= '$spanishKey' -> '$englishGenre' found IDs: $found")
                                found.forEach { matchedGenres.add(it) }
                            }
                        }
                    }
                }
                
                // Direct search in genres
                val wordFound = findGenresByName(cleanWord)
                println("[GenreMapper] Word search for '$cleanWord' found IDs: $wordFound")
                wordFound.forEach { matchedGenres.add(it) }
            }
        }
        
        val result = matchedGenres.toList()
        println("[GenreMapper] Final result for query '$query': $result")
        return result
    }
    
    private fun isFuzzyMatch(query: String, target: String): Boolean {
        val queryLower = query.lowercase()
        val targetLower = target.lowercase()
        
        // Exact match
        if (queryLower == targetLower) return true
        
        // Contains match (query contains target or vice versa)
        if (queryLower.contains(targetLower) || targetLower.contains(queryLower)) return true
        
        // Handle common variations and typos
        val variations = mapOf(
            "accion" to listOf("acción"),
            "acción" to listOf("accion"),
            "ciencia ficcion" to listOf("ciencia ficción", "sci-fi", "scifi"),
            "ciencia ficción" to listOf("ciencia ficcion", "sci-fi", "scifi"),
            "fantasia" to listOf("fantasía"),
            "fantasía" to listOf("fantasia"),
            "romantica" to listOf("romántica", "romance"),
            "romántica" to listOf("romantica", "romance"),
            "romantico" to listOf("romántico", "romance"),
            "romántico" to listOf("romantico", "romance"),
            "musica" to listOf("música"),
            "música" to listOf("musica"),
            "biografia" to listOf("biografía"),
            "biografía" to listOf("biografia"),
            "animacion" to listOf("animación"),
            "animación" to listOf("animacion"),
            "superheroes" to listOf("superhéroes", "super heroes", "super héroes"),
            "superhéroes" to listOf("superheroes", "super heroes", "super héroes")
        )
        
        // Check variations
        variations[queryLower]?.let { targets ->
            if (targets.any { it == targetLower }) return true
        }
        variations[targetLower]?.let { queries ->
            if (queries.any { it == queryLower }) return true
        }
        
        // Levenshtein distance for typos (allow 1-2 character differences for words > 4 chars)
        if (queryLower.length > 4 && targetLower.length > 4) {
            val distance = levenshteinDistance(queryLower, targetLower)
            val maxDistance = minOf(2, maxOf(queryLower.length, targetLower.length) / 4)
            return distance <= maxDistance
        }
        
        return false
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        
        for (i in 1..m) {
            for (j in 1..n) {
                if (s1[i - 1] == s2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    dp[i][j] = 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        
        return dp[m][n]
    }
    
    private fun findGenresByName(name: String): List<Int> {
        val searchName = name.lowercase()
        val found = mutableListOf<Int>()
        
        // Search in movie genres with exact and fuzzy matching
        movieGenres.forEach { genre ->
            val genreName = genre.name.lowercase()
            
            // Exact match first (highest priority)
            if (genreName == searchName) {
                found.add(genre.id)
            }
            // Contains match
            else if (genreName.contains(searchName) || searchName.contains(genreName)) {
                found.add(genre.id)
            }
            // Fuzzy match for typos and variations
            else if (isFuzzyMatch(searchName, genreName)) {
                found.add(genre.id)
            }
        }
        
        // Search in TV genres with exact and fuzzy matching
        tvGenres.forEach { genre ->
            val genreName = genre.name.lowercase()
            
            // Exact match first (highest priority)
            if (genreName == searchName) {
                found.add(genre.id)
            }
            // Contains match  
            else if (genreName.contains(searchName) || searchName.contains(genreName)) {
                found.add(genre.id)
            }
            // Fuzzy match for typos and variations
            else if (isFuzzyMatch(searchName, genreName)) {
                found.add(genre.id)
            }
        }
        
        return found.distinct()
    }
    
    fun getGenreName(genreId: Int): String? {
        return movieGenres.find { it.id == genreId }?.name 
            ?: tvGenres.find { it.id == genreId }?.name
    }
    
    fun getAllMovieGenres(): List<Genre> = movieGenres
    fun getAllTVGenres(): List<Genre> = tvGenres
}
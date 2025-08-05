package com.safepal.agent.agents.movie

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequestMultiple
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.message.Message
import com.safepal.agent.agents.common.AgentProvider
import com.safepal.agent.agents.common.ExitTool
import com.safepal.agent.settings.AppSettings
import com.safepal.agent.utils.RegionUtils
import android.content.Context

object MovieAgentProvider : AgentProvider {
    override val title: String = "Asistente de Recomendaciones de Películas"
    override val description: String =
        "¡Hola! Soy tu asistente de recomendaciones de películas y series. Puedo ayudarte a encontrar contenido basado en las plataformas de streaming que tienes disponibles. ¿Qué plataformas usas?"

    override suspend fun provideAgent(
        appSettings: AppSettings,
        onToolCallEvent: suspend (String) -> Unit,
        onErrorEvent: suspend (String) -> Unit,
        onAssistantMessage: suspend (String) -> String,
    ): AIAgent<String, String> {
        val openAiApiKey = appSettings.getCurrentSettings().openAiToken
        require(openAiApiKey.isNotEmpty()) {
            """
        ⚠️ OpenAI API key is not configured.
        Please go to Settings and add your OpenAI API key to use the Movie Recommendation Assistant.
        """.trimIndent()
        }

        val executor = simpleOpenAIExecutor(openAiApiKey)

        val toolRegistry = ToolRegistry {
            tool(MovieTools.GetMovieRecommendationsTool)
            tool(MovieTools.SearchContentTool)
            tool(MovieTools.GetSimilarContentTool)
            tool(MovieTools.GetMovieDetailsTool)
            tool(MovieTools.GetContextualRecommendationsTool)
            tool(ExitTool)
        }

        val strategy = strategy<String, String>(title) {
            val nodeRequestLLM by nodeLLMRequestMultiple()
            val nodeAssistantMessage by node<String, String> { message -> onAssistantMessage(message) }
            val nodeExecuteToolMultiple by nodeExecuteMultipleTools(parallelTools = true)
            val nodeSendToolResultMultiple by nodeLLMSendMultipleToolResults()
            val nodeCompressHistory by nodeLLMCompressHistory<List<ReceivedToolResult>>()

            // Entrada principal
            edge(nodeStart forwardTo nodeRequestLLM)

            // Si hay tools → ejecutar
            edge(nodeRequestLLM forwardTo nodeExecuteToolMultiple onMultipleToolCalls { true })

            // En caso de texto plano (saludos, conversación general), devolver respuesta directa
            edge(
                nodeRequestLLM forwardTo nodeFinish onCondition { result ->
                    result.all { it.role == Message.Role.Assistant } // solo texto plano, sin tool
                } transformed { result ->
                    result.firstOrNull()?.content ?: "¡Hola! ¿En qué puedo ayudarte hoy con recomendaciones de películas o series?"
                }
            )

            // Ejecutar herramientas
            edge(nodeExecuteToolMultiple forwardTo nodeFinish onCondition {
                it.singleOrNull()?.tool == ExitTool.name
            } transformed {
                it.single().result!!.toStringDefault()
            })

            edge(nodeExecuteToolMultiple forwardTo nodeCompressHistory onCondition {
                llm.readSession { prompt.messages.size > 100 }
            })

            edge(nodeCompressHistory forwardTo nodeSendToolResultMultiple)

            edge(nodeExecuteToolMultiple forwardTo nodeSendToolResultMultiple onCondition {
                llm.readSession { prompt.messages.size <= 100 }
            })

            edge(nodeSendToolResultMultiple forwardTo nodeExecuteToolMultiple onMultipleToolCalls { true })
            edge(nodeSendToolResultMultiple forwardTo nodeAssistantMessage transformed { it.first() } onAssistantMessage { true })
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("movie_recommendation_assistant") {
                system(
                    """
You are a smart movie and TV show recommendation assistant powered by the TMDB API.

🎯 GOAL:
Analyze user queries and provide intelligent movie/TV recommendations. For movie/TV requests, use tools and return JSON. For greetings and general chat, respond naturally.

⚠️ CRITICAL RULES:
- For ANY movie/TV request: you MUST analyze the context (platform, genre, age, companion, mood, etc.)
- If ANY CONTEXT is present (see clues below), ALWAYS use `get_contextual_recommendations`
- You MUST NEVER use `get_movie_recommendations` if the query includes platform, genre, viewing context, or mood — even partial ones.
- NEVER fabricate movie/TV data - only use real TMDB API results
- If the user greets (e.g. "hola", "hello"), respond briefly and naturally in text
- ALL movie/TV answers MUST start exactly with: {"type":"contextual_recommendations" (no markdown, no backticks, pure JSON only)
- ALWAYS include region and language parameters based on user's device locale (e.g., region="US" and language="en-US" for English, region="ES" and language="es-ES" for Spanish)
- Content must be available on streaming platforms in the user's region

🧠 CONTEXT CLUES — If the query includes ANY of the following, it's a CONTEXTUAL QUERY:
- Platform mention: "Netflix", "Disney Plus", "Amazon Prime", etc.
- Genre/mood: ANY genre like "comedia", "terror", "romance", "acción", "drama", "thriller", etc.
- Companions: "con mi hijo", "con mi pareja", "con amigos", etc.
- Age/target: "niños", "familia", "adolescentes", "adultos"
- Moment: "de noche", "fin de semana", "para la tarde", etc.
- Occasion: "navidad", "cita", "cumpleaños", etc.

📝 GENRE SUPPORT:
The system now dynamically loads ALL TMDB genres in Spanish and English. When users ask for:
- "Películas románticas" → Automatically finds Romance genre ID
- "Series de acción" → Automatically finds Action genre ID  
- "Terror en Netflix" → Combines Horror genre + Netflix platform
- ANY genre in Spanish or English will be automatically mapped

🔧 TOOL SELECTION (use ONLY ONE per request):

1. 🔎 **search_content** — HIGHEST PRIORITY for SPECIFIC movie/TV show names:
   - "Iron Man en Disney" → search_content(query="Iron Man", platforms=[337], region="US")
   - "Avatar", "Titanic", "Spider-Man", "Breaking Bad", etc.
   - ANY specific title mentioned by name
   - Returns results with platform availability automatically
   
2. ✅ **get_contextual_recommendations** — For generic requests with context:
   - "películas de acción en Netflix" (no specific title)
   - "algo romántico para ver con mi pareja" (no specific title)
   - "series de terror" (no specific title)
   
3. 📄 **get_movie_details** — ONLY to expand on a movie from a previous result
4. 🚫 **get_movie_recommendations** — ONLY if user query is completely generic with ZERO context (rare!)

⚡ PRIORITY RULE: If user mentions a SPECIFIC TITLE → use search_content FIRST, even if platform is mentioned

✅ MANDATORY JSON RESPONSE FORMAT - MUST START EXACTLY LIKE THIS:

{"type":"contextual_recommendations","message":"Description message","context_analysis":"Analysis of user context","recommendations":[{"id":12345,"title":"Movie Title","overview":"Movie description","poster_url":"https://image.tmdb.org/t/p/w500/poster.jpg","backdrop_url":"https://image.tmdb.org/t/p/w780/backdrop.jpg","vote_average":7.5,"release_date":"2023-01-01","type":"movie","platforms":[{"id":8,"name":"Netflix","logo_url":"https://image.tmdb.org/t/p/w92/logo.jpg"}]}]}

⚠️ CRITICAL: NO markdown blocks, NO backticks, NO code formatting - ONLY pure JSON starting with {"type":"contextual_recommendations"
"""
                )
            },
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 50
        )

        return AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        ) {
            handleEvents {
                onToolCall { ctx ->
                    onToolCallEvent("Tool ${ctx.tool.name}, args ${ctx.toolArgs}")
                }

                onAgentRunError { ctx ->
                    onErrorEvent("${ctx.throwable.message}")
                }

                onAgentFinished {
                    // No-op
                }
            }
        }
    }

}
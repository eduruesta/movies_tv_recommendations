package com.safepal.agent.agents.common

import ai.koog.agents.core.tools.*
import kotlinx.serialization.Serializable

object ExitTool : Tool<ExitTool.Args, ExitTool.Result>() {
    @Serializable
    data class Args(val reason: String = "Chat terminado") : ToolArgs

    @Serializable
    data class Result(val message: String) : ToolResult {
        override fun toStringDefault(): String = message
    }

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "exit_chat",
        description = "Exit the current chat session",
        requiredParameters = listOf()
    )

    override suspend fun execute(args: Args): Result {
        return Result("Chat terminado: ${args.reason}")
    }
}
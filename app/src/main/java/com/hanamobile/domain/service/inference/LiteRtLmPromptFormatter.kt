package com.hanamobile.domain.service.inference

import com.hanamobile.core.model.BackendRequest
import com.hanamobile.core.model.MessageRole

/**
 * Converts assembled session context into a single text prompt for LiteRT-LM.
 * This keeps session assembly concerns outside backend runtime code.
 */
class LiteRtLmPromptFormatter {
    fun format(request: BackendRequest): String {
        val parts = mutableListOf<PromptSegment>()

        if (request.systemPrompt.isNotBlank()) {
            parts += PromptSegment("system", request.systemPrompt.trim())
        }

        if (request.memoryBlock.isNotBlank()) {
            parts += PromptSegment("memory", request.memoryBlock.trim())
        }

        if (request.toolResults.isNotEmpty()) {
            val toolBlock = request.toolResults.joinToString("\n") {
                "- ${it.toolName}: ${it.outputText}"
            }
            parts += PromptSegment("tools", toolBlock)
        }

        request.history.forEach { message ->
            parts += PromptSegment(roleTag(message.role), message.content.trim())
        }

        parts += PromptSegment("user", request.userInput.trim())
        parts += PromptSegment("assistant", "", closeTag = false)

        return parts.joinToString(separator = "\n\n") { it.toPromptText() }
    }

    private fun roleTag(role: MessageRole): String = when (role) {
        MessageRole.SYSTEM -> "system"
        MessageRole.MEMORY -> "memory"
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
        MessageRole.TOOL -> "tool"
    }
}

private data class PromptSegment(
    val role: String,
    val text: String,
    val closeTag: Boolean = true
) {
    fun toPromptText(): String =
        if (closeTag) {
            "<$role>\n$text\n</$role>"
        } else {
            "<$role>"
        }
}

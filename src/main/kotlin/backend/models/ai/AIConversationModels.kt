package backend.models.ai

import kotlinx.serialization.Serializable

@Serializable
data class AIConversationSummary(
    val sessionId: String,
    val title: String,
    val messageCount: Int,
    val updatedAt: String
)

@Serializable
data class AIConversationTranscript(
    val sessionId: String,
    val messages: List<AIMessage>
)

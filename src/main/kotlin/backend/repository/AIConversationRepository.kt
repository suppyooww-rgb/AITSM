package backend.repository

import backend.config.AIConversationMessagesTable
import backend.models.ai.AIConversationSummary
import backend.models.ai.AIMessage
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class AIConversationRepository {
    fun appendMessage(userId: Int, sessionId: String, message: AIMessage) = transaction {
        AIConversationMessagesTable.insert {
            it[AIConversationMessagesTable.userId] = userId
            it[AIConversationMessagesTable.sessionId] = sessionId
            it[role] = message.role
            it[content] = message.content
            it[createdAt] = LocalDateTime.now()
        }
    }

    fun loadSession(userId: Int, sessionId: String, limit: Int): MutableList<AIMessage> = transaction {
        AIConversationMessagesTable
            .selectAll()
            .where {
                (AIConversationMessagesTable.userId eq userId) and
                    (AIConversationMessagesTable.sessionId eq sessionId)
            }
            .orderBy(AIConversationMessagesTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { AIMessage(it[AIConversationMessagesTable.role], it[AIConversationMessagesTable.content]) }
            .reversed()
            .toMutableList()
    }

    fun listSessions(userId: Int, limit: Int): List<AIConversationSummary> = transaction {
        AIConversationMessagesTable
            .selectAll()
            .where { AIConversationMessagesTable.userId eq userId }
            .orderBy(AIConversationMessagesTable.createdAt, SortOrder.DESC)
            .limit(1_000)
            .toList()
            .groupBy { it[AIConversationMessagesTable.sessionId] }
            .map { (sessionId, messages) ->
                val firstUserMessage = messages
                    .asReversed()
                    .firstOrNull { it[AIConversationMessagesTable.role] == "user" }
                    ?.get(AIConversationMessagesTable.content)
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    .orEmpty()
                AIConversationSummary(
                    sessionId = sessionId,
                    title = firstUserMessage.ifBlank { "Untitled conversation" }.take(80),
                    messageCount = messages.size,
                    updatedAt = messages.maxOf { it[AIConversationMessagesTable.createdAt] }.toString()
                )
            }
            .sortedByDescending { it.updatedAt }
            .take(limit.coerceIn(1, 50))
    }

    fun clearSession(userId: Int, sessionId: String): Int = transaction {
        AIConversationMessagesTable.deleteWhere {
            (AIConversationMessagesTable.userId eq userId) and
                (AIConversationMessagesTable.sessionId eq sessionId)
        }
    }
}

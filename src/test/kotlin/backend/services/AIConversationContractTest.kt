package backend.services

import backend.models.ai.AIMessage
import backend.services.ai.initialTroubleshootingClarification
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AIConversationContractTest {
    private val chatService = Files.readString(
        Path.of("src/main/kotlin/backend/services/ai/AIChatService.kt")
    )

    @Test
    fun `vague issues require clarification before troubleshooting`() {
        assertTrue(chatService.contains("ask exactly one highest-value clarifying question and stop"))
        assertTrue(chatService.contains("Do not combine several questions"))
        assertTrue(chatService.contains("Treat short reports"))
        assertFalse(chatService.contains("Use these short plain headings when relevant: Likely causes"))
    }

    @Test
    fun `troubleshooting is conversational incremental and evidence based`() {
        assertTrue(chatService.contains("Respond naturally and acknowledge the user's concern without sounding scripted"))
        assertTrue(chatService.contains("suggest only one or two safe, relevant actions at a time"))
        assertTrue(chatService.contains("ask the user to report the result before continuing"))
        assertTrue(chatService.contains("Clearly describe uncertain causes as possibilities, not confirmed findings"))
        assertTrue(chatService.contains("Never say an issue is resolved unless the user confirms"))
    }

    @Test
    fun `structured headings are reserved for supported summaries`() {
        assertTrue(chatService.contains("Use a natural conversational reply by default"))
        assertTrue(chatService.contains("only when summarizing a supported diagnosis, confirmed outcome, or ticket-ready resolution"))
    }

    @Test
    fun `hardware guidance requires exact identification and avoids invented vendor steps`() {
        assertTrue(chatService.contains("identify the manufacturer and exact model before giving device-specific instructions"))
        assertTrue(chatService.contains("collect the connection method, operating system, exact symptom or error, and relevant recent changes one question at a time"))
        assertTrue(chatService.contains("Never invent model-specific menus, button sequences, driver names, firmware versions, commands, or support URLs"))
    }

    @Test
    fun `initial vague printer report asks for brand and model deterministically`() {
        val clarification = initialTroubleshootingClarification(
            listOf(AIMessage("user", "Printer is not detected"))
        )

        assertContains(clarification.orEmpty(), "printer's brand and exact model")
        assertNull(initialTroubleshootingClarification(listOf(AIMessage("user", "Epson L3210 printer is not detected"))))
        assertNull(initialTroubleshootingClarification(listOf(AIMessage("user", "My laptop is slow"))))
        assertNull(
            initialTroubleshootingClarification(
                listOf(
                    AIMessage("user", "Printer is not detected"),
                    AIMessage("assistant", clarification.orEmpty()),
                    AIMessage("user", "Epson L3210")
                )
            )
        )
    }

    @Test
    fun `assistant empty state prepares users for follow up questions`() {
        val frontend = Files.readString(
            Path.of("src/main/resources/static/frontend/ai-assistant/js/ai-assistant.js")
        )

        assertTrue(frontend.contains("The assistant may ask a few questions before suggesting the next step"))
    }

    @Test
    fun `suggested prompts are replaced by persistent chat history`() {
        val html = Files.readString(
            Path.of("src/main/resources/static/frontend/ai-assistant/html/ai-assistant.html")
        )
        val frontend = Files.readString(
            Path.of("src/main/resources/static/frontend/ai-assistant/js/ai-assistant.js")
        )
        val routes = Files.readString(Path.of("src/main/kotlin/backend/routes/AIRoutes.kt"))
        val repository = Files.readString(Path.of("src/main/kotlin/backend/repository/AIConversationRepository.kt"))

        assertTrue(html.contains("AI chat history"))
        assertTrue(html.contains("id='aiHistoryList'"))
        assertTrue(html.contains("id='newAiChatBtn'"))
        assertFalse(html.contains("Suggested prompts"))
        assertFalse(html.contains("data-prompt="))
        assertTrue(frontend.contains("loadChatHistory"))
        assertTrue(frontend.contains("loadConversation"))
        assertTrue(frontend.contains("startNewChat"))
        assertTrue(routes.contains("get(\"/api/ai/conversations\")"))
        assertTrue(routes.contains("get(\"/api/ai/conversations/{sessionId}\")"))
        assertTrue(repository.contains("fun listSessions(userId: Int"))
        assertTrue(repository.contains("AIConversationMessagesTable.userId eq userId"))
    }
}

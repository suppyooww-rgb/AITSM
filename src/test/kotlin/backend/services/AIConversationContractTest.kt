package backend.services

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AIConversationContractTest {
    private val chatService = Files.readString(
        Path.of("src/main/kotlin/backend/services/ai/AIChatService.kt")
    )

    @Test
    fun `vague issues require clarification before troubleshooting`() {
        assertTrue(chatService.contains("ask up to three focused clarifying questions and stop"))
        assertTrue(chatService.contains("Do not provide likely causes, a generic checklist, or a resolution in the same response"))
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
    fun `assistant empty state prepares users for follow up questions`() {
        val frontend = Files.readString(
            Path.of("src/main/resources/static/frontend/ai-assistant/js/ai-assistant.js")
        )

        assertTrue(frontend.contains("The assistant may ask a few questions before suggesting the next step"))
    }
}

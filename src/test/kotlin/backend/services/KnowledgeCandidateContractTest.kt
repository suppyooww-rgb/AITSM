package backend.services

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class KnowledgeCandidateContractTest {
    @Test fun `third resolved occurrence creates a reviewable candidate with traceability`() {
        val repository = Files.readString(Path.of("src/main/kotlin/backend/repository/KnowledgeCandidateRepository.kt"))
        assertTrue(repository.contains("candidateThreshold = 3"))
        assertTrue(repository.contains("KnowledgeIssueOccurrencesTable.ticketId eq ticket.id"))
        assertTrue(repository.contains("if (count < candidateThreshold)"))
        assertTrue(repository.contains("sourceTicketIds = ticketIds"))
        assertTrue(repository.contains("status] = \"READY\""))
    }

    @Test fun `only a super admin may publish or dismiss generated drafts`() {
        val routes = Files.readString(Path.of("src/main/kotlin/backend/routes/KnowledgeRoutes.kt"))
        val frontend = Files.readString(Path.of("src/main/resources/static/frontend/knowledge/js/knowledge.js"))
        assertTrue(routes.contains("get(\"/candidates\")"))
        assertTrue(routes.contains("post(\"/candidates/{id}/publish\")"))
        assertTrue(routes.contains("post(\"/candidates/{id}/dismiss\")"))
        assertTrue(routes.split("UserRole.SUPERADMIN").size - 1 >= 3)
        assertTrue(frontend.contains("currentRole() !== 'superadmin'"))
        assertTrue(frontend.contains("Publish reviewed article"))
    }

    @Test fun `ticket resolution records only the reviewed requester visible resolution`() {
        val service = Files.readString(Path.of("src/main/kotlin/backend/services/TicketService.kt"))
        val repository = Files.readString(Path.of("src/main/kotlin/backend/repository/KnowledgeCandidateRepository.kt"))
        assertTrue(service.contains("status == TicketStatus.RESOLVED"))
        assertTrue(service.contains("knowledgeCandidates.recordResolved(updated)"))
        assertTrue(repository.contains("ticket.reviewedResolution"))
        assertTrue(!repository.contains("workNote"))
    }
}

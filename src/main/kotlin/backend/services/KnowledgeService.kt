package backend.services

import backend.models.KnowledgeRequest
import backend.models.KnowledgePublicationResponse
import backend.repository.AuditRepository
import backend.repository.KnowledgeCandidateRepository
import backend.repository.KnowledgeRepository

class KnowledgeService(
    private val repository: KnowledgeRepository,
    private val candidateRepository: KnowledgeCandidateRepository,
    private val auditRepository: AuditRepository
) {
    fun list(limit: Int, offset: Long) = repository.list(limit, offset)
    fun create(req: KnowledgeRequest, userId: Int?) = repository.create(req).also { auditRepository.log(userId, "Created article #${it.id}", "knowledge_articles") }
    fun update(id: Int, req: KnowledgeRequest, userId: Int?) = repository.update(id, req).also { auditRepository.log(userId, "Updated article #$id", "knowledge_articles") }
    fun delete(id: Int, userId: Int?): Boolean = repository.delete(id).also { deleted ->
        if (deleted) auditRepository.log(userId, "Deleted article #$id", "knowledge_articles")
    }

    fun candidates() = candidateRepository.listReady()

    fun publishCandidate(id: Int, request: KnowledgeRequest, userId: Int): KnowledgePublicationResponse? {
        val normalized = validate(request)
        return candidateRepository.publish(id, normalized, userId)?.let { (candidate, article) ->
            auditRepository.log(userId, "Published knowledge candidate #$id as article #${article.id}", "knowledge_candidates")
            KnowledgePublicationResponse(candidate, article)
        }
    }

    fun dismissCandidate(id: Int, userId: Int) = candidateRepository.dismiss(id, userId)?.also {
        auditRepository.log(userId, "Dismissed knowledge candidate #$id", "knowledge_candidates")
    }

    private fun validate(request: KnowledgeRequest): KnowledgeRequest {
        val title = request.title.trim()
        val content = request.content.trim()
        val category = request.category.trim().ifBlank { "General" }
        require(title.length in 5..200) { "Article title must contain 5 to 200 characters" }
        require(content.length in 10..10_000) { "Article content must contain 10 to 10,000 characters" }
        require(category.length <= 100) { "Article category must not exceed 100 characters" }
        return KnowledgeRequest(title, content, category)
    }
}

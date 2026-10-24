package backend.repository

import backend.config.KnowledgeArticlesTable
import backend.config.KnowledgeCandidatesTable
import backend.config.KnowledgeIssueOccurrencesTable
import backend.config.KnowledgeIssuePatternsTable
import backend.models.KnowledgeArticle
import backend.models.KnowledgeCandidate
import backend.models.KnowledgeRequest
import backend.models.Ticket
import backend.services.RecurringIssueMatcher
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class KnowledgeCandidateRepository {
    private val candidateThreshold = 3

    fun recordResolved(ticket: Ticket): KnowledgeCandidate? = transaction {
        val resolution = ticket.reviewedResolution?.trim()?.takeIf { it.isNotBlank() } ?: return@transaction null
        if (KnowledgeIssueOccurrencesTable.selectAll().where { KnowledgeIssueOccurrencesTable.ticketId eq ticket.id }.any()) {
            return@transaction null
        }

        val terms = RecurringIssueMatcher.normalizedTerms(ticket.title)
        if (terms.isEmpty()) return@transaction null
        val category = ticket.category.trim().ifBlank { "General" }.take(100)
        val patterns = KnowledgeIssuePatternsTable.selectAll()
            .where { KnowledgeIssuePatternsTable.category eq category }
            .toList()
        val best = patterns
            .map { row -> row to RecurringIssueMatcher.similarity(terms, decodeTerms(row[KnowledgeIssuePatternsTable.normalizedTerms])) }
            .filter { (row, _) -> RecurringIssueMatcher.isSimilar(terms, decodeTerms(row[KnowledgeIssuePatternsTable.normalizedTerms])) }
            .maxByOrNull { it.second }
            ?.first

        val now = LocalDateTime.now()
        val patternId = best?.get(KnowledgeIssuePatternsTable.id) ?: KnowledgeIssuePatternsTable.insert {
            it[KnowledgeIssuePatternsTable.category] = category
            it[normalizedTerms] = terms.sorted().joinToString(" ")
            it[representativeTitle] = ticket.title.trim().take(200)
            it[createdAt] = now
            it[updatedAt] = now
        }[KnowledgeIssuePatternsTable.id]

        KnowledgeIssueOccurrencesTable.insert {
            it[KnowledgeIssueOccurrencesTable.patternId] = patternId
            it[ticketId] = ticket.id
            it[KnowledgeIssueOccurrencesTable.resolution] = resolution.take(10_000)
            it[createdAt] = now
        }
        KnowledgeIssuePatternsTable.update({ KnowledgeIssuePatternsTable.id eq patternId }) { it[updatedAt] = now }

        val count = KnowledgeIssueOccurrencesTable.selectAll()
            .where { KnowledgeIssueOccurrencesTable.patternId eq patternId }
            .count().toInt()
        if (count < candidateThreshold) return@transaction null

        val existingCandidate = KnowledgeCandidatesTable.selectAll()
            .where { KnowledgeCandidatesTable.patternId eq patternId }
            .singleOrNull()
        if (existingCandidate == null) {
            val representativeTitle = (best?.get(KnowledgeIssuePatternsTable.representativeTitle) ?: ticket.title).trim().take(200)
            val candidateId = KnowledgeCandidatesTable.insert {
                it[KnowledgeCandidatesTable.patternId] = patternId
                it[title] = representativeTitle
                it[content] = resolution.take(10_000)
                it[KnowledgeCandidatesTable.category] = category
                it[occurrenceCount] = count
                it[status] = "READY"
                it[createdAt] = now
                it[updatedAt] = now
            }[KnowledgeCandidatesTable.id]
            return@transaction candidateById(candidateId)
        }

        KnowledgeCandidatesTable.update({ KnowledgeCandidatesTable.id eq existingCandidate[KnowledgeCandidatesTable.id] }) {
            it[occurrenceCount] = count
            it[updatedAt] = now
        }
        candidateById(existingCandidate[KnowledgeCandidatesTable.id])
    }

    fun listReady(): List<KnowledgeCandidate> = transaction {
        KnowledgeCandidatesTable.selectAll()
            .where { KnowledgeCandidatesTable.status eq "READY" }
            .orderBy(KnowledgeCandidatesTable.updatedAt to SortOrder.DESC)
            .map(::toCandidate)
    }

    fun publish(id: Int, request: KnowledgeRequest, reviewerId: Int): Pair<KnowledgeCandidate, KnowledgeArticle>? = transaction {
        val candidate = KnowledgeCandidatesTable.selectAll().where {
            (KnowledgeCandidatesTable.id eq id) and (KnowledgeCandidatesTable.status eq "READY")
        }.singleOrNull() ?: return@transaction null
        val now = LocalDateTime.now()
        val articleId = KnowledgeArticlesTable.insert {
            it[title] = request.title
            it[content] = request.content
            it[category] = request.category
            it[createdAt] = now
        }[KnowledgeArticlesTable.id]
        KnowledgeCandidatesTable.update({ KnowledgeCandidatesTable.id eq id }) {
            it[status] = "PUBLISHED"
            it[publishedArticleId] = articleId
            it[updatedAt] = now
            it[reviewedAt] = now
            it[reviewedBy] = reviewerId
        }
        val updatedCandidate = candidateById(id) ?: return@transaction null
        val article = KnowledgeArticlesTable.selectAll().where { KnowledgeArticlesTable.id eq articleId }.single().let(::toArticle)
        updatedCandidate to article
    }

    fun dismiss(id: Int, reviewerId: Int): KnowledgeCandidate? = transaction {
        val now = LocalDateTime.now()
        val updated = KnowledgeCandidatesTable.update({
            (KnowledgeCandidatesTable.id eq id) and (KnowledgeCandidatesTable.status eq "READY")
        }) {
            it[status] = "DISMISSED"
            it[updatedAt] = now
            it[reviewedAt] = now
            it[reviewedBy] = reviewerId
        }
        if (updated == 0) null else candidateById(id)
    }

    private fun candidateById(id: Int): KnowledgeCandidate? = KnowledgeCandidatesTable.selectAll()
        .where { KnowledgeCandidatesTable.id eq id }
        .singleOrNull()?.let(::toCandidate)

    private fun toCandidate(row: ResultRow): KnowledgeCandidate {
        val patternId = row[KnowledgeCandidatesTable.patternId]
        val ticketIds = KnowledgeIssueOccurrencesTable.selectAll()
            .where { KnowledgeIssueOccurrencesTable.patternId eq patternId }
            .orderBy(KnowledgeIssueOccurrencesTable.createdAt to SortOrder.ASC)
            .map { it[KnowledgeIssueOccurrencesTable.ticketId] }
        return KnowledgeCandidate(
            id = row[KnowledgeCandidatesTable.id],
            title = row[KnowledgeCandidatesTable.title],
            content = row[KnowledgeCandidatesTable.content],
            category = row[KnowledgeCandidatesTable.category],
            occurrenceCount = row[KnowledgeCandidatesTable.occurrenceCount],
            sourceTicketIds = ticketIds,
            status = row[KnowledgeCandidatesTable.status],
            publishedArticleId = row[KnowledgeCandidatesTable.publishedArticleId],
            createdAt = row[KnowledgeCandidatesTable.createdAt].toString(),
            updatedAt = row[KnowledgeCandidatesTable.updatedAt].toString(),
            reviewedAt = row[KnowledgeCandidatesTable.reviewedAt]?.toString()
        )
    }

    private fun toArticle(row: ResultRow) = KnowledgeArticle(
        id = row[KnowledgeArticlesTable.id],
        title = row[KnowledgeArticlesTable.title],
        content = row[KnowledgeArticlesTable.content],
        category = row[KnowledgeArticlesTable.category],
        createdAt = row[KnowledgeArticlesTable.createdAt].toString()
    )

    private fun decodeTerms(value: String): Set<String> = value.split(' ').filter(String::isNotBlank).toSet()
}

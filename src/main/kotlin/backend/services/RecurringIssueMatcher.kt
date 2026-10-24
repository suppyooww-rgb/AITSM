package backend.services

import java.util.Locale

object RecurringIssueMatcher {
    private val stopWords = setOf(
        "a", "an", "and", "are", "at", "for", "from", "in", "is", "it", "my", "of", "on", "the", "to", "with"
    )

    fun normalizedTerms(title: String): Set<String> = title
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .split(Regex("\\s+"))
        .asSequence()
        .filter { it.length >= 2 && it !in stopWords }
        .map(::stem)
        .filter { it.isNotBlank() }
        .toSet()

    fun similarity(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val union = left union right
        return (left intersect right).size.toDouble() / union.size.toDouble()
    }

    fun isSimilar(left: Set<String>, right: Set<String>): Boolean {
        if (left == right && left.isNotEmpty()) return true
        if (minOf(left.size, right.size) < 2) return false
        return similarity(left, right) >= 0.60
    }

    private fun stem(token: String): String = when {
        token.length > 5 && token.endsWith("ing") -> token.dropLast(3)
        token.length > 4 && token.endsWith("ed") -> token.dropLast(2)
        token.length > 4 && token.endsWith("es") -> token.dropLast(2)
        token.length > 3 && token.endsWith("s") -> token.dropLast(1)
        else -> token
    }
}

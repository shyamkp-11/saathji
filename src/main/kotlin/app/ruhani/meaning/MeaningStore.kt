package app.ruhani.meaning

import app.ruhani.model.MeaningUpvoteEntity
import app.ruhani.model.WordEntryEntity
import app.ruhani.model.WordMeaningEntity
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class MeaningContribution(
    val word: String,
    val meaning: WordMeaningEntity,
)

@Component
@Transactional
class MeaningStore(
    private val entries: WordEntryRepository,
    private val meanings: WordMeaningRepository,
    private val upvotes: MeaningUpvoteRepository,
) {

    fun getOrCreateEntry(word: String, languageCode: String): WordEntryEntity {
        val normalized = WordNormalizer.canonicalize(word, languageCode)
        entries.findByNormalizedFormAndLanguageCode(normalized, languageCode)?.let { return it }
        // Concurrent inserts on the same (word, lang) race against the unique
        // index — catch and re-read so callers always get a managed entity.
        val stem = WordNormalizer.stem(normalized, languageCode)
        return try {
            entries.save(
                WordEntryEntity(
                    normalizedForm = normalized,
                    languageCode = languageCode,
                    stem = stem,
                )
            )
        } catch (_: DataIntegrityViolationException) {
            entries.findByNormalizedFormAndLanguageCode(normalized, languageCode)!!
        }
    }

    @Transactional(readOnly = true)
    fun findEntryByWord(word: String, languageCode: String): WordEntryEntity? =
        entries.findByNormalizedFormAndLanguageCode(
            WordNormalizer.canonicalize(word, languageCode),
            languageCode,
        )

    @Transactional(readOnly = true)
    fun getMeanings(wordEntryId: String): List<WordMeaningEntity> =
        meanings.findByWordEntryIdOrderByUpvoteCountDesc(wordEntryId)

    fun addMeaning(wordEntryId: String, text: String, authorId: String): WordMeaningEntity =
        meanings.save(
            WordMeaningEntity(wordEntryId = wordEntryId, text = text, authorId = authorId)
        )

    fun findMeaning(id: String): WordMeaningEntity? = meanings.findById(id).orElse(null)

    @Transactional(readOnly = true)
    fun meaningsByAuthor(authorId: String): List<MeaningContribution> =
        meanings.findByAuthorIdOrderByCreatedAtDesc(authorId).mapNotNull { meaning ->
            val entry = entries.findById(meaning.wordEntryId).orElse(null) ?: return@mapNotNull null
            MeaningContribution(word = entry.normalizedForm, meaning = meaning)
        }

    fun deleteMeaning(id: String) {
        upvotes.deleteByMeaningId(id)
        meanings.deleteById(id)
    }

    /**
     * Atomically flips the upvote state for (meaningId, userId) and adjusts the
     * denormalized counter on word_meanings. Returns the post-toggle entity, or
     * null if the meaning doesn't exist.
     */
    fun toggleUpvote(meaningId: String, userId: String): WordMeaningEntity? {
        val meaning = meanings.findById(meaningId).orElse(null) ?: return null
        val alreadyUpvoted = upvotes.existsByMeaningIdAndUserId(meaningId, userId)
        if (alreadyUpvoted) {
            upvotes.deleteOne(meaningId, userId)
            meanings.adjustUpvoteCount(meaningId, -1)
            meaning.upvoteCount -= 1
        } else {
            upvotes.save(MeaningUpvoteEntity(meaningId = meaningId, userId = userId))
            meanings.adjustUpvoteCount(meaningId, 1)
            meaning.upvoteCount += 1
        }
        return meaning
    }

    @Transactional(readOnly = true)
    fun hasUpvoted(meaningId: String, userId: String): Boolean =
        upvotes.existsByMeaningIdAndUserId(meaningId, userId)

    /** Subset of [wordEntryIds] that has at least one meaning. */
    @Transactional(readOnly = true)
    fun wordEntryIdsWithMeanings(wordEntryIds: Collection<String>): Set<String> =
        if (wordEntryIds.isEmpty()) emptySet()
        else meanings.findWordEntryIdsWithMeanings(wordEntryIds).toSet()

    /**
     * Suggest other WordEntries that share the same stem as [word] in
     * [languageCode], excluding the (canonicalised) word itself. Used by
     * the dictionary screen to surface morphological relatives like
     * अपार and अपारे.
     */
    @Transactional(readOnly = true)
    fun suggestRelated(word: String, languageCode: String, limit: Int): List<Suggestion> {
        val canonical = WordNormalizer.canonicalize(word, languageCode)
        val stem = WordNormalizer.stem(canonical, languageCode)
        if (stem.isBlank()) return emptyList()
        val current = entries.findByNormalizedFormAndLanguageCode(canonical, languageCode)
        val candidates = entries.findRelatedByStem(
            stem = stem,
            lang = languageCode,
            excludeId = current?.id ?: "",
            pageable = org.springframework.data.domain.PageRequest.of(0, limit),
        )
        return candidates.map { entry ->
            Suggestion(
                word = entry.normalizedForm,
                meaningCount = meanings.findByWordEntryIdOrderByUpvoteCountDesc(entry.id).size,
            )
        }
    }
}

data class Suggestion(val word: String, val meaningCount: Int)

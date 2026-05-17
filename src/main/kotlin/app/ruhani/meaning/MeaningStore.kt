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
        val normalized = word.lowercase()
        entries.findByNormalizedFormAndLanguageCode(normalized, languageCode)?.let { return it }
        // Concurrent inserts on the same (word, lang) race against the unique
        // index — catch and re-read so callers always get a managed entity.
        return try {
            entries.save(WordEntryEntity(normalizedForm = normalized, languageCode = languageCode))
        } catch (_: DataIntegrityViolationException) {
            entries.findByNormalizedFormAndLanguageCode(normalized, languageCode)!!
        }
    }

    @Transactional(readOnly = true)
    fun findEntryByWord(word: String, languageCode: String): WordEntryEntity? =
        entries.findByNormalizedFormAndLanguageCode(word.lowercase(), languageCode)

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
}

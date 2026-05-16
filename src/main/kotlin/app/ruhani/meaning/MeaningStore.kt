package app.ruhani.meaning

import app.ruhani.model.WordEntryEntity
import app.ruhani.model.WordMeaningEntity
import org.springframework.stereotype.Component
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class MeaningStore {
    private val entries = ConcurrentHashMap<String, WordEntryEntity>()         // id → entity
    private val entryByKey = ConcurrentHashMap<String, String>()               // "norm::lang" → id
    private val meanings = ConcurrentHashMap<String, WordMeaningEntity>()      // id → entity
    private val meaningsByEntry = ConcurrentHashMap<String, MutableSet<String>>() // entryId → ids

    fun getOrCreateEntry(word: String, languageCode: String): WordEntryEntity {
        val normalized = word.lowercase()
        val key = "$normalized::$languageCode"
        val id = entryByKey.getOrPut(key) { UUID.randomUUID().toString() }
        return entries.getOrPut(id) {
            WordEntryEntity(id = id, normalizedForm = normalized, languageCode = languageCode)
        }
    }

    fun findEntryByWord(word: String, languageCode: String): WordEntryEntity? {
        val key = "${word.lowercase()}::$languageCode"
        return entryByKey[key]?.let { entries[it] }
    }

    fun getMeanings(wordEntryId: String): List<WordMeaningEntity> =
        (meaningsByEntry[wordEntryId] ?: emptySet())
            .mapNotNull { meanings[it] }
            .sortedByDescending { it.upvoteCount }

    fun addMeaning(wordEntryId: String, text: String, authorId: String): WordMeaningEntity {
        val meaning = WordMeaningEntity(wordEntryId = wordEntryId, text = text, authorId = authorId)
        meanings[meaning.id] = meaning
        meaningsByEntry
            .getOrPut(wordEntryId) { Collections.newSetFromMap(ConcurrentHashMap()) }
            .add(meaning.id)
        return meaning
    }

    fun findMeaning(id: String): WordMeaningEntity? = meanings[id]

    fun toggleUpvote(meaningId: String, userId: String): WordMeaningEntity? {
        val meaning = meanings[meaningId] ?: return null
        synchronized(meaning) {
            if (meaning.upvoterIds.add(userId)) meaning.upvoteCount++
            else { meaning.upvoterIds.remove(userId); meaning.upvoteCount-- }
        }
        return meaning
    }
}

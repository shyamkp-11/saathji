package app.ruhani.model

import java.time.Instant
import java.util.UUID

data class UserEntity(
    val id: String = UUID.randomUUID().toString(),
    val email: String,
    var handle: String? = null,
    var bio: String? = null,
    val createdAt: Instant = Instant.now(),
)

data class PostEntity(
    val id: String = UUID.randomUUID().toString(),
    val authorId: String,
    val poetId: String? = null,
    val languageCode: String,
    val form: String,
    val tags: MutableList<String> = mutableListOf(),
    var status: String = "DRAFT",
    val createdAt: Instant = Instant.now(),
    var publishedAt: Instant? = null,
    var summary: String? = null,
    var editsLocked: Boolean = false,
    val lines: MutableList<LineEntity> = mutableListOf(),
)

data class LineEntity(
    val id: String = UUID.randomUUID().toString(),
    val postId: String,
    val ordinal: Int,
    var text: String,
    val transliterations: MutableMap<String, String> = mutableMapOf(),
    var summary: String? = null,
    val tokens: MutableList<TokenEntity> = mutableListOf(),
)

data class TokenEntity(
    val id: String = UUID.randomUUID().toString(),
    val lineId: String,
    val ordinal: Int,
    val text: String,
    val wordEntryId: String,
)

data class WordEntryEntity(
    val id: String = UUID.randomUUID().toString(),
    val normalizedForm: String,
    val languageCode: String,
)

data class WordMeaningEntity(
    val id: String = UUID.randomUUID().toString(),
    val wordEntryId: String,
    val text: String,
    val authorId: String,
    val createdAt: Instant = Instant.now(),
    var upvoteCount: Int = 0,
    val upvoterIds: MutableSet<String> = mutableSetOf(),
)

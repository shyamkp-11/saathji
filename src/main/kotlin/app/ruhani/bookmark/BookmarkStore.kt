package app.ruhani.bookmark

import app.ruhani.model.BookmarkEntity
import app.ruhani.model.PostEntity
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional
class BookmarkStore(private val repo: BookmarkRepository) {

    fun add(userId: String, postId: String) {
        try {
            repo.save(BookmarkEntity(userId = userId, postId = postId))
        } catch (_: DataIntegrityViolationException) {
            // Already bookmarked — idempotent.
        }
    }

    fun remove(userId: String, postId: String) {
        repo.deleteOne(userId, postId)
    }

    @Transactional(readOnly = true)
    fun isBookmarked(userId: String, postId: String): Boolean =
        repo.existsByUserIdAndPostId(userId, postId)

    @Transactional(readOnly = true)
    fun page(userId: String, cursor: String?, pageSize: Int = 20): Pair<List<PostEntity>, String?> {
        val cutoff = cursor?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
        val rows = repo.bookmarkedPosts(userId, cutoff, PageRequest.of(0, pageSize + 1))
        val hasMore = rows.size > pageSize
        val page = if (hasMore) rows.dropLast(1) else rows
        val nextCursor = if (hasMore) {
            val lastPostId = page.last().id
            repo.bookmarkCreatedAt(userId, lastPostId)?.toEpochMilli()?.toString()
        } else null
        return page to nextCursor
    }
}

package app.ruhani.post

import app.ruhani.model.PostEntity
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional
class PostStore(private val repo: PostRepository) {

    @Transactional(readOnly = true)
    fun findById(id: String): PostEntity? =
        repo.findById(id).orElse(null)?.takeIf { it.deletedAt == null }

    @Transactional(readOnly = true)
    fun findDeleted(id: String): PostEntity? =
        repo.findById(id).orElse(null)?.takeIf { it.deletedAt != null }

    fun save(post: PostEntity): PostEntity = repo.save(post)

    /** Soft-delete so the Undo flow can restore within the snackbar window. */
    fun delete(id: String) {
        val post = repo.findById(id).orElse(null) ?: return
        post.deletedAt = Instant.now()
        repo.save(post)
    }

    fun restore(id: String): PostEntity? {
        val post = repo.findById(id).orElse(null) ?: return null
        if (post.deletedAt == null) return null
        post.deletedAt = null
        return repo.save(post)
    }

    @Transactional(readOnly = true)
    fun feed(
        cursor: String?,
        form: String?,
        lang: String?,
        pageSize: Int = 20,
    ): Pair<List<PostEntity>, String?> {
        val cutoff = cursor?.toInstantOrNull()
        // pageSize + 1 so we can tell whether there's another page without an extra count query.
        val rows = repo.feedPage(cutoff, form, lang, PageRequest.of(0, pageSize + 1))
        return paginate(rows, pageSize)
    }

    @Transactional(readOnly = true)
    fun byAuthor(authorId: String, status: String?): List<PostEntity> =
        repo.byAuthor(authorId, status)

    @Transactional(readOnly = true)
    fun search(
        query: String,
        form: String?,
        lang: String?,
        tag: String?,
        cursor: String?,
        pageSize: Int = 20,
    ): Pair<List<PostEntity>, String?> {
        val cutoff = cursor?.toInstantOrNull()
        val rows = repo.search(query, form, lang, tag, cutoff, PageRequest.of(0, pageSize + 1))
        return paginate(rows, pageSize)
    }

    private fun paginate(rows: List<PostEntity>, pageSize: Int): Pair<List<PostEntity>, String?> {
        val hasMore = rows.size > pageSize
        val page = if (hasMore) rows.dropLast(1) else rows
        return page to if (hasMore) page.last().createdAt.toEpochMilli().toString() else null
    }

    private fun String.toInstantOrNull(): Instant? =
        toLongOrNull()?.let { Instant.ofEpochMilli(it) }
}

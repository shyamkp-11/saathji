package app.ruhani.post

import app.ruhani.model.LineEntity
import app.ruhani.model.PostEntity
import app.ruhani.model.TokenEntity
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

    /**
     * Deep-copy [source] into a new DRAFT row that points back at it via
     * `parent_post_id`. The author edits this draft; on publish the parent
     * transitions to SUPERSEDED via [markSuperseded].
     *
     * We copy lines + transliterations + tokens; tags are copied too.
     * `wordEntryId` is reused — meanings live on the word, not the post,
     * so the new version inherits them automatically. Same for any
     * `editsLocked` flag (always false on a fresh draft).
     */
    fun snapshot(source: PostEntity): PostEntity {
        val draft = PostEntity(
            authorId = source.authorId,
            poetId = source.poetId,
            parentPostId = source.id,
            version = source.version + 1,
            languageCode = source.languageCode,
            form = source.form,
            title = source.title,
            status = "DRAFT",
            summary = source.summary,
        )
        draft.tags.addAll(source.tags)
        source.lines.forEach { srcLine ->
            val newLine = LineEntity(
                postId = draft.id,
                ordinal = srcLine.ordinal,
                text = srcLine.text,
                summary = srcLine.summary,
            )
            newLine.transliterations.putAll(srcLine.transliterations)
            srcLine.tokens.forEach { srcToken ->
                newLine.tokens.add(
                    TokenEntity(
                        lineId = newLine.id,
                        ordinal = srcToken.ordinal,
                        text = srcToken.text,
                        wordEntryId = srcToken.wordEntryId,
                    )
                )
            }
            draft.lines.add(newLine)
        }
        return repo.save(draft)
    }

    /**
     * Mark [parentId]'s post as SUPERSEDED if it was PUBLISHED. Called when
     * a newer version publishes. Does nothing if the parent isn't found, is
     * already SUPERSEDED, or was still a draft (impossible in practice).
     */
    fun markSuperseded(parentId: String) {
        val parent = repo.findById(parentId).orElse(null) ?: return
        if (parent.status == "PUBLISHED") {
            parent.status = "SUPERSEDED"
            repo.save(parent)
        }
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

package app.ruhani.list

import app.ruhani.model.ListItemEntity
import app.ruhani.model.PostListEntity
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional
class PostListStore(
    private val lists: PostListRepository,
    private val items: ListItemRepository,
) {

    // ── reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun findById(id: String): PostListEntity? = lists.findById(id).orElse(null)

    @Transactional(readOnly = true)
    fun findBySlug(slug: String): PostListEntity? = lists.findBySlug(slug)

    @Transactional(readOnly = true)
    fun publishedPage(cursor: String?, pageSize: Int = 20): Pair<List<PostListEntity>, String?> {
        val cutoff = cursor?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
        val rows = lists.publishedPage(cutoff, PageRequest.of(0, pageSize + 1))
        val hasMore = rows.size > pageSize
        val page = if (hasMore) rows.dropLast(1) else rows
        val nextCursor = if (hasMore) {
            page.last().publishedAt?.toEpochMilli()?.toString()
        } else null
        return page to nextCursor
    }

    @Transactional(readOnly = true)
    fun byEditor(editorId: String): List<PostListEntity> = lists.byEditor(editorId)

    @Transactional(readOnly = true)
    fun items(listId: String): List<ListItemEntity> = items.findByListIdOrderByOrdinalAsc(listId)

    @Transactional(readOnly = true)
    fun itemCount(listId: String): Long = items.countByListId(listId)

    // ── writes ───────────────────────────────────────────────────────────────

    fun create(editorId: String, title: String, description: String?, slugHint: String?): PostListEntity {
        val baseSlug = (slugHint?.takeIf { it.isNotBlank() } ?: title).slugify()
        val slug = uniqueSlug(baseSlug)
        val list = PostListEntity(
            slug = slug,
            title = title,
            description = description,
            status = "DRAFT",
            editorId = editorId,
        )
        return lists.save(list)
    }

    fun updateMetadata(list: PostListEntity, title: String, description: String?): PostListEntity {
        list.title = title
        list.description = description
        list.updatedAt = Instant.now()
        return lists.save(list)
    }

    fun publish(list: PostListEntity): PostListEntity {
        if (list.status != "PUBLISHED") {
            list.status = "PUBLISHED"
            list.publishedAt = Instant.now()
        }
        list.updatedAt = Instant.now()
        return lists.save(list)
    }

    fun delete(list: PostListEntity) {
        // list_items cascade-delete via the FK.
        lists.delete(list)
    }

    /**
     * Append a post to the end of the list. Idempotent: returns false when
     * the post is already in the list (caller can map to 409 if desired).
     */
    fun addPost(listId: String, postId: String): Boolean {
        if (items.existsByListIdAndPostId(listId, postId)) return false
        val nextOrdinal = items.maxOrdinal(listId) + 1
        try {
            items.save(ListItemEntity(listId = listId, postId = postId, ordinal = nextOrdinal))
        } catch (_: DataIntegrityViolationException) {
            // Concurrent insert won the race; the row's already there.
            return false
        }
        touchUpdatedAt(listId)
        return true
    }

    /** Returns true when an item was actually removed. */
    fun removePost(listId: String, postId: String): Boolean {
        val removed = items.deleteOne(listId, postId) > 0
        if (removed) touchUpdatedAt(listId)
        return removed
    }

    /**
     * Replace the list's ordering with [postIds] (which must already match
     * the current item set — caller validates membership and reports errors).
     * We wipe and re-insert because Hibernate has nothing clever to do with
     * an idClass-keyed entity when the composite values aren't changing.
     */
    fun reorder(listId: String, postIds: List<String>) {
        items.deleteAllByListId(listId)
        items.flush()  // ensure the deletes hit the DB before the inserts replay (PK collisions otherwise)
        postIds.forEachIndexed { idx, postId ->
            items.save(ListItemEntity(listId = listId, postId = postId, ordinal = idx))
        }
        touchUpdatedAt(listId)
    }

    // ── slug + bookkeeping ───────────────────────────────────────────────────

    private fun touchUpdatedAt(listId: String) {
        lists.findById(listId).ifPresent {
            it.updatedAt = Instant.now()
            lists.save(it)
        }
    }

    /**
     * Slugify roughly per URL convention: lowercase, replace non-alnum runs
     * with single hyphens, trim hyphens. Falls back to a UUID-shaped suffix
     * if the input has no alphanumerics at all (e.g. all-emoji title).
     */
    private fun String.slugify(): String {
        val cleaned = lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(96)
        return if (cleaned.isNotEmpty()) cleaned else "list-${System.currentTimeMillis()}"
    }

    /** Append `-2`, `-3`, … until we find a free slug. */
    private fun uniqueSlug(base: String): String {
        if (!lists.existsBySlug(base)) return base
        var n = 2
        while (lists.existsBySlug("$base-$n")) n++
        return "$base-$n"
    }
}

/** Visible to the controller — same package, no need to expose externally. */
private fun ListItemRepository.flush() {
    // JpaRepository extends CrudRepository, which doesn't expose flush().
    // The cast is safe because Spring Data JPA's runtime proxy is a SimpleJpaRepository.
    @Suppress("USELESS_CAST")
    (this as org.springframework.data.jpa.repository.JpaRepository<*, *>).flush()
}

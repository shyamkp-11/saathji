package app.ruhani.post

import app.ruhani.model.PostEntity
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class PostStore {
    private val posts = ConcurrentHashMap<String, PostEntity>()
    private val deleted = ConcurrentHashMap<String, PostEntity>()

    fun findById(id: String): PostEntity? = posts[id]

    fun findDeleted(id: String): PostEntity? = deleted[id]

    fun save(post: PostEntity): PostEntity {
        posts[post.id] = post
        return post
    }

    fun delete(id: String) {
        posts.remove(id)?.also { deleted[it.id] = it }
    }

    fun restore(id: String): PostEntity? =
        deleted.remove(id)?.also { posts[it.id] = it }

    fun feed(
        cursor: String?,
        form: String?,
        lang: String?,
        pageSize: Int = 20,
    ): Pair<List<PostEntity>, String?> = page(
        cursor = cursor,
        pageSize = pageSize,
        candidates = posts.values
            .filter { it.status == "PUBLISHED" }
            .filter { form == null || it.form == form }
            .filter { lang == null || it.languageCode == lang },
    )

    fun byAuthor(authorId: String, status: String?): List<PostEntity> =
        posts.values
            .filter { it.authorId == authorId }
            .filter { status == null || it.status == status }
            .sortedByDescending { it.createdAt.toEpochMilli() }

    fun search(
        query: String,
        form: String?,
        lang: String?,
        tag: String?,
        cursor: String?,
        pageSize: Int = 20,
    ): Pair<List<PostEntity>, String?> {
        val q = query.lowercase()
        return page(
            cursor = cursor,
            pageSize = pageSize,
            candidates = posts.values
                .filter { it.status == "PUBLISHED" }
                .filter { form == null || it.form == form }
                .filter { lang == null || it.languageCode == lang }
                .filter { tag == null || it.tags.any { t -> t.equals(tag, ignoreCase = true) } }
                .filter { post ->
                    post.lines.any { line -> line.text.lowercase().contains(q) }
                        || post.summary?.lowercase()?.contains(q) == true
                        || post.tags.any { t -> t.lowercase().contains(q) }
                },
        )
    }

    private fun page(
        cursor: String?,
        pageSize: Int,
        candidates: Iterable<PostEntity>,
    ): Pair<List<PostEntity>, String?> {
        val cutoff = cursor?.toLongOrNull()
        val sorted = candidates.sortedByDescending { it.createdAt.toEpochMilli() }
        val slice = (if (cutoff == null) sorted
        else sorted.filter { it.createdAt.toEpochMilli() < cutoff })
            .take(pageSize + 1)
        val hasMore = slice.size > pageSize
        val result = if (hasMore) slice.dropLast(1) else slice
        return result to if (hasMore) result.last().createdAt.toEpochMilli().toString() else null
    }
}

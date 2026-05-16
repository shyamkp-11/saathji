package app.ruhani.bookmark

import app.ruhani.auth.UserStore
import app.ruhani.model.FeedItemDto
import app.ruhani.model.FeedPageDto
import app.ruhani.model.toDto
import app.ruhani.post.PostStore
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/bookmarks")
class BookmarkController(
    private val bookmarkStore: BookmarkStore,
    private val postStore: PostStore,
    private val userStore: UserStore,
) {
    @PostMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun add(@PathVariable postId: String, auth: Authentication) {
        bookmarkStore.add(auth.name, postId)
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(@PathVariable postId: String, auth: Authentication) {
        bookmarkStore.remove(auth.name, postId)
    }

    @GetMapping
    fun list(@RequestParam cursor: String?, auth: Authentication): FeedPageDto {
        val pageSize = 20
        val cutoff = cursor?.toLongOrNull()

        val posts = bookmarkStore.getPostIds(auth.name)
            .mapNotNull { postStore.findById(it) }
            .filter { it.status == "PUBLISHED" }
            .sortedByDescending { it.createdAt.toEpochMilli() }
            .let { sorted ->
                if (cutoff == null) sorted.take(pageSize + 1)
                else sorted.filter { it.createdAt.toEpochMilli() < cutoff }.take(pageSize + 1)
            }

        val hasMore = posts.size > pageSize
        val result = if (hasMore) posts.dropLast(1) else posts
        return FeedPageDto(
            items = result.map { post ->
                FeedItemDto(
                    post = post.toDto(),
                    authorHandle = userStore.findById(post.authorId)?.handle ?: post.authorId,
                )
            },
            nextCursor = if (hasMore) result.last().createdAt.toEpochMilli().toString() else null,
        )
    }
}

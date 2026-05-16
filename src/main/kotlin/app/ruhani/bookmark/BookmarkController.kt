package app.ruhani.bookmark

import app.ruhani.auth.UserStore
import app.ruhani.model.FeedItemDto
import app.ruhani.model.FeedPageDto
import app.ruhani.model.toDto
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/bookmarks")
class BookmarkController(
    private val bookmarkStore: BookmarkStore,
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
        val (posts, next) = bookmarkStore.page(auth.name, cursor)
        return FeedPageDto(
            items = posts.map { post ->
                FeedItemDto(
                    post = post.toDto(),
                    authorHandle = userStore.findById(post.authorId)?.handle ?: post.authorId,
                )
            },
            nextCursor = next,
        )
    }
}

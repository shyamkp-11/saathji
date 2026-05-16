package app.ruhani.search

import app.ruhani.auth.UserStore
import app.ruhani.model.FeedItemDto
import app.ruhani.model.FeedPageDto
import app.ruhani.model.toDto
import app.ruhani.post.PostStore
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class SearchController(
    private val postStore: PostStore,
    private val userStore: UserStore,
) {
    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam form: String?,
        @RequestParam lang: String?,
        @RequestParam tag: String?,
        @RequestParam cursor: String?,
    ): FeedPageDto {
        val (posts, next) = postStore.search(q, form, lang, tag, cursor)
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

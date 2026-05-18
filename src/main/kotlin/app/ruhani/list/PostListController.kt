package app.ruhani.list

import app.ruhani.auth.UserStore
import app.ruhani.model.AddListItemRequest
import app.ruhani.model.CreatePostListRequest
import app.ruhani.model.FeedItemDto
import app.ruhani.model.PostListDto
import app.ruhani.model.PostListSummaryDto
import app.ruhani.model.PostListsPageDto
import app.ruhani.model.ReorderListRequest
import app.ruhani.model.UpdatePostListRequest
import app.ruhani.model.PostListEntity
import app.ruhani.model.toDto
import app.ruhani.post.PostStore
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

/**
 * Editorial lists of posts. Curated and ordered by users flagged as
 * moderators (users.is_moderator). Anyone can read PUBLISHED lists;
 * DRAFTs are visible only to their editor.
 *
 * Endpoint map:
 *   GET    /lists                   public browse (PUBLISHED only)
 *   GET    /lists/{slug}            single list + items
 *   POST   /lists                   create (moderator) → DRAFT
 *   PUT    /lists/{slug}            update metadata (moderator + owner)
 *   POST   /lists/{slug}/publish    flip to PUBLISHED
 *   DELETE /lists/{slug}            remove
 *   POST   /lists/{slug}/items      add a post to the end
 *   DELETE /lists/{slug}/items/{postId}
 *   PUT    /lists/{slug}/items/order   reorder (full ordered postId list)
 *   GET    /me/lists                editor's own lists (DRAFT + PUBLISHED)
 */
@RestController
class PostListController(
    private val store: PostListStore,
    private val userStore: UserStore,
    private val postStore: PostStore,
) {

    // ── public reads ─────────────────────────────────────────────────────────

    @GetMapping("/lists")
    fun browse(@RequestParam cursor: String?): PostListsPageDto {
        val (rows, next) = store.publishedPage(cursor)
        return PostListsPageDto(items = rows.map { it.toSummary() }, nextCursor = next)
    }

    @GetMapping("/lists/{slug}")
    fun read(@PathVariable slug: String, auth: Authentication?): PostListDto {
        val list = store.findBySlug(slug)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        // DRAFTs are private to their editor; everyone sees PUBLISHED.
        if (list.status == "DRAFT" && auth?.name != list.editorId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        return list.toFull()
    }

    /** Editor's own lists (any status). */
    @GetMapping("/me/lists")
    fun myLists(auth: Authentication): List<PostListSummaryDto> =
        store.byEditor(auth.name).map { it.toSummary() }

    // ── editor writes ────────────────────────────────────────────────────────

    @PostMapping("/lists")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody req: CreatePostListRequest, auth: Authentication): PostListSummaryDto {
        requireModerator(auth)
        if (req.title.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required")
        }
        val list = store.create(auth.name, req.title.trim(), req.description?.trim(), req.slug?.trim())
        return list.toSummary()
    }

    @PutMapping("/lists/{slug}")
    fun update(@PathVariable slug: String, @RequestBody req: UpdatePostListRequest, auth: Authentication): PostListSummaryDto {
        val list = requireOwnedList(slug, auth)
        if (req.title.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required")
        }
        return store.updateMetadata(list, req.title.trim(), req.description?.trim()).toSummary()
    }

    @PostMapping("/lists/{slug}/publish")
    fun publish(@PathVariable slug: String, auth: Authentication): PostListSummaryDto {
        val list = requireOwnedList(slug, auth)
        return store.publish(list).toSummary()
    }

    @DeleteMapping("/lists/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable slug: String, auth: Authentication) {
        val list = requireOwnedList(slug, auth)
        store.delete(list)
    }

    // ── items ────────────────────────────────────────────────────────────────

    @PostMapping("/lists/{slug}/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun addItem(@PathVariable slug: String, @RequestBody req: AddListItemRequest, auth: Authentication) {
        val list = requireOwnedList(slug, auth)
        // 404 on a missing post is friendlier than letting the FK blow up.
        postStore.findById(req.postId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "post ${req.postId} not found")
        val added = store.addPost(list.id, req.postId)
        if (!added) throw ResponseStatusException(HttpStatus.CONFLICT, "post is already in the list")
    }

    @DeleteMapping("/lists/{slug}/items/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeItem(@PathVariable slug: String, @PathVariable postId: String, auth: Authentication) {
        val list = requireOwnedList(slug, auth)
        val removed = store.removePost(list.id, postId)
        if (!removed) throw ResponseStatusException(HttpStatus.NOT_FOUND, "post not in list")
    }

    @PutMapping("/lists/{slug}/items/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reorder(@PathVariable slug: String, @RequestBody req: ReorderListRequest, auth: Authentication) {
        val list = requireOwnedList(slug, auth)
        val current = store.items(list.id).map { it.postId }.toSet()
        val incoming = req.postIds.toSet()
        if (incoming != current) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "postIds must match the list's current membership exactly (use add/remove first)"
            )
        }
        // Also guard against duplicates inside the same payload.
        if (req.postIds.size != incoming.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "postIds contains duplicates")
        }
        store.reorder(list.id, req.postIds)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun requireModerator(auth: Authentication) {
        val user = userStore.findById(auth.name)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        if (!user.isModerator) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "moderator role required")
        }
    }

    private fun requireOwnedList(slug: String, auth: Authentication): PostListEntity {
        requireModerator(auth)
        val list = store.findBySlug(slug)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (list.editorId != auth.name) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "you don't own this list")
        }
        return list
    }

    private fun PostListEntity.toSummary(): PostListSummaryDto {
        val editorHandle = userStore.findById(editorId)?.handle ?: editorId
        return PostListSummaryDto(
            id = id,
            slug = slug,
            title = title,
            description = description,
            status = status,
            editorId = editorId,
            editorHandle = editorHandle,
            itemCount = store.itemCount(id).toInt(),
            createdAt = createdAt.toString(),
            publishedAt = publishedAt?.toString(),
        )
    }

    private fun PostListEntity.toFull(): PostListDto {
        val editorHandle = userStore.findById(editorId)?.handle ?: editorId
        val items = store.items(id).mapNotNull { item ->
            val post = postStore.findById(item.postId) ?: return@mapNotNull null
            val authorHandle = userStore.findById(post.authorId)?.handle ?: post.authorId
            FeedItemDto(post = post.toDto(), authorHandle = authorHandle)
        }
        return PostListDto(
            id = id,
            slug = slug,
            title = title,
            description = description,
            status = status,
            editorId = editorId,
            editorHandle = editorHandle,
            createdAt = createdAt.toString(),
            publishedAt = publishedAt?.toString(),
            items = items,
        )
    }
}

package app.ruhani.post

import app.ruhani.auth.UserStore
import app.ruhani.meaning.MeaningStore
import app.ruhani.model.*
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestController
class PostController(
    private val postStore: PostStore,
    private val meaningStore: MeaningStore,
    private val userStore: UserStore,
) {

    // ── Feed ──────────────────────────────────────────────────────────────────

    @GetMapping("/posts")
    fun feed(
        @RequestParam cursor: String?,
        @RequestParam form: String?,
        @RequestParam lang: String?,
    ): FeedPageDto {
        val (posts, next) = postStore.feed(cursor, form, lang)
        return FeedPageDto(items = posts.map { it.toFeedItem() }, nextCursor = next)
    }

    // ── Single post ───────────────────────────────────────────────────────────

    @GetMapping("/posts/{id}")
    fun getPost(@PathVariable id: String): PostDto {
        val post = postStore.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val tokenWordEntryIds = post.lines.flatMap { it.tokens }.map { it.wordEntryId }.toSet()
        val withMeanings = meaningStore.wordEntryIdsWithMeanings(tokenWordEntryIds)
        val dto = post.toDto()
        return dto.copy(
            lines = dto.lines.map { line ->
                line.copy(
                    tokens = line.tokens.map { token ->
                        token.copy(hasMeanings = token.wordEntryId in withMeanings)
                    }
                )
            }
        )
    }

    // ── Author posts (profile screen) ─────────────────────────────────────────

    @GetMapping("/authors/{authorId}/posts")
    fun byAuthor(
        @PathVariable authorId: String,
        @RequestParam status: String?,
    ): FeedPageDto {
        val posts = postStore.byAuthor(authorId, status)
        return FeedPageDto(items = posts.map { it.toFeedItem() })
    }

    // ── Draft lifecycle ───────────────────────────────────────────────────────

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    fun createDraft(
        @RequestBody req: CreateDraftRequest,
        auth: Authentication,
    ): PostDto {
        val post = PostEntity(authorId = auth.name, languageCode = req.languageCode, form = req.form)
        req.rawText.split("\n")
            .filter { it.isNotBlank() }
            .forEachIndexed { lineIdx, lineText ->
                val line = LineEntity(postId = post.id, ordinal = lineIdx, text = lineText.trim())
                lineText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    .forEachIndexed { wordIdx, word ->
                        val entry = meaningStore.getOrCreateEntry(word, req.languageCode)
                        line.tokens.add(TokenEntity(lineId = line.id, ordinal = wordIdx, text = word, wordEntryId = entry.id))
                    }
                post.lines.add(line)
            }
        return postStore.save(post).toDto()
    }

    @PutMapping("/posts/{id}/staging")
    fun updateStaging(
        @PathVariable id: String,
        @RequestBody req: UpdateStagingRequest,
        auth: Authentication,
    ): PostDto {
        val post = postStore.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (post.authorId != auth.name) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (post.editsLocked) throw ResponseStatusException(HttpStatus.CONFLICT, "Edits locked after community meanings exist")

        post.title = req.title?.trim()?.takeIf { it.isNotEmpty() }
        post.summary = req.summary
        post.tags.clear()
        post.tags.addAll(req.tags)

        post.lines.clear()
        req.lines.forEachIndexed { idx, dto ->
            val line = LineEntity(postId = post.id, ordinal = idx, text = dto.text)
            line.transliterations.putAll(dto.transliterations)
            line.summary = dto.summary
            dto.tokenTexts.forEachIndexed { tokenIdx, tokenText ->
                val entry = meaningStore.getOrCreateEntry(tokenText, post.languageCode)
                line.tokens.add(TokenEntity(lineId = line.id, ordinal = tokenIdx, text = tokenText, wordEntryId = entry.id))
            }
            post.lines.add(line)
        }
        return postStore.save(post).toDto()
    }

    @PostMapping("/posts/{id}/publish")
    fun publish(@PathVariable id: String, auth: Authentication): PostDto {
        val post = postStore.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (post.authorId != auth.name) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        post.status = "PUBLISHED"
        post.publishedAt = Instant.now()
        val saved = postStore.save(post)
        // Edit publish: demote the previous version so the feed shows only the latest.
        post.parentPostId?.let { postStore.markSuperseded(it) }
        return saved.toDto()
    }

    /**
     * Begin editing a published post — currently always in-place: the post
     * is flipped back to DRAFT, the author edits, and publishing re-PUBLISHEs
     * the same row (no version bump, same id, same slug).
     *
     * Refuses on non-published posts (drafts are already editable in place;
     * superseded versions shouldn't be branched from).
     *
     * Forks-via-[PostStore.snapshot] (which creates a new DRAFT row pointing
     * back at this one and supersedes the parent on publish) are kept around
     * for the eventual post-level comments feature: once a post has any
     * public comment, an edit will fork a new version so commenters' replies
     * stay anchored to the version they actually saw.
     */
    @PostMapping("/posts/{id}/edit")
    @ResponseStatus(HttpStatus.CREATED)
    fun startEdit(@PathVariable id: String, auth: Authentication): PostDto {
        val source = postStore.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (source.authorId != auth.name) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        if (source.status != "PUBLISHED") {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Only published posts can be edited (status was ${source.status})"
            )
        }
        source.status = "DRAFT"
        return postStore.save(source).toDto()
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePost(@PathVariable id: String, auth: Authentication) {
        val post = postStore.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (post.authorId != auth.name) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        postStore.delete(id)
    }

    @PostMapping("/posts/{id}/restore")
    fun restore(@PathVariable id: String, auth: Authentication): PostDto {
        val post = postStore.findDeleted(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (post.authorId != auth.name) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        return postStore.restore(id)!!.toDto()
    }

    // ── Meanings (word-tap) ───────────────────────────────────────────────────

    @GetMapping("/posts/{id}/meanings")
    fun getMeanings(
        @PathVariable id: String,
        @RequestParam word: String,
        auth: Authentication?,
    ): MeaningsBundleDto {
        val post = postStore.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val entry = meaningStore.findEntryByWord(word, post.languageCode)
        val wordMeanings = entry?.let { meaningStore.getMeanings(it.id) } ?: emptyList()
        val viewerId = auth?.name
        return MeaningsBundleDto(
            word = word,
            languageCode = post.languageCode,
            wordMeanings = wordMeanings.map { m ->
                m.toDto(viewerUpvoted = viewerId != null && meaningStore.hasUpvoted(m.id, viewerId))
            },
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun PostEntity.toFeedItem(): FeedItemDto {
        val handle = userStore.findById(authorId)?.handle ?: authorId
        return FeedItemDto(post = toDto(), authorHandle = handle)
    }
}

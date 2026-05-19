package app.ruhani.meaning

import app.ruhani.model.AddMeaningRequest
import app.ruhani.model.MeaningContributionDto
import app.ruhani.model.MeaningContributionsPageDto
import app.ruhani.model.MeaningsBundleDto
import app.ruhani.model.WordMeaningDto
import app.ruhani.model.toDto
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
class MeaningController(private val meaningStore: MeaningStore) {

    /**
     * Look up all meanings for a (word, language) pair regardless of post
     * context. Powers the dictionary-builder screen so users can add
     * meanings to any word without first finding a post that uses it.
     * Anon-readable like the per-post variant.
     */
    @GetMapping("/word-meanings")
    fun getByWord(
        @RequestParam word: String,
        @RequestParam lang: String,
        auth: Authentication?,
    ): MeaningsBundleDto {
        val entry = meaningStore.findEntryByWord(word, lang)
        val list = entry?.let { meaningStore.getMeanings(it.id) } ?: emptyList()
        val viewerId = auth?.name
        return MeaningsBundleDto(
            word = word,
            languageCode = lang,
            wordMeanings = list.map { m ->
                m.toDto(viewerUpvoted = viewerId != null && meaningStore.hasUpvoted(m.id, viewerId))
            },
        )
    }

    @GetMapping("/authors/{authorId}/word-meanings")
    fun meaningsByAuthor(
        @PathVariable authorId: String,
        auth: Authentication,
    ): MeaningContributionsPageDto {
        if (authorId != auth.name) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }

        val items = meaningStore.meaningsByAuthor(authorId).map { contribution ->
            MeaningContributionDto(
                word = contribution.word,
                meaning = contribution.meaning.toDto(
                    viewerUpvoted = meaningStore.hasUpvoted(contribution.meaning.id, auth.name)
                ),
            )
        }
        return MeaningContributionsPageDto(items = items)
    }

    @PostMapping("/word-meanings")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMeaning(
        @RequestBody req: AddMeaningRequest,
        auth: Authentication,
    ): WordMeaningDto {
        val entry = meaningStore.getOrCreateEntry(req.word, req.languageCode)
        val meaning = meaningStore.addMeaning(entry.id, req.text, auth.name)
        // Author hasn't upvoted their own meaning yet.
        return meaning.toDto(viewerUpvoted = false)
    }

    @PostMapping("/word-meanings/{id}/upvote/toggle")
    fun toggleUpvote(
        @PathVariable id: String,
        auth: Authentication,
    ): WordMeaningDto {
        val meaning = meaningStore.toggleUpvote(id, auth.name)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return meaning.toDto(viewerUpvoted = meaningStore.hasUpvoted(meaning.id, auth.name))
    }

    @DeleteMapping("/word-meanings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMeaning(
        @PathVariable id: String,
        auth: Authentication,
    ) {
        val meaning = meaningStore.findMeaning(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (meaning.authorId != auth.name) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
        meaningStore.deleteMeaning(id)
    }
}

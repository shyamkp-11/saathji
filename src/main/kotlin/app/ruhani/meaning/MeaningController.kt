package app.ruhani.meaning

import app.ruhani.model.AddMeaningRequest
import app.ruhani.model.WordMeaningDto
import app.ruhani.model.toDto
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
class MeaningController(private val meaningStore: MeaningStore) {

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
}

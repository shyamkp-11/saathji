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
        return meaningStore.addMeaning(entry.id, req.text, auth.name).toDto(auth.name)
    }

    @PostMapping("/word-meanings/{id}/upvote/toggle")
    fun toggleUpvote(
        @PathVariable id: String,
        auth: Authentication,
    ): WordMeaningDto =
        meaningStore.toggleUpvote(id, auth.name)?.toDto(auth.name)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
}

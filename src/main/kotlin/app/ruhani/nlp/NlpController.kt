package app.ruhani.nlp

import app.ruhani.model.TransliterateBatchRequest
import app.ruhani.model.TransliterateBatchResponse
import app.ruhani.model.TransliterateRequest
import app.ruhani.model.TransliterateResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/nlp")
class NlpController(private val transliterationService: TransliterationService) {

    @PostMapping("/transliterate")
    fun transliterate(@RequestBody req: TransliterateRequest): TransliterateResponse =
        TransliterateResponse(
            results = req.toScripts.associateWith { target ->
                transliterationService.translate(req.text, req.fromScript, target)
            },
        )

    @PostMapping("/transliterate-batch")
    fun transliterateBatch(@RequestBody req: TransliterateBatchRequest): TransliterateBatchResponse =
        TransliterateBatchResponse(
            results = req.lines.map { line ->
                TransliterateResponse(
                    results = req.toScripts.associateWith { target ->
                        transliterationService.translate(line, req.fromScript, target)
                    },
                )
            },
        )
}

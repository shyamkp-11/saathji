package app.ruhani.nlp

import app.ruhani.model.TransliterateBatchRequest
import app.ruhani.model.TransliterateBatchResponse
import app.ruhani.model.TransliterateRequest
import app.ruhani.model.TransliterateResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Stub NLP controller — returns source text as-is for every requested target
 * script. Replace with real transliteration (ICU4J, cloud API, etc.) later.
 */
@RestController
@RequestMapping("/nlp")
class NlpController {

    @PostMapping("/transliterate")
    fun transliterate(@RequestBody req: TransliterateRequest): TransliterateResponse =
        TransliterateResponse(results = req.toScripts.associateWith { req.text })

    @PostMapping("/transliterate-batch")
    fun transliterateBatch(@RequestBody req: TransliterateBatchRequest): TransliterateBatchResponse =
        TransliterateBatchResponse(
            results = req.lines.map { line ->
                TransliterateResponse(results = req.toScripts.associateWith { line })
            },
        )
}

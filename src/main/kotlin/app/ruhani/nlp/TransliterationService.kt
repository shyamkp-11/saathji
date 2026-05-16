package app.ruhani.nlp

import com.ibm.icu.text.Transliterator
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Script transliteration backed by ICU4J's [Transliterator].
 *
 * The wire enum uses uppercase script names (LATIN/DEVANAGARI/GUJARATI);
 * ICU expects TitleCase ("Latin"/"Devanagari"/"Gujarati"), so we normalize
 * on the way in.
 *
 * For targets that land in Latin we chain `Latin-ASCII` to strip the IAST
 * diacritics ICU emits by default (`devanāgarī` → `devanagari`) — closer to
 * how Hindi/Gujarati speakers actually romanize words in practice.
 *
 * Transliterator instances are thread-safe but expensive to construct, so we
 * cache them by rule string.
 */
@Service
class TransliterationService {

    private val cache = ConcurrentHashMap<String, Transliterator>()

    fun translate(text: String, fromScript: String, toScript: String): String {
        if (text.isEmpty() || fromScript.equals(toScript, ignoreCase = true)) return text
        val from = canonical(fromScript)
        val to = canonical(toScript)
        val rule = buildString {
            append(from).append('-').append(to)
            if (to == "Latin") append("; Latin-ASCII")
        }
        return cache.getOrPut(rule) { Transliterator.getInstance(rule) }.transliterate(text)
    }

    private fun canonical(script: String): String =
        script.lowercase().replaceFirstChar { it.uppercase() }
}

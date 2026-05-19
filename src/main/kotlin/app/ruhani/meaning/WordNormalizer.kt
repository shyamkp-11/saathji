package app.ruhani.meaning

import java.text.Normalizer

/**
 * Canonical form used to key [app.ruhani.model.WordEntryEntity]. The
 * surface form (what the user typed) is preserved on whichever
 * [app.ruhani.model.TokenEntity] cited the word; only the lookup hash is
 * canonicalised so different spellings of the same word resolve to one
 * meaning list.
 *
 * Rules:
 *   1. NFC, then trim + lowercase, then strip trailing punctuation.
 *   2. Drop nukta (Hindi क़→क, Gujarati જ઼→જ etc.) — variants are
 *      surface-level only; meanings are the same.
 *   3. Anusvara before a stop consonant becomes the matching nasal +
 *      halant. Examples:
 *        आनंद  → आनन्द   (हिन्दी:  ं + द → न् + द)
 *        હિંમત → હિન્મત  (ગુજરાતી: ં + મ → ન્ + મ)
 *
 * The phonetic outcome is unchanged either way; the orthography is the
 * only thing that varies and we collapse it to the longer, fully-spelt
 * form because that's what dictionaries traditionally print.
 *
 * For Latin / English we only lowercase + NFC. Anusvara rules don't
 * apply and there's no nukta. (Future: handle diacritic stripping for
 * romanised Indic if needed.)
 */
object WordNormalizer {

    fun canonicalize(input: String, languageCode: String): String {
        val base = Normalizer.normalize(input.trim(), Normalizer.Form.NFC)
            .trimEnd('.', ',', '!', '?', ';', ':', '।', '।')
            .trim()
            .lowercase()
        return when {
            languageCode.startsWith("hi") -> canonicalizeIndic(base, DEVANAGARI)
            languageCode.startsWith("gu") -> canonicalizeIndic(base, GUJARATI)
            else -> base
        }
    }

    // ── per-script tables ────────────────────────────────────────────────────

    private data class IndicScript(
        val anusvara: Int,
        val nukta: Int,
        val halant: Char,
        /** consonant char → corresponding nasal char for "anusvara + cons" sequences. */
        val anusvaraNasal: Map<Char, Char>,
    )

    private val DEVANAGARI = IndicScript(
        anusvara = 0x0902,
        nukta = 0x093C,
        halant = '्',
        anusvaraNasal = mapOf(
            'क' to 'ङ', 'ख' to 'ङ', 'ग' to 'ङ', 'घ' to 'ङ',
            'च' to 'ञ', 'छ' to 'ञ', 'ज' to 'ञ', 'झ' to 'ञ',
            'ट' to 'ण', 'ठ' to 'ण', 'ड' to 'ण', 'ढ' to 'ण',
            'त' to 'न', 'थ' to 'न', 'द' to 'न', 'ध' to 'न',
            'प' to 'म', 'फ' to 'म', 'ब' to 'म', 'भ' to 'म',
        ),
    )

    private val GUJARATI = IndicScript(
        anusvara = 0x0A82,
        nukta = 0x0ABC,
        halant = '્',
        anusvaraNasal = mapOf(
            'ક' to 'ઙ', 'ખ' to 'ઙ', 'ગ' to 'ઙ', 'ઘ' to 'ઙ',
            'ચ' to 'ઞ', 'છ' to 'ઞ', 'જ' to 'ઞ', 'ઝ' to 'ઞ',
            'ટ' to 'ણ', 'ઠ' to 'ણ', 'ડ' to 'ણ', 'ઢ' to 'ણ',
            'ત' to 'ન', 'થ' to 'ન', 'દ' to 'ન', 'ધ' to 'ન',
            'પ' to 'મ', 'ફ' to 'મ', 'બ' to 'મ', 'ભ' to 'મ',
        ),
    )

    private fun canonicalizeIndic(s: String, script: IndicScript): String {
        // 1. Drop nukta combining mark — variant chars collapse to their base.
        val deNukted = s.filter { it.code != script.nukta }
        // 2. Replace anusvara-before-stop with the matching nasal + halant.
        return buildString(deNukted.length) {
            var i = 0
            while (i < deNukted.length) {
                val c = deNukted[i]
                if (c.code == script.anusvara && i + 1 < deNukted.length) {
                    val replacement = script.anusvaraNasal[deNukted[i + 1]]
                    if (replacement != null) {
                        append(replacement)
                        append(script.halant)
                        i++
                        continue
                    }
                }
                append(c)
                i++
            }
        }
    }
}

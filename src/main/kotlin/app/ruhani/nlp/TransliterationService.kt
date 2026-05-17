package app.ruhani.nlp

import com.ibm.icu.text.Transliterator
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Script transliteration backed by ICU4J's [Transliterator].
 *
 * The wire enum uses uppercase script names (LATIN/DEVANAGARI/GUJARATI);
 * ICU expects TitleCase ("Latin"/"Devanagari"/"Gujarati") so we normalize
 * on the way in.
 *
 * To a non-Latin target → single ICU pass.
 *
 * To Latin → four-phase, all aimed at producing the kind of English-keyboard
 * romanization a Hindi/Gujarati speaker would actually type (not the IAST
 * scholar spelling ICU defaults to):
 *
 *   1. `${from}-Latin` produces ISO 15919 / IAST with diacritics
 *        दिल   → "dila"
 *        कला   → "kalā"
 *        ख़िश्त → "ḵẖiśta"
 *        न     → "na"
 *
 *   2. Word-final schwa deletion drops the inherent 'a' that ICU otherwise
 *      carries through. Done BEFORE the phonetic / ASCII passes so we can
 *      still distinguish the schwa 'a' from the long 'ā' (कला stays "kalā"
 *      because the last vowel is 'ā', not 'a'). Single-syllable words like
 *      न are protected — dropping the schwa would leave a consonant-only
 *      token that's unpronounceable.
 *        "dila"   → "dil"
 *        "kalā"   → "kalā"   (no match, last char is 'ā')
 *        "ḵẖiśta" → "ḵẖiśt"
 *        "na"     → "na"     (too short, schwa kept)
 *
 *   3. IAST → English-keyboard phonetic substitution (ś→sh, ṅ→ng, ñ→ny,
 *      ṭ→t, ḍ→d, ṇ→n, ḥ→h, ṃ→m, ḷ→l, ṛ→ri). Without this `Latin-ASCII`
 *      would map ś → s and we'd write "khist" instead of "khisht".
 *        "ḵẖiśt" → "ḵẖisht"
 *
 *   4. `Latin-ASCII` strips remaining diacritics (macrons, dots-below on
 *      letters we didn't remap) so the final string fits on a normal keyboard.
 *        "ḵẖisht" → "khisht"
 *        "kalā"   → "kala"
 *
 * Internal schwa deletion (e.g. नमक → "namak" vs. नमकीन → "namkīn") is a
 * harder, language-specific problem that ICU doesn't solve. Word-final is
 * the high-value 80% of cases for our poetry use case; authors still edit
 * the staging row anyway.
 *
 * Transliterator instances are thread-safe but expensive to construct, so
 * we cache them by rule string.
 */
@Service
class TransliterationService {

    private val cache = ConcurrentHashMap<String, Transliterator>()
    private val latinToAscii: Transliterator by lazy { Transliterator.getInstance("Latin-ASCII") }

    private val vowels: Set<Char> = buildSet {
        addAll(listOf('a', 'e', 'i', 'o', 'u', 'ā', 'ī', 'ū', 'ē', 'ō', 'ṛ', 'ṝ'))
        // upper-case counterparts
        addAll(this.toList().map { it.uppercaseChar() })
    }

    /**
     * IAST diacritic consonants → English-keyboard phonetic equivalents.
     *
     * The three nasals (ṅ/ñ/ṇ) all collapse to "n": in Hindi/Gujarati
     * romanization a homorganic nasal before a stop is just the nasal
     * colour of the preceding vowel (गंगा is "ganga", not "gangga";
     * पंच is "panch", not "panych").
     */
    private val phonetic: Map<Char, String> = mapOf(
        'ś' to "sh", 'Ś' to "Sh",
        'ṣ' to "sh", 'Ṣ' to "Sh",
        'ṅ' to "n",  'Ṅ' to "N",
        'ñ' to "n",  'Ñ' to "N",
        // च (palatal voiceless) is IAST `c`; Hindi/Gujarati romanization
        // writes "ch". (छ stays "chh" naturally — IAST "ch" + this rule.)
        'c' to "ch", 'C' to "Ch",
        'ṭ' to "t",  'Ṭ' to "T",
        'ḍ' to "d",  'Ḍ' to "D",
        'ṇ' to "n",  'Ṇ' to "N",
        'ḷ' to "l",  'Ḷ' to "L",
        'ḥ' to "h",  'Ḥ' to "H",
        'ṃ' to "m",  'Ṃ' to "M",
        // velar fricatives (ख़/ग़) — ICU emits the base consonant with a
        // combining macron-below; map to the base letter so we don't lose
        // them to Latin-ASCII.
        'ḵ' to "k",  'Ḵ' to "K",
        'ḡ' to "g",  'Ḡ' to "G",
        'ẖ' to "h",
    )

    /**
     * Two-codepoint IAST sequences that need fixing before the char-by-char
     * phonetic map runs. Vocalic ऋ comes out of ICU as `r` + COMBINING RING
     * BELOW (U+0325) which the per-char map can't see.
     */
    private val multiCharIastFixes: List<Pair<String, String>> = listOf(
        "r̥" to "ri",   // ऋ (vocalic r)  → "ri"
        "R̥" to "Ri",
        "l̥" to "li",   // ऌ (vocalic l)  → "li"
        "L̥" to "Li",
    )

    // Letters AND combining marks. ICU's IAST output uses combining diacritics
    // (e.g. पंच → "pan̄ca" with a combining macron on n); treating those as
    // word separators would chop "pan̄ca" into "pan" + "ca" and skip schwa
    // deletion on what's actually one word.
    private val wordPattern = Regex("""[\p{L}\p{M}]+""")

    fun translate(text: String, fromScript: String, toScript: String): String {
        if (text.isEmpty() || fromScript.equals(toScript, ignoreCase = true)) return text
        val from = canonical(fromScript)
        val to = canonical(toScript)

        if (to == "Latin") {
            val iast = transliterator("$from-Latin").transliterate(text)
            // Phonetic substitution comes BEFORE schwa deletion so the latter
            // can see the new vowels we introduced (r̥ → "ri" turns कृष्ण
            // from a vowel-less cluster into one whose schwa we can safely
            // drop → "krishn").
            val phoneticised = applyPhonetic(iast)
            val schwaDropped =
                if (from == "Devanagari" || from == "Gujarati") dropSchwas(phoneticised) else phoneticised
            return latinToAscii.transliterate(schwaDropped)
        }

        return transliterator("$from-$to").transliterate(text)
    }

    private fun transliterator(rule: String): Transliterator =
        cache.getOrPut(rule) { Transliterator.getInstance(rule) }

    private fun canonical(script: String): String =
        script.lowercase().replaceFirstChar { it.uppercase() }

    /**
     * Apply Hindi/Gujarati schwa-deletion rules word by word: first the
     * word-final schwa, then the iterative internal-schwa rule (Ohala 1983,
     * generalised slightly per Tyson & Nagar).
     */
    private fun dropSchwas(text: String): String =
        wordPattern.replace(text) { m -> dropSchwasInWord(m.value) }

    private fun dropSchwasInWord(word: String): String {
        val afterFinal = dropWordFinalSchwa(word)
        return dropInternalSchwas(afterFinal)
    }

    /**
     * Word-final 'a' (schwa) deletion. Drops the trailing 'a' (or 'A') when:
     *   - the word is ≥3 chars long (single-syllable words keep their schwa
     *     — "na" must stay "na" or it becomes unpronounceable);
     *   - the preceding char isn't itself a vowel (we're not breaking a
     *     diphthong like "ai" / "au");
     *   - the rest of the word has at least one other vowel (so we don't
     *     turn an all-consonant cluster + schwa into just consonants).
     */
    private fun dropWordFinalSchwa(w: String): String =
        if (
            w.length >= 3 &&
            (w.last() == 'a' || w.last() == 'A') &&
            w[w.length - 2] !in vowels &&
            w.dropLast(1).any { it in vowels }
        ) {
            w.dropLast(1)
        } else {
            w
        }

    /**
     * Iterative right-to-left internal schwa deletion (Ohala 1983).
     * For each schwa 'a' that's not at position 0 and not word-final, drop
     * it when the pattern   `…VCəCV…`   holds:
     *   - immediately after the schwa: a consonant followed by a vowel
     *   - immediately before the schwa: a single consonant preceded by a vowel
     *
     * Right-to-left order matters: dropping the rightmost eligible schwa
     * first lets later (left-side) schwas "see" the surviving structure.
     * Each pass deletes at most one char, so we just decrement the cursor
     * and continue scanning leftward.
     *
     *   नमकीन  "namakīn"  →  schwa@3 (am __ kī) → "namkīn"
     *   सरकार  "sarakār"  →  schwa@3 (ar __ kā) → "sarkār"
     *   व्यवहार "vyavahār" →  schwa@4 (av __ hā) → "vyavhār"
     *   नमक    "namak"    →  no drop  (left schwa@1 has "n" before, no V)
     *   ज़िंदगी "zindagī"  →  no drop  (schwa@4 has "nd" before — VCC,
     *                         not VC — so the rule deliberately doesn't fire)
     *
     * Known limitation: VCCəCV cases like सन्तरा ("santarā" → ideally
     * "santra") aren't dropped because IAST collapses halant-suppressed
     * consonants and anusvara-derived nasals into the same surface form
     * (both produce a plain "n" before the next stop). Distinguishing
     * them would require parsing the original Devanagari source for the
     * halant U+094D marker. Left as a TODO; staging-screen edits cover
     * these outliers.
     */
    private fun dropInternalSchwas(word: String): String {
        if (word.length < 5) return word    // need at least V C ə C V
        var s = word
        var i = s.length - 3                // last position with room for "C V" after
        while (i >= 2) {                    // need at least "V C" before → index 2 is the earliest
            val c = s[i]
            if (c == 'a' || c == 'A') {
                val vBefore = s[i - 2]
                val cBefore = s[i - 1]
                val cAfter  = s[i + 1]
                val vAfter  = s[i + 2]
                if (
                    vBefore in vowels &&    // V at i-2
                    cBefore !in vowels &&   // single C at i-1
                    cAfter !in vowels &&    // C at i+1
                    vAfter in vowels        // V at i+2
                ) {
                    s = s.substring(0, i) + s.substring(i + 1)
                }
            }
            i--
        }
        return s
    }

    private fun applyPhonetic(text: String): String {
        // First fix the multi-codepoint sequences ICU emits (vocalic r̥, l̥);
        // after this everything we care about fits in a single Char.
        var s = text
        for ((from, to) in multiCharIastFixes) s = s.replace(from, to)
        val sb = StringBuilder(s.length + 4)
        for (c in s) {
            val sub = phonetic[c]
            if (sub != null) sb.append(sub) else sb.append(c)
        }
        return sb.toString()
    }
}

package db.migration

import app.ruhani.meaning.WordNormalizer
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Connection
import java.sql.SQLException

/**
 * Re-canonicalise every word_entries row under the new orthographic rules
 * (see [WordNormalizer]) and merge any rows that now collide on
 * (normalized_form, language_code).
 *
 * Two layers of merging:
 *
 * 1. **Kotlin-side grouping**: rows whose old `normalized_form` produces
 *    the same canonical string under [WordNormalizer.canonicalize] are
 *    grouped. The lowest-id row wins; the rest are deleted after
 *    re-pointing post_tokens and word_meanings at the survivor.
 *
 * 2. **MySQL-collation fallback**: utf8mb4_unicode_ci doesn't always agree
 *    with Kotlin's byte-level string equality (anusvara and some combining
 *    marks compare equal to their absence at primary collation weight),
 *    so an UPDATE that's byte-different can still hit the unique index.
 *    We catch the 1062 and merge into whatever existing row MySQL was
 *    pointing us at.
 *
 * Going forward, every read and write goes through
 * [app.ruhani.meaning.MeaningStore], which canonicalises before hitting
 * the table, so this pass is one-time.
 */
@Suppress("ClassName")
class V6__Canonicalize_word_entries : BaseJavaMigration() {

    private data class Row(val id: String, val oldForm: String, val lang: String)
    private data class Key(val canonical: String, val lang: String)

    override fun migrate(context: Context) {
        val conn = context.connection
        conn.autoCommit = false

        // ── 1. Snapshot every row ───────────────────────────────────────────
        val rows = mutableListOf<Row>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT id, normalized_form, language_code FROM word_entries").use { rs ->
                while (rs.next()) {
                    rows += Row(rs.getString(1), rs.getString(2), rs.getString(3))
                }
            }
        }

        // ── 2. Group by Kotlin-side canonical ───────────────────────────────
        val groups = HashMap<Key, MutableList<Row>>()
        for (row in rows) {
            val canonical = WordNormalizer.canonicalize(row.oldForm, row.lang)
            groups.getOrPut(Key(canonical, row.lang)) { mutableListOf() } += row
        }

        // ── 3. Process each group: intra-group dedup, then survivor update ──
        // Survivor selection is deterministic (lowest id) so reruns and
        // local-vs-prod produce the same shape.
        for ((key, members) in groups) {
            val sorted = members.sortedBy { it.id }
            var survivorId = sorted.first().id
            val survivorOldForm = sorted.first().oldForm
            val intraDuplicates = sorted.drop(1).map { it.id }

            if (intraDuplicates.isNotEmpty()) {
                mergeInto(conn, survivorId, intraDuplicates)
            }

            if (survivorOldForm != key.canonical) {
                survivorId = updateOrMerge(conn, survivorId, key)
            }
        }

        conn.commit()
    }

    /**
     * Try to rename `id`'s normalized_form to [key.canonical]. If that
     * collides with an existing (collation-equal) row, merge `id` into
     * that row instead. Returns the id that now represents the canonical
     * form — which is either the input `id` (if the rename succeeded) or
     * the existing-row id (if a merge happened).
     */
    private fun updateOrMerge(conn: Connection, id: String, key: Key): String {
        try {
            conn.prepareStatement(
                "UPDATE word_entries SET normalized_form = ? WHERE id = ?"
            ).use { ps ->
                ps.setString(1, key.canonical)
                ps.setString(2, id)
                ps.executeUpdate()
            }
            return id
        } catch (e: SQLException) {
            // 1062 = ER_DUP_ENTRY. Anything else is a genuine failure.
            if (e.errorCode != 1062) throw e

            // Find the row whose normalized_form collides under the
            // collation. WHERE id <> ? guards against matching ourselves;
            // there will be exactly one row (the unique index says so).
            val conflictId = conn.prepareStatement(
                "SELECT id FROM word_entries " +
                    "WHERE normalized_form = ? AND language_code = ? AND id <> ?"
            ).use { ps ->
                ps.setString(1, key.canonical)
                ps.setString(2, key.lang)
                ps.setString(3, id)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
                ?: throw e   // shouldn't happen — re-throw if we can't find the culprit

            mergeInto(conn, conflictId, listOf(id))
            return conflictId
        }
    }

    /**
     * Re-point every dependent FK (post_tokens, word_meanings) from each
     * id in [duplicates] to [survivorId], then delete the duplicate rows.
     */
    private fun mergeInto(conn: Connection, survivorId: String, duplicates: List<String>) {
        if (duplicates.isEmpty()) return
        val placeholders = duplicates.joinToString(",") { "?" }
        conn.prepareStatement(
            "UPDATE post_tokens SET word_entry_id = ? WHERE word_entry_id IN ($placeholders)"
        ).use { ps ->
            ps.setString(1, survivorId)
            duplicates.forEachIndexed { i, d -> ps.setString(i + 2, d) }
            ps.executeUpdate()
        }
        conn.prepareStatement(
            "UPDATE word_meanings SET word_entry_id = ? WHERE word_entry_id IN ($placeholders)"
        ).use { ps ->
            ps.setString(1, survivorId)
            duplicates.forEachIndexed { i, d -> ps.setString(i + 2, d) }
            ps.executeUpdate()
        }
        conn.prepareStatement(
            "DELETE FROM word_entries WHERE id IN ($placeholders)"
        ).use { ps ->
            duplicates.forEachIndexed { i, d -> ps.setString(i + 1, d) }
            ps.executeUpdate()
        }
    }
}

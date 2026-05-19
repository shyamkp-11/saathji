package db.migration

import app.ruhani.meaning.WordNormalizer
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

/**
 * Re-canonicalise every word_entries row under the new orthographic rules
 * (see [WordNormalizer]) and merge any rows that now collide on
 * (normalized_form, language_code).
 *
 * Merging:
 *   For each (canonical, language_code) group, the row with the
 *   lexicographically smallest id wins. Every dependent row — post_tokens
 *   and word_meanings — gets re-pointed at the survivor, then duplicate
 *   word_entries rows are deleted. The survivor's normalized_form is
 *   updated to the canonical form when it differs.
 *
 * This runs once at deploy time. Subsequent writes go through
 * [app.ruhani.meaning.MeaningStore.getOrCreateEntry] which already
 * canonicalises, so the table stays in shape.
 */
@Suppress("ClassName")
class V6__Canonicalize_word_entries : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val conn = context.connection
        conn.autoCommit = false

        // ── 1. Snapshot every existing row ──────────────────────────────────
        data class Row(val id: String, val oldForm: String, val lang: String)
        val rows = mutableListOf<Row>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT id, normalized_form, language_code FROM word_entries").use { rs ->
                while (rs.next()) {
                    rows += Row(rs.getString(1), rs.getString(2), rs.getString(3))
                }
            }
        }

        // ── 2. Group by canonical key ───────────────────────────────────────
        data class Key(val canonical: String, val lang: String)
        val groups = HashMap<Key, MutableList<Row>>()
        for (row in rows) {
            val canonical = WordNormalizer.canonicalize(row.oldForm, row.lang)
            groups.getOrPut(Key(canonical, row.lang)) { mutableListOf() } += row
        }

        // ── 3. For each group: pick survivor, re-point dependants, delete dups,
        //       update survivor's normalized_form ─────────────────────────────
        for ((key, members) in groups) {
            val sorted = members.sortedBy { it.id }
            val survivor = sorted.first()
            val duplicates = sorted.drop(1)

            if (duplicates.isNotEmpty()) {
                val placeholders = duplicates.joinToString(",") { "?" }
                conn.prepareStatement(
                    "UPDATE post_tokens SET word_entry_id = ? WHERE word_entry_id IN ($placeholders)"
                ).use { ps ->
                    ps.setString(1, survivor.id)
                    duplicates.forEachIndexed { i, d -> ps.setString(i + 2, d.id) }
                    ps.executeUpdate()
                }
                conn.prepareStatement(
                    "UPDATE word_meanings SET word_entry_id = ? WHERE word_entry_id IN ($placeholders)"
                ).use { ps ->
                    ps.setString(1, survivor.id)
                    duplicates.forEachIndexed { i, d -> ps.setString(i + 2, d.id) }
                    ps.executeUpdate()
                }
                conn.prepareStatement(
                    "DELETE FROM word_entries WHERE id IN ($placeholders)"
                ).use { ps ->
                    duplicates.forEachIndexed { i, d -> ps.setString(i + 1, d.id) }
                    ps.executeUpdate()
                }
            }

            if (survivor.oldForm != key.canonical) {
                conn.prepareStatement(
                    "UPDATE word_entries SET normalized_form = ? WHERE id = ?"
                ).use { ps ->
                    ps.setString(1, key.canonical)
                    ps.setString(2, survivor.id)
                    ps.executeUpdate()
                }
            }
        }

        conn.commit()
    }
}

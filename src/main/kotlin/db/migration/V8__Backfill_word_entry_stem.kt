package db.migration

import app.ruhani.meaning.WordNormalizer
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

/**
 * Populates word_entries.stem for rows that pre-existed V7. New rows go
 * through MeaningStore.getOrCreateEntry, which now writes stem at insert
 * time, so this pass is one-time.
 */
@Suppress("ClassName")
class V8__Backfill_word_entry_stem : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val conn = context.connection
        conn.autoCommit = false

        data class Row(val id: String, val normalizedForm: String, val lang: String)
        val rows = mutableListOf<Row>()
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT id, normalized_form, language_code FROM word_entries WHERE stem = ''"
            ).use { rs ->
                while (rs.next()) {
                    rows += Row(rs.getString(1), rs.getString(2), rs.getString(3))
                }
            }
        }

        conn.prepareStatement("UPDATE word_entries SET stem = ? WHERE id = ?").use { ps ->
            for (row in rows) {
                val stem = WordNormalizer.stem(row.normalizedForm, row.lang)
                ps.setString(1, stem)
                ps.setString(2, row.id)
                ps.addBatch()
            }
            ps.executeBatch()
        }

        conn.commit()
    }
}

-- Stem column powers the "related words" suggestions on the dictionary
-- and meaning-sheet screens. See WordNormalizer.stem() for the rule set.
-- Populated by V8 (Kotlin-based) for existing rows.
ALTER TABLE word_entries
  ADD COLUMN stem VARCHAR(255) NOT NULL DEFAULT '';

CREATE INDEX idx_word_entries_stem ON word_entries (language_code, stem);

package app.ruhani.meaning

import app.ruhani.model.MeaningUpvoteEntity
import app.ruhani.model.MeaningUpvoteId
import app.ruhani.model.WordEntryEntity
import app.ruhani.model.WordMeaningEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface WordEntryRepository : JpaRepository<WordEntryEntity, String> {
    fun findByNormalizedFormAndLanguageCode(normalizedForm: String, languageCode: String): WordEntryEntity?
}

@Repository
interface WordMeaningRepository : JpaRepository<WordMeaningEntity, String> {
    fun findByWordEntryIdOrderByUpvoteCountDesc(wordEntryId: String): List<WordMeaningEntity>

    fun findByAuthorIdOrderByCreatedAtDesc(authorId: String): List<WordMeaningEntity>

    /**
     * Count meanings on [postId] authored by anyone OTHER than [excludeAuthorId].
     * Used to decide whether editing a post should fork a new version (when
     * community contributions exist) or edit it in place (when only the
     * author's own seed meanings exist, or none).
     */
    @Query(
        """
        SELECT COUNT(m) FROM WordMeaningEntity m
        WHERE m.authorId <> :excludeAuthorId
          AND m.wordEntryId IN (
            SELECT t.wordEntryId FROM TokenEntity t
            WHERE t.lineId IN (
              SELECT l.id FROM LineEntity l WHERE l.postId = :postId
            )
          )
        """
    )
    fun countOthersOnPost(
        @Param("postId") postId: String,
        @Param("excludeAuthorId") excludeAuthorId: String,
    ): Long

    @Modifying
    @Query("UPDATE WordMeaningEntity m SET m.upvoteCount = m.upvoteCount + :delta WHERE m.id = :id")
    fun adjustUpvoteCount(@Param("id") id: String, @Param("delta") delta: Int): Int
}

@Repository
interface MeaningUpvoteRepository : JpaRepository<MeaningUpvoteEntity, MeaningUpvoteId> {
    fun existsByMeaningIdAndUserId(meaningId: String, userId: String): Boolean

    @Modifying
    @Query("DELETE FROM MeaningUpvoteEntity u WHERE u.meaningId = :meaningId AND u.userId = :userId")
    fun deleteOne(@Param("meaningId") meaningId: String, @Param("userId") userId: String): Int

    @Modifying
    @Query("DELETE FROM MeaningUpvoteEntity u WHERE u.meaningId = :meaningId")
    fun deleteByMeaningId(@Param("meaningId") meaningId: String): Int
}

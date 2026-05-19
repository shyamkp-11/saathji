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

    /**
     * Other WordEntries sharing this stem in the same language. Ordered by
     * meaning count (entries with curated meanings surface first) then by
     * normalized form alphabetical so ties are stable.
     */
    @Query(
        """
        SELECT w FROM WordEntryEntity w
        WHERE w.stem = :stem
          AND w.languageCode = :lang
          AND w.id <> :excludeId
        ORDER BY (SELECT COUNT(m) FROM WordMeaningEntity m WHERE m.wordEntryId = w.id) DESC,
                 w.normalizedForm ASC
        """
    )
    fun findRelatedByStem(
        @Param("stem") stem: String,
        @Param("lang") lang: String,
        @Param("excludeId") excludeId: String,
        pageable: org.springframework.data.domain.Pageable,
    ): List<WordEntryEntity>
}

@Repository
interface WordMeaningRepository : JpaRepository<WordMeaningEntity, String> {
    fun findByWordEntryIdOrderByUpvoteCountDesc(wordEntryId: String): List<WordMeaningEntity>

    fun findByAuthorIdOrderByCreatedAtDesc(authorId: String): List<WordMeaningEntity>

    /**
     * Of the supplied word-entry ids, returns the subset that has at least
     * one meaning. Used by [PostController.getPost] to mark tappable words
     * with a dot indicator in PostDetail.
     */
    @Query(
        "SELECT DISTINCT m.wordEntryId FROM WordMeaningEntity m WHERE m.wordEntryId IN :ids"
    )
    fun findWordEntryIdsWithMeanings(@Param("ids") ids: Collection<String>): List<String>

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

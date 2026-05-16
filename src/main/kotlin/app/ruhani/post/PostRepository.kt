package app.ruhani.post

import app.ruhani.model.PostEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PostRepository : JpaRepository<PostEntity, String> {

    /**
     * Cursor-paginated published feed. `cursor` is the createdAt of the last
     * post in the previous page (null = first page). Filters are optional.
     */
    @Query(
        """
        SELECT p FROM PostEntity p
        WHERE p.status = 'PUBLISHED'
          AND p.deletedAt IS NULL
          AND (:cursor   IS NULL OR p.createdAt   <  :cursor)
          AND (:form     IS NULL OR p.form         = :form)
          AND (:langCode IS NULL OR p.languageCode = :langCode)
        ORDER BY p.createdAt DESC
        """
    )
    fun feedPage(
        @Param("cursor") cursor: Instant?,
        @Param("form") form: String?,
        @Param("langCode") langCode: String?,
        pageable: Pageable,
    ): List<PostEntity>

    @Query(
        """
        SELECT p FROM PostEntity p
        WHERE p.deletedAt IS NULL
          AND p.authorId = :authorId
          AND (:status IS NULL OR p.status = :status)
        ORDER BY p.createdAt DESC
        """
    )
    fun byAuthor(
        @Param("authorId") authorId: String,
        @Param("status") status: String?,
    ): List<PostEntity>

    /**
     * Simple LIKE-based full-text search across line text, summary, and tags.
     * Good enough for v1; swap to MySQL full-text index or external engine later.
     */
    @Query(
        """
        SELECT DISTINCT p FROM PostEntity p
          LEFT JOIN p.lines l
          LEFT JOIN p.tags  t
        WHERE p.status = 'PUBLISHED'
          AND p.deletedAt IS NULL
          AND (:cursor   IS NULL OR p.createdAt   <  :cursor)
          AND (:form     IS NULL OR p.form         = :form)
          AND (:langCode IS NULL OR p.languageCode = :langCode)
          AND (:tag      IS NULL OR LOWER(t)       = LOWER(:tag))
          AND (LOWER(l.text)    LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(p.summary) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(t)         LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY p.createdAt DESC
        """
    )
    fun search(
        @Param("q") q: String,
        @Param("form") form: String?,
        @Param("langCode") langCode: String?,
        @Param("tag") tag: String?,
        @Param("cursor") cursor: Instant?,
        pageable: Pageable,
    ): List<PostEntity>
}

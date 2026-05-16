package app.ruhani.bookmark

import app.ruhani.model.BookmarkEntity
import app.ruhani.model.BookmarkId
import app.ruhani.model.PostEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface BookmarkRepository : JpaRepository<BookmarkEntity, BookmarkId> {

    fun existsByUserIdAndPostId(userId: String, postId: String): Boolean

    @Modifying
    @Query("DELETE FROM BookmarkEntity b WHERE b.userId = :userId AND b.postId = :postId")
    fun deleteOne(@Param("userId") userId: String, @Param("postId") postId: String): Int

    /**
     * Joined list of bookmarked posts, paged by createdAt cursor on the
     * bookmark row (not the post). Excludes deleted/unpublished posts.
     */
    @Query(
        """
        SELECT p FROM BookmarkEntity b
          JOIN PostEntity p ON p.id = b.postId
        WHERE b.userId = :userId
          AND p.deletedAt IS NULL
          AND p.status = 'PUBLISHED'
          AND (:cursor IS NULL OR b.createdAt < :cursor)
        ORDER BY b.createdAt DESC
        """
    )
    fun bookmarkedPosts(
        @Param("userId") userId: String,
        @Param("cursor") cursor: Instant?,
        pageable: Pageable,
    ): List<PostEntity>

    /** Used to build the response cursor — last bookmark createdAt on the page. */
    @Query("SELECT b.createdAt FROM BookmarkEntity b WHERE b.userId = :userId AND b.postId = :postId")
    fun bookmarkCreatedAt(@Param("userId") userId: String, @Param("postId") postId: String): Instant?
}

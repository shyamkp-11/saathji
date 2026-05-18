package app.ruhani.list

import app.ruhani.model.ListItemEntity
import app.ruhani.model.ListItemId
import app.ruhani.model.PostListEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PostListRepository : JpaRepository<PostListEntity, String> {

    fun findBySlug(slug: String): PostListEntity?
    fun existsBySlug(slug: String): Boolean

    /**
     * Public browse: PUBLISHED only, paged by publishedAt cursor (descending).
     */
    @Query(
        """
        SELECT l FROM PostListEntity l
        WHERE l.status = 'PUBLISHED'
          AND (:cursor IS NULL OR l.publishedAt < :cursor)
        ORDER BY l.publishedAt DESC
        """
    )
    fun publishedPage(@Param("cursor") cursor: Instant?, pageable: Pageable): List<PostListEntity>

    /** Editor's own view: includes both DRAFT and PUBLISHED, newest first. */
    @Query("SELECT l FROM PostListEntity l WHERE l.editorId = :editorId ORDER BY l.createdAt DESC")
    fun byEditor(@Param("editorId") editorId: String): List<PostListEntity>
}

@Repository
interface ListItemRepository : JpaRepository<ListItemEntity, ListItemId> {

    /** Items in display order. */
    fun findByListIdOrderByOrdinalAsc(listId: String): List<ListItemEntity>

    fun existsByListIdAndPostId(listId: String, postId: String): Boolean

    fun countByListId(listId: String): Long

    /** Used when reordering — drop all rows for the list before re-inserting. */
    @Modifying
    @Query("DELETE FROM ListItemEntity i WHERE i.listId = :listId")
    fun deleteAllByListId(@Param("listId") listId: String): Int

    @Modifying
    @Query("DELETE FROM ListItemEntity i WHERE i.listId = :listId AND i.postId = :postId")
    fun deleteOne(@Param("listId") listId: String, @Param("postId") postId: String): Int

    @Query("SELECT COALESCE(MAX(i.ordinal), -1) FROM ListItemEntity i WHERE i.listId = :listId")
    fun maxOrdinal(@Param("listId") listId: String): Int
}

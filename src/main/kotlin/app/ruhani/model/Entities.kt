package app.ruhani.model

import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/**
 * JPA-backed domain entities. All ids are application-generated UUID strings
 * (kept as VARCHAR(36) in MySQL) so we don't need DB-side sequences and so
 * client-supplied ids would be trivial to support if we ever need them.
 *
 * Equality is intentionally id-based (not data-class generated) — JPA proxies
 * and lazy loading make property-based equals/hashCode unsafe.
 */

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(nullable = false, length = 255)
    var email: String = "",

    @Column(length = 64)
    var handle: String? = null,

    @Column(length = 500)
    var bio: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    override fun equals(other: Any?) = other is UserEntity && id == other.id
    override fun hashCode() = id.hashCode()
}

@Entity
@Table(name = "posts")
class PostEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "author_id", nullable = false, length = 36)
    var authorId: String = "",

    @Column(name = "poet_id", length = 36)
    var poetId: String? = null,

    /** Previous version in this post's edit chain. Null for the original draft. */
    @Column(name = "parent_post_id", length = 36)
    var parentPostId: String? = null,

    /** 1 for the original, incremented on each edit. */
    @Column(nullable = false)
    var version: Int = 1,

    @Column(name = "language_code", nullable = false, length = 16)
    var languageCode: String = "",

    @Column(nullable = false, length = 32)
    var form: String = "",

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "post_tags", joinColumns = [JoinColumn(name = "post_id")])
    @Column(name = "tag", length = 64)
    var tags: MutableList<String> = mutableListOf(),

    @Column(nullable = false, length = 16)
    var status: String = "DRAFT",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(length = 1000)
    var summary: String? = null,

    @Column(name = "edits_locked", nullable = false)
    var editsLocked: Boolean = false,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    // FK is owned by the child's explicit postId column (insertable/updatable on
    // that side). Marking @JoinColumn read-only here avoids Hibernate's
    // INSERT-then-UPDATE dance, which would fail against a NOT NULL FK.
    @OneToMany(
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER,
    )
    @JoinColumn(name = "post_id", insertable = false, updatable = false)
    @OrderBy("ordinal ASC")
    var lines: MutableList<LineEntity> = mutableListOf(),
) {
    override fun equals(other: Any?) = other is PostEntity && id == other.id
    override fun hashCode() = id.hashCode()
}

@Entity
@Table(name = "post_lines")
class LineEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "post_id", nullable = false, length = 36)
    var postId: String = "",

    @Column(nullable = false)
    var ordinal: Int = 0,

    @Column(nullable = false, columnDefinition = "TEXT")
    var text: String = "",

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "line_transliterations", joinColumns = [JoinColumn(name = "line_id")])
    @MapKeyColumn(name = "script", length = 16)
    @Column(name = "text", columnDefinition = "TEXT")
    var transliterations: MutableMap<String, String> = mutableMapOf(),

    @Column(length = 500)
    var summary: String? = null,

    @OneToMany(
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER,
    )
    @JoinColumn(name = "line_id", insertable = false, updatable = false)
    @OrderBy("ordinal ASC")
    var tokens: MutableList<TokenEntity> = mutableListOf(),
) {
    override fun equals(other: Any?) = other is LineEntity && id == other.id
    override fun hashCode() = id.hashCode()
}

@Entity
@Table(name = "post_tokens")
class TokenEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "line_id", nullable = false, length = 36)
    var lineId: String = "",

    @Column(nullable = false)
    var ordinal: Int = 0,

    @Column(nullable = false, length = 255)
    var text: String = "",

    @Column(name = "word_entry_id", nullable = false, length = 36)
    var wordEntryId: String = "",
) {
    override fun equals(other: Any?) = other is TokenEntity && id == other.id
    override fun hashCode() = id.hashCode()
}

@Entity
@Table(name = "word_entries")
class WordEntryEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "normalized_form", nullable = false, length = 255)
    var normalizedForm: String = "",

    @Column(name = "language_code", nullable = false, length = 16)
    var languageCode: String = "",
) {
    override fun equals(other: Any?) = other is WordEntryEntity && id == other.id
    override fun hashCode() = id.hashCode()
}

@Entity
@Table(name = "word_meanings")
class WordMeaningEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "word_entry_id", nullable = false, length = 36)
    var wordEntryId: String = "",

    @Column(nullable = false, length = 1000)
    var text: String = "",

    @Column(name = "author_id", nullable = false, length = 36)
    var authorId: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "upvote_count", nullable = false)
    var upvoteCount: Int = 0,
) {
    override fun equals(other: Any?) = other is WordMeaningEntity && id == other.id
    override fun hashCode() = id.hashCode()
}

@Entity
@Table(name = "meaning_upvotes")
@IdClass(MeaningUpvoteId::class)
class MeaningUpvoteEntity(
    @Id
    @Column(name = "meaning_id", length = 36)
    var meaningId: String = "",

    @Id
    @Column(name = "user_id", length = 36)
    var userId: String = "",
)

data class MeaningUpvoteId(
    var meaningId: String = "",
    var userId: String = "",
) : Serializable

@Entity
@Table(name = "bookmarks")
@IdClass(BookmarkId::class)
class BookmarkEntity(
    @Id
    @Column(name = "user_id", length = 36)
    var userId: String = "",

    @Id
    @Column(name = "post_id", length = 36)
    var postId: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)

data class BookmarkId(
    var userId: String = "",
    var postId: String = "",
) : Serializable

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenEntity(
    @Id
    @Column(length = 64)
    var token: String = "",

    @Column(name = "user_id", nullable = false, length = 36)
    var userId: String = "",

    @Column(nullable = false, length = 16)
    var status: String = "VALID",   // VALID | ROTATED | REVOKED

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    override fun equals(other: Any?) = other is RefreshTokenEntity && token == other.token
    override fun hashCode() = token.hashCode()
}

package app.ruhani.model

// ── Auth ─────────────────────────────────────────────────────────────────────

data class RequestOtpRequest(val email: String)

data class VerifyOtpRequest(val email: String, val code: String)

data class VerifyOtpResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto?,
    val isNewUser: Boolean,
)

data class CompleteSignupRequest(val handle: String, val bio: String?)

data class CompleteSignupResponse(val user: UserDto)

data class RefreshRequest(val refreshToken: String)

data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

data class SignOutRequest(val refreshToken: String)

data class UserDto(
    val id: String,
    val email: String,
    val handle: String,
    val bio: String?,
    val createdAt: String,
)

// ── Posts ─────────────────────────────────────────────────────────────────────

data class CreateDraftRequest(
    val languageCode: String,
    val form: String,
    val rawText: String,
    val authorHandle: String,
)

data class StagedLineDto(
    val text: String,
    val transliterations: Map<String, String> = emptyMap(),
    val summary: String? = null,
    val tokenTexts: List<String> = emptyList(),
)

data class UpdateStagingRequest(
    val lines: List<StagedLineDto>,
    val summary: String? = null,
    val tags: List<String> = emptyList(),
)

data class PostDto(
    val id: String,
    val authorId: String,
    val poetId: String? = null,
    val parentPostId: String? = null,
    val version: Int = 1,
    val languageCode: String,
    val form: String,
    val tags: List<String> = emptyList(),
    /** DRAFT | PUBLISHED | SUPERSEDED — SUPERSEDED means a newer version replaced it. */
    val status: String,
    val createdAt: String,
    val publishedAt: String? = null,
    val summary: String? = null,
    val editsLocked: Boolean = false,
    val lines: List<LineDto> = emptyList(),
)

data class LineDto(
    val id: String,
    val postId: String,
    val ordinal: Int,
    val text: String,
    val transliterations: Map<String, String> = emptyMap(),
    val summary: String? = null,
    val tokens: List<TokenDto> = emptyList(),
)

data class TokenDto(
    val id: String,
    val lineId: String,
    val ordinal: Int,
    val text: String,
    val wordEntryId: String,
)

data class FeedItemDto(
    val post: PostDto,
    val authorHandle: String,
)

data class FeedPageDto(
    val items: List<FeedItemDto>,
    val nextCursor: String? = null,
)

// ── Meanings ──────────────────────────────────────────────────────────────────

data class AddMeaningRequest(
    val word: String,
    val languageCode: String,
    val text: String,
)

data class WordMeaningDto(
    val id: String,
    val wordEntryId: String,
    val text: String,
    val authorId: String,
    val createdAt: String,
    val upvoteCount: Int,
    val viewerUpvoted: Boolean = false,
)

data class MeaningContributionDto(
    val word: String,
    val meaning: WordMeaningDto,
)

data class MeaningContributionsPageDto(
    val items: List<MeaningContributionDto>,
    val nextCursor: String? = null,
)

data class ContextualNoteDto(
    val id: String,
    val scope: String,
    val targetId: String,
    val postId: String,
    val text: String,
    val authorId: String,
    val createdAt: String,
    val upvoteCount: Int,
    val viewerUpvoted: Boolean = false,
)

data class MeaningsBundleDto(
    val word: String,
    val languageCode: String,
    val wordMeanings: List<WordMeaningDto>,
    val notes: List<ContextualNoteDto> = emptyList(),
)

// ── NLP ───────────────────────────────────────────────────────────────────────

data class TransliterateRequest(
    val text: String,
    val fromScript: String,
    val toScripts: List<String>,
)

data class TransliterateResponse(
    val results: Map<String, String>,
)

data class TransliterateBatchRequest(
    val lines: List<String>,
    val fromScript: String,
    val toScripts: List<String>,
)

data class TransliterateBatchResponse(
    val results: List<TransliterateResponse>,
)

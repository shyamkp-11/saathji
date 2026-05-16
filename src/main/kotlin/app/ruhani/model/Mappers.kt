package app.ruhani.model

fun UserEntity.toDto() = UserDto(
    id = id,
    email = email,
    handle = handle ?: "",
    bio = bio,
    createdAt = createdAt.toString(),
)

fun PostEntity.toDto() = PostDto(
    id = id,
    authorId = authorId,
    poetId = poetId,
    languageCode = languageCode,
    form = form,
    tags = tags.toList(),
    status = status,
    createdAt = createdAt.toString(),
    publishedAt = publishedAt?.toString(),
    summary = summary,
    editsLocked = editsLocked,
    lines = lines.map { it.toDto() },
)

fun LineEntity.toDto() = LineDto(
    id = id,
    postId = postId,
    ordinal = ordinal,
    text = text,
    transliterations = transliterations.toMap(),
    summary = summary,
    tokens = tokens.map { it.toDto() },
)

fun TokenEntity.toDto() = TokenDto(
    id = id,
    lineId = lineId,
    ordinal = ordinal,
    text = text,
    wordEntryId = wordEntryId,
)

fun WordMeaningEntity.toDto(viewerUserId: String?) = WordMeaningDto(
    id = id,
    wordEntryId = wordEntryId,
    text = text,
    authorId = authorId,
    createdAt = createdAt.toString(),
    upvoteCount = upvoteCount,
    viewerUpvoted = viewerUserId != null && upvoterIds.contains(viewerUserId),
)

package app.ruhani.model

/**
 * Roles drive what a user can do beyond posting:
 *   - USER:   default. Post, comment, bookmark.
 *   - EDITOR: above + curate editorial lists.
 *   - ADMIN:  reserved. v1 treats ADMIN identically to EDITOR.
 *
 * Stored as the enum name in `users.role` (VARCHAR(16)). Unknown values
 * default to USER on read so a forward-rolled DB never crashes the app.
 */
enum class UserRole { USER, EDITOR, ADMIN }

val UserEntity.userRole: UserRole
    get() = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.USER)

/** True for any role that can curate editorial lists. */
fun UserEntity.canCurate(): Boolean = userRole != UserRole.USER

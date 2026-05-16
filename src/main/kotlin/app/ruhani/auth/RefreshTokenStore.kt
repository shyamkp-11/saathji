package app.ruhani.auth

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks opaque refresh tokens with rotation and reuse detection.
 *
 * - `valid`: tokens that can be used right now (token → userId)
 * - `rotated`: tokens that were once valid but have been exchanged (token → userId)
 *   Presenting a rotated token means the previous holder's copy was stolen;
 *   we revoke all sessions for that user.
 */
@Component
class RefreshTokenStore {
    private val valid = ConcurrentHashMap<String, String>()    // token → userId
    private val rotated = ConcurrentHashMap<String, String>()  // token → userId

    fun store(token: String, userId: String) {
        valid[token] = userId
    }

    /** Consumes `token` and returns the userId, or null if invalid/revoked. */
    fun useToken(token: String): String? {
        val userId = valid.remove(token)
        if (userId != null) {
            rotated[token] = userId
            return userId
        }
        val revokedOwner = rotated[token]
        if (revokedOwner != null) {
            revokeAllForUser(revokedOwner)
        }
        return null
    }

    fun revoke(token: String) {
        val userId = valid.remove(token) ?: return
        rotated[token] = userId
    }

    private fun revokeAllForUser(userId: String) {
        valid.entries.removeIf { it.value == userId }
    }
}

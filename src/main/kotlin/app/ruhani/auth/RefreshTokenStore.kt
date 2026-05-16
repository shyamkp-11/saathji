package app.ruhani.auth

import app.ruhani.model.RefreshTokenEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Refresh-token persistence with rotation and reuse detection.
 *
 * Tokens transition VALID → ROTATED on a successful refresh and stay around
 * so we can detect replay attacks. If a token presented to [useToken] has
 * status=ROTATED the previous owner's copy was leaked — we revoke every
 * other VALID token for that user.
 */
@Component
@Transactional
class RefreshTokenStore(private val repo: RefreshTokenRepository) {

    fun store(token: String, userId: String) {
        repo.save(RefreshTokenEntity(token = token, userId = userId, status = "VALID"))
    }

    /** Consumes `token` and returns the userId, or null if invalid/revoked. */
    fun useToken(token: String): String? {
        val entity = repo.findById(token).orElse(null) ?: return null
        return when (entity.status) {
            "VALID" -> {
                entity.status = "ROTATED"
                repo.save(entity)
                entity.userId
            }
            "ROTATED" -> {
                // Replay attack — wipe out every active session for this user.
                repo.revokeAllValidForUser(entity.userId)
                null
            }
            else -> null  // REVOKED
        }
    }

    fun revoke(token: String) {
        val entity = repo.findById(token).orElse(null) ?: return
        if (entity.status == "VALID") {
            entity.status = "REVOKED"
            repo.save(entity)
        }
    }
}

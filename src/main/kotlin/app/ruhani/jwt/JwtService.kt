package app.ruhani.jwt

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${ruhani.jwt.secret}") private val secret: String,
    @Value("\${ruhani.jwt.access-token-expiry-seconds}") val accessTokenExpirySeconds: Long,
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
    }

    fun generateAccessToken(userId: String): String {
        val now = Date()
        val expiry = Date(now.time + accessTokenExpirySeconds * 1000L)
        return Jwts.builder()
            .subject(userId)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun extractUserId(token: String): String? = try {
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
            .subject
    } catch (_: JwtException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

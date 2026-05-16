package app.ruhani.auth

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple fixed-window in-memory rate limiter. Sufficient for a single-instance
 * backend; swap for Bucket4j-on-Redis when we go horizontal.
 *
 * Each key gets its own window. When the window expires, the count resets.
 * `tryAcquire` returns null when allowed; otherwise the seconds-until-retry.
 */
class RateLimiter(
    private val maxRequests: Int,
    private val windowSeconds: Long,
) {
    private class Window(var count: Int, val expiresAt: Instant)

    private val windows = ConcurrentHashMap<String, Window>()

    fun tryAcquire(key: String): Long? {
        val now = Instant.now()
        val window = windows.compute(key) { _, existing ->
            if (existing == null || now.isAfter(existing.expiresAt)) {
                Window(count = 0, expiresAt = now.plusSeconds(windowSeconds))
            } else {
                existing
            }
        }!!
        synchronized(window) {
            if (window.count >= maxRequests) {
                return Duration.between(now, window.expiresAt).seconds.coerceAtLeast(1)
            }
            window.count++
            return null
        }
    }

    fun checkOrThrow(key: String) {
        tryAcquire(key)?.let { throw RateLimitException(it) }
    }
}

class RateLimitException(val retryAfterSeconds: Long) :
    RuntimeException("Rate limit exceeded; retry in ${retryAfterSeconds}s")

@ControllerAdvice
class RateLimitAdvice {
    @ExceptionHandler(RateLimitException::class)
    fun handle(ex: RateLimitException): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", ex.retryAfterSeconds.toString())
            .body(
                mapOf(
                    "error" to "rate_limit_exceeded",
                    "retryAfterSeconds" to ex.retryAfterSeconds,
                )
            )
}

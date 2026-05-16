package app.ruhani.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class OtpStore(
    @Value("\${ruhani.otp.expiry-seconds}") private val expirySeconds: Long,
    @Value("\${ruhani.otp.universal-bypass}") private val universalBypass: String,
) {
    private data class Entry(val code: String, val expiresAt: Instant)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun generate(email: String): String {
        val code = (100000..999999).random().toString()
        entries[email.lowercase()] = Entry(
            code = code,
            expiresAt = Instant.now().plusSeconds(expirySeconds),
        )
        return code
    }

    fun verify(email: String, code: String): Boolean {
        if (code == universalBypass) return true
        val key = email.lowercase()
        val entry = entries[key] ?: return false
        if (Instant.now().isAfter(entry.expiresAt)) {
            entries.remove(key)
            return false
        }
        if (entry.code != code) return false
        entries.remove(key)
        return true
    }
}

package app.ruhani.auth

import app.ruhani.jwt.JwtService
import app.ruhani.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class AuthService(
    private val userStore: UserStore,
    private val otpStore: OtpStore,
    private val refreshTokenStore: RefreshTokenStore,
    private val jwtService: JwtService,
    private val emailSender: EmailSender,
    @Qualifier("otpRequestPerEmail") private val otpRequestPerEmail: RateLimiter,
    @Qualifier("otpRequestPerIp")    private val otpRequestPerIp: RateLimiter,
    @Qualifier("otpVerifyPerEmail")  private val otpVerifyPerEmail: RateLimiter,
) {
    fun requestOtp(email: String, clientIp: String) {
        // Two independent limits: one user abusing many IPs and many users sharing one IP
        // are both bounded.
        otpRequestPerEmail.checkOrThrow("email:${email.lowercase()}")
        otpRequestPerIp.checkOrThrow("ip:$clientIp")
        val code = otpStore.generate(email)
        emailSender.sendOtp(email, code)
    }

    fun verifyOtp(email: String, code: String): VerifyOtpResponse {
        // Anti brute-force on the 6-digit code (~1M space, would crack in seconds otherwise).
        otpVerifyPerEmail.checkOrThrow("email:${email.lowercase()}")
        if (!otpStore.verify(email, code)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OTP")
        }
        val existing = userStore.findByEmail(email)
        val isNewUser = existing == null || existing.handle == null
        val user = existing ?: userStore.create(UserEntity(email = email))

        val (access, refresh) = issue(user.id)
        return VerifyOtpResponse(
            accessToken = access,
            refreshToken = refresh,
            expiresIn = jwtService.accessTokenExpirySeconds,
            user = if (!isNewUser) user.toDto() else null,
            isNewUser = isNewUser,
        )
    }

    fun completeSignup(userId: String, handle: String, bio: String?): CompleteSignupResponse {
        if (userStore.handleExists(handle)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Handle already taken")
        }
        userStore.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        userStore.setProfile(userId, handle, bio)
        return CompleteSignupResponse(user = userStore.findById(userId)!!.toDto())
    }

    fun refresh(refreshToken: String): RefreshResponse {
        val userId = refreshTokenStore.useToken(refreshToken)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked refresh token")
        val (access, newRefresh) = issue(userId)
        return RefreshResponse(
            accessToken = access,
            refreshToken = newRefresh,
            expiresIn = jwtService.accessTokenExpirySeconds,
        )
    }

    fun signOut(refreshToken: String) {
        refreshTokenStore.revoke(refreshToken)
    }

    private fun issue(userId: String): Pair<String, String> {
        val access = jwtService.generateAccessToken(userId)
        val refresh = UUID.randomUUID().toString()
        refreshTokenStore.store(refresh, userId)
        return access to refresh
    }
}

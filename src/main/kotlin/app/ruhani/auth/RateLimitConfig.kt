package app.ruhani.auth

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Dev defaults are deliberately loose so local poking isn't annoying:
 *   per-email   5 / hour       (caps OTP-spam to one person's inbox)
 *   per-IP     20 / hour       (caps OTP-spam from one host)
 *   verify     10 / hour/email (anti brute-force on the 6-digit code)
 *
 * Override per-deploy via the matching env vars in application.yml.
 */
@Configuration
class RateLimitConfig {

    @Bean
    @Qualifier("otpRequestPerEmail")
    fun otpRequestPerEmail(
        @Value("\${ruhani.rate-limit.otp-request.per-email-per-hour:5}") max: Int,
    ): RateLimiter = RateLimiter(maxRequests = max, windowSeconds = 3600)

    @Bean
    @Qualifier("otpRequestPerIp")
    fun otpRequestPerIp(
        @Value("\${ruhani.rate-limit.otp-request.per-ip-per-hour:20}") max: Int,
    ): RateLimiter = RateLimiter(maxRequests = max, windowSeconds = 3600)

    @Bean
    @Qualifier("otpVerifyPerEmail")
    fun otpVerifyPerEmail(
        @Value("\${ruhani.rate-limit.otp-verify.per-email-per-hour:10}") max: Int,
    ): RateLimiter = RateLimiter(maxRequests = max, windowSeconds = 3600)
}

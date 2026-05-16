package app.ruhani.auth

import app.ruhani.model.*
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/request-otp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun requestOtp(@RequestBody req: RequestOtpRequest) {
        authService.requestOtp(req.email)
    }

    @PostMapping("/verify-otp")
    fun verifyOtp(@RequestBody req: VerifyOtpRequest): VerifyOtpResponse =
        authService.verifyOtp(req.email, req.code)

    @PostMapping("/complete-signup")
    fun completeSignup(
        @RequestBody req: CompleteSignupRequest,
        auth: Authentication,
    ): CompleteSignupResponse = authService.completeSignup(auth.name, req.handle, req.bio)

    @PostMapping("/refresh")
    fun refresh(@RequestBody req: RefreshRequest): RefreshResponse =
        authService.refresh(req.refreshToken)

    @PostMapping("/sign-out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun signOut(@RequestBody req: SignOutRequest) {
        authService.signOut(req.refreshToken)
    }
}

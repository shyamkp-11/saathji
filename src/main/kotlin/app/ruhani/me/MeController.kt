package app.ruhani.me

import app.ruhani.auth.UserStore
import app.ruhani.model.UserDto
import app.ruhani.model.toDto
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class MeController(private val userStore: UserStore) {

    @GetMapping("/me")
    fun getMe(auth: Authentication): UserDto =
        userStore.findById(auth.name)?.toDto()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
}

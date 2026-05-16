package app.ruhani.auth

import app.ruhani.model.UserEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional
class UserStore(private val users: UserRepository) {

    fun findById(id: String): UserEntity? = users.findById(id).orElse(null)

    @Transactional(readOnly = true)
    fun findByEmail(email: String): UserEntity? = users.findByEmail(email.lowercase())

    @Transactional(readOnly = true)
    fun findByHandle(handle: String): UserEntity? = users.findByHandle(handle.lowercase())

    @Transactional(readOnly = true)
    fun handleExists(handle: String): Boolean = users.existsByHandle(handle.lowercase())

    fun create(user: UserEntity): UserEntity {
        user.email = user.email.lowercase()
        return users.save(user)
    }

    fun setProfile(id: String, handle: String, bio: String?) {
        val user = users.findById(id).orElse(null) ?: return
        user.handle = handle.lowercase()
        user.bio = bio
        users.save(user)
    }
}

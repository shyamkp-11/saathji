package app.ruhani.auth

import app.ruhani.model.UserEntity
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class UserStore {
    private val byId = ConcurrentHashMap<String, UserEntity>()
    private val byEmail = ConcurrentHashMap<String, String>()   // email (lower) → id
    private val byHandle = ConcurrentHashMap<String, String>()  // handle (lower) → id

    fun findById(id: String): UserEntity? = byId[id]

    fun findByEmail(email: String): UserEntity? = byEmail[email.lowercase()]?.let { byId[it] }

    fun findByHandle(handle: String): UserEntity? = byHandle[handle.lowercase()]?.let { byId[it] }

    fun handleExists(handle: String): Boolean = byHandle.containsKey(handle.lowercase())

    fun create(user: UserEntity): UserEntity {
        byId[user.id] = user
        byEmail[user.email.lowercase()] = user.id
        return user
    }

    fun setProfile(id: String, handle: String, bio: String?) {
        val user = byId[id] ?: return
        user.handle = handle
        user.bio = bio
        byHandle[handle.lowercase()] = id
    }
}

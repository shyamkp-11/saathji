package app.ruhani.auth

import app.ruhani.model.RefreshTokenEntity
import app.ruhani.model.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<UserEntity, String> {
    fun findByEmail(email: String): UserEntity?
    fun findByHandle(handle: String): UserEntity?
    fun existsByHandle(handle: String): Boolean
}

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, String> {
    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.status = 'REVOKED' WHERE r.userId = :userId AND r.status = 'VALID'")
    fun revokeAllValidForUser(@Param("userId") userId: String): Int
}

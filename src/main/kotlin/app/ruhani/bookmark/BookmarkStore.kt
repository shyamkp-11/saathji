package app.ruhani.bookmark

import org.springframework.stereotype.Component
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

@Component
class BookmarkStore {
    private val bookmarks = ConcurrentHashMap<String, MutableSet<String>>() // userId → postIds

    fun add(userId: String, postId: String) {
        bookmarks.getOrPut(userId) { Collections.newSetFromMap(ConcurrentHashMap()) }.add(postId)
    }

    fun remove(userId: String, postId: String) {
        bookmarks[userId]?.remove(postId)
    }

    fun getPostIds(userId: String): Set<String> = bookmarks[userId]?.toSet() ?: emptySet()
}

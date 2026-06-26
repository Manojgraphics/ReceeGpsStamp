package com.receegpsstamp.data.capture

import androidx.compose.runtime.mutableStateListOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide buffer for photos captured by the camera before they are attached to a
 * recce or installation entry.
 *
 * This MUST be a @Singleton. Each navigation destination gets its own [AppViewModel]
 * instance (because `hiltViewModel()` scopes the ViewModel to the NavBackStackEntry),
 * so the camera screen and the recce/installation screen would otherwise see different,
 * unconnected lists. Holding the buffer in a singleton makes every AppViewModel instance
 * share the same captured photos.
 */
@Singleton
class PhotoCaptureBuffer @Inject constructor() {
    val photos = mutableStateListOf<String>()

    fun add(path: String) {
        photos.add(path)
    }

    fun clear() {
        photos.clear()
    }

    fun snapshot(): List<String> = photos.toList()
}

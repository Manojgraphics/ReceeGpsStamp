package com.receegpsstamp.data.local

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The signed-in surveyor's profile, collected once at sign-in. Stored locally. */
data class AppProfile(
    val name: String = "",
    val surname: String = "",
    val mobile: String = "",
    val email: String = "",
    val gender: String = "",   // "Male" | "Female" | "" (not set)
    val city: String = "",
    val state: String = "",
) {
    val isComplete: Boolean get() = name.isNotBlank() && mobile.isNotBlank()
    val fullName: String get() = listOf(name, surname).filter { it.isNotBlank() }.joinToString(" ")
}

@Singleton
class ProfileStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val file = File(context.filesDir, "rgs_profile.json")
    private val gson = Gson()
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<AppProfile> = _profile.asStateFlow()

    private fun load(): AppProfile = try {
        if (file.exists()) gson.fromJson(file.readText(), AppProfile::class.java) ?: AppProfile() else AppProfile()
    } catch (_: Throwable) { AppProfile() }

    fun update(transform: (AppProfile) -> AppProfile) {
        val next = transform(_profile.value)
        _profile.value = next
        ioScope.launch {
            try {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(gson.toJson(next))
                tmp.renameTo(file)
            } catch (_: Throwable) { }
        }
    }
}

package com.receegpsstamp.feature.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.receegpsstamp.data.auth.AuthRepository
import com.receegpsstamp.data.auth.PhoneAuthState
import com.receegpsstamp.data.auth.PhoneSignInController
import com.receegpsstamp.data.local.AppProfile
import com.receegpsstamp.data.local.AppSettings
import com.receegpsstamp.data.local.ProfileStore
import com.receegpsstamp.data.local.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val settingsStore: SettingsStore,
    private val profileStore: ProfileStore,
) : ViewModel() {

    val user: StateFlow<FirebaseUser?> = authRepo.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepo.currentUser)

    val settings: StateFlow<AppSettings> = settingsStore.settings

    /** The signed-in surveyor's editable profile (name, mobile, email, gender). */
    val profile: StateFlow<AppProfile> = profileStore.profile

    fun update(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    fun saveProfile(
        name: String, surname: String, mobile: String, email: String,
        gender: String, city: String, state: String,
    ) {
        profileStore.update {
            it.copy(
                name = name.trim(), surname = surname.trim(), mobile = mobile.trim(),
                email = email.trim(), gender = gender, city = city.trim(), state = state.trim(),
            )
        }
    }

    fun getSignInIntent(): android.content.Intent = authRepo.getSignInIntent()

    fun handleSignInResult(data: android.content.Intent?) {
        viewModelScope.launch {
            try {
                val account = com.google.android.gms.auth.api.signin.GoogleSignIn
                    .getSignedInAccountFromIntent(data)
                    .getResult(com.google.android.gms.common.api.ApiException::class.java)
                account.idToken?.let { authRepo.firebaseAuthWithGoogle(it) }
            } catch (_: Exception) { /* user cancelled or failed — stay offline */ }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepo.signOut() }
    }

    // ── Phone-OTP (shared controller; signing in flips [user] so the UI updates reactively) ──
    private val phone = PhoneSignInController(authRepo, viewModelScope)
    val phoneState: StateFlow<PhoneAuthState> = phone.state
    val phoneError: StateFlow<String?> = phone.error

    fun sendOtp(phoneE164: String, activity: Activity) = phone.sendOtp(phoneE164, activity)
    fun resendOtp(activity: Activity) = phone.resendOtp(activity)
    fun verifyOtp(code: String) = phone.verifyOtp(code)
    fun resetPhoneFlow() = phone.reset()
    fun consumePhoneError() = phone.consumeError()
}

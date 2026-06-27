package com.receegpsstamp.feature.login

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.receegpsstamp.data.auth.AuthRepository
import com.receegpsstamp.data.auth.PhoneAuthState
import com.receegpsstamp.data.auth.PhoneSignInController
import com.receegpsstamp.data.local.AppProfile
import com.receegpsstamp.data.local.ProfileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val profileStore: ProfileStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    val profile: StateFlow<AppProfile> = profileStore.profile

    // Sign-in is compulsory — only skip the login screen when a real Firebase session already exists.
    val canSkipLogin: Boolean get() = authRepo.isSignedIn

    fun getSignInIntent(): Intent = authRepo.getSignInIntent()

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    authRepo.firebaseAuthWithGoogle(idToken)
                    // Pre-fill name/surname from the Google account (mobile is still asked).
                    profileStore.update { p ->
                        p.copy(
                            name = p.name.ifBlank { account.givenName ?: account.displayName?.substringBefore(" ") ?: "" },
                            surname = p.surname.ifBlank { account.familyName ?: account.displayName?.substringAfter(" ", "") ?: "" },
                        )
                    }
                    _uiState.value = LoginUiState.Success
                } else {
                    _uiState.value = LoginUiState.Error("No ID token received")
                }
            } catch (e: ApiException) {
                _uiState.value = LoginUiState.Error("Sign-in failed: ${e.statusCode}")
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun saveProfile(name: String, surname: String, mobile: String) {
        profileStore.update { it.copy(name = name.trim(), surname = surname.trim(), mobile = mobile.trim()) }
    }

    // ── Phone-OTP (delegated to the shared controller) ──
    private val phone = PhoneSignInController(
        authRepo, viewModelScope,
        onSignedIn = { user ->
            // Mobile is known from the verified number — prefill it (strip the +91).
            val local = user.phoneNumber?.removePrefix("+91")?.takeLast(10) ?: ""
            profileStore.update { p -> p.copy(mobile = p.mobile.ifBlank { local }) }
            _uiState.value = LoginUiState.Success
        },
    )
    val phoneState: StateFlow<PhoneAuthState> = phone.state

    init {
        // Surface phone-flow errors through uiState so the existing error toast shows them.
        viewModelScope.launch { phone.error.collect { msg -> if (msg != null) _uiState.value = LoginUiState.Error(msg) } }
    }

    fun sendOtp(phoneE164: String, activity: Activity) = phone.sendOtp(phoneE164, activity)
    fun resendOtp(activity: Activity) = phone.resendOtp(activity)
    fun verifyOtp(code: String) = phone.verifyOtp(code)
    fun resetPhoneFlow() = phone.reset()
}

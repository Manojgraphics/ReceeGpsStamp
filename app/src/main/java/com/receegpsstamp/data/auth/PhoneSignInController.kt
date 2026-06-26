package com.receegpsstamp.data.auth

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the phone-OTP flow currently is. */
sealed interface PhoneAuthState {
    data object EnterNumber : PhoneAuthState
    data object Sending : PhoneAuthState
    data class CodeSent(val phone: String) : PhoneAuthState
    data object Verifying : PhoneAuthState
}

/**
 * Reusable phone-OTP state machine, shared by the Login and Settings screens so the flow lives in
 * one place. Construct one per ViewModel (pass the VM's [scope]); the UI collects [state] + [error].
 * [onSignedIn] fires once a FirebaseUser is obtained — by instant/auto verification or a typed code.
 */
class PhoneSignInController(
    private val authRepo: AuthRepository,
    private val scope: CoroutineScope,
    private val onSignedIn: (FirebaseUser) -> Unit = {},
) {
    private val _state = MutableStateFlow<PhoneAuthState>(PhoneAuthState.EnterNumber)
    val state: StateFlow<PhoneAuthState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var pendingPhone: String = ""

    /** Sends an OTP to [phoneE164] (e.g. "+919876543210"); [activity] hosts the verification check. */
    fun sendOtp(phoneE164: String, activity: Activity) {
        pendingPhone = phoneE164
        _error.value = null
        _state.value = PhoneAuthState.Sending
        authRepo.verifyPhoneNumber(phoneE164, activity, callbacks())
    }

    /** Re-sends a code to the same number using the cached resend token. */
    fun resendOtp(activity: Activity) {
        if (pendingPhone.isBlank()) return
        _state.value = PhoneAuthState.Sending
        authRepo.verifyPhoneNumber(pendingPhone, activity, callbacks(), resendToken)
    }

    fun verifyOtp(code: String) {
        val id = verificationId ?: run { _error.value = "Code expired — request a new OTP"; return }
        _state.value = PhoneAuthState.Verifying
        complete(authRepo.phoneCredential(id, code))
    }

    /** Back to the number screen (e.g. "Change number"). */
    fun reset() {
        verificationId = null
        _error.value = null
        _state.value = PhoneAuthState.EnterNumber
    }

    fun consumeError() { _error.value = null }

    private fun callbacks() = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) = complete(credential)
        override fun onVerificationFailed(e: FirebaseException) {
            _state.value = PhoneAuthState.EnterNumber
            _error.value = humanError(e)
        }
        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = id
            resendToken = token
            _state.value = PhoneAuthState.CodeSent(pendingPhone)
        }
    }

    private fun complete(credential: PhoneAuthCredential) {
        scope.launch {
            _state.value = PhoneAuthState.Verifying
            try {
                val user = authRepo.signInWithPhoneCredential(credential)
                if (user != null) {
                    onSignedIn(user)
                } else {
                    _state.value = PhoneAuthState.CodeSent(pendingPhone)
                    _error.value = "Phone sign-in failed — try again"
                }
            } catch (_: Exception) {
                _state.value = PhoneAuthState.CodeSent(pendingPhone)
                _error.value = "Invalid or expired code"
            }
        }
    }

    private fun humanError(e: FirebaseException): String {
        val m = e.message ?: return "Phone verification failed"
        return when {
            m.contains("invalid", true) && m.contains("phone", true) -> "Invalid phone number"
            m.contains("quota", true) || m.contains("too many", true) -> "Too many attempts — try again later"
            m.contains("network", true) -> "Network error — check your connection"
            else -> m
        }
    }
}

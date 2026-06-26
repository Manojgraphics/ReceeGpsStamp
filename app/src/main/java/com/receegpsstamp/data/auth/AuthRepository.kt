package com.receegpsstamp.data.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
) {
    val currentUser: FirebaseUser? get() = auth.currentUser
    val isSignedIn: Boolean get() = auth.currentUser != null

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun getSignInClient(): GoogleSignInClient {
        val webClientId = context.getString(
            context.resources.getIdentifier("default_web_client_id", "string", context.packageName),
        )
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(): Intent = getSignInClient().signInIntent

    suspend fun firebaseAuthWithGoogle(idToken: String): FirebaseUser? {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        return result.user
    }

    suspend fun signOut() {
        auth.signOut()
        try { getSignInClient().signOut().await() } catch (_: Exception) {}
    }

    // ── Phone-OTP auth ───────────────────────────────────────────────────────────────────────
    /**
     * Starts phone-number verification. Firebase drives the rest through [callbacks]:
     * `onCodeSent` (show the OTP box), `onVerificationCompleted` (instant/auto-retrieval — sign in
     * straight away) or `onVerificationFailed`. Needs the hosting [activity] for the reCAPTCHA /
     * Play-Integrity check. Pass [resendToken] to re-send a code to the same number.
     */
    fun verifyPhoneNumber(
        phoneNumber: String,
        activity: Activity,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
    ) {
        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (resendToken != null) builder.setForceResendingToken(resendToken)
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    /** Builds a credential from the typed OTP [code] + the verificationId from `onCodeSent`. */
    fun phoneCredential(verificationId: String, code: String): PhoneAuthCredential =
        PhoneAuthProvider.getCredential(verificationId, code)

    /** Completes sign-in with a phone credential (typed code or auto-retrieved). */
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): FirebaseUser? =
        auth.signInWithCredential(credential).await().user
}

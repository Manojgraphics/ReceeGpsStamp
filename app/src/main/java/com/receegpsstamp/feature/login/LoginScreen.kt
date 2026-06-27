package com.receegpsstamp.feature.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.receegpsstamp.data.auth.PhoneAuthState
import com.receegpsstamp.ui.theme.AppYellow
import com.receegpsstamp.ui.theme.BrandGradient
import com.receegpsstamp.ui.theme.SplashBg
import kotlinx.coroutines.flow.first

private val BoxBg = Color(0xFF1A2530)
private val BoxBorder = Color(0xFF2A3540)

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val phoneState by viewModel.phoneState.collectAsStateWithLifecycle()
    var showProfile by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var pName by rememberSaveable { mutableStateOf("") }
    var pSurname by rememberSaveable { mutableStateOf("") }
    var pMobile by rememberSaveable { mutableStateOf("") }
    var phoneDigits by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val versionName = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Exception) { "?" }
    }

    LaunchedEffect(Unit) {
        if (viewModel.canSkipLogin) onSignedIn()
    }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            if (profile.isComplete) {
                onSignedIn()
            } else {
                // Returning user signing in on a fresh device: their profile is saved in the cloud but
                // pulls down asynchronously. Wait briefly for it before re-asking the name.
                checking = true
                val synced = kotlinx.coroutines.withTimeoutOrNull(4000) { viewModel.profile.first { it.isComplete } }
                checking = false
                if (synced != null) {
                    onSignedIn()
                } else {
                    // New user (no saved profile) — ask name once. Pre-fill anything from Google.
                    val p = viewModel.profile.value
                    pName = p.name; pSurname = p.surname; pMobile = p.mobile
                    showProfile = true
                }
            }
        } else if (uiState is LoginUiState.Error) {
            // Make the failure unmissable so the real cause can be diagnosed.
            android.widget.Toast.makeText(context, (uiState as LoginUiState.Error).message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Always handle the result — even on cancel/failure — so the real error code surfaces
        // instead of failing silently (silent cancels were hiding sign-in config errors).
        viewModel.handleSignInResult(result.data)
    }

    Column(
        Modifier.fillMaxSize().background(SplashBg).imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).size(54.dp).background(BrandGradient, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("RGS", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SplashBg)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Recce GPS Stamp", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
            letterSpacing = (-0.3).sp, modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "The GPS-stamped survey camera for field recces — fast, offline and reliable.",
            fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.45f), lineHeight = 18.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(22.dp))

        if (checking) {
            Spacer(Modifier.height(30.dp))
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = AppYellow, strokeWidth = 2.5.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                "Setting up your account…", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        } else if (!showProfile) {
        // What the app does
        FeatureRow("GPS location, address & time stamped on every photo")
        FeatureRow("Organise recces by company & distributor")
        FeatureRow("Capture shop media — size, creative & quantity")
        FeatureRow("Export professional PDF & Excel reports")
        FeatureRow("Mark up photos — shapes, arrows, text & sizes")

        Spacer(Modifier.height(24.dp))

        // ── Phone-OTP sign-in (primary) ──
        val showCode = phoneState is PhoneAuthState.CodeSent || phoneState is PhoneAuthState.Verifying
        Column(Modifier.fillMaxWidth().background(BoxBg, RoundedCornerShape(16.dp)).border(1.dp, BoxBorder, RoundedCornerShape(16.dp)).padding(16.dp)) {
            if (!showCode) {
                Text("Sign in with your mobile number", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(12.dp))
                DarkField("Mobile number (+91)", phoneDigits, KeyboardType.Phone) { phoneDigits = it.filter { c -> c.isDigit() }.take(10) }
                Spacer(Modifier.height(10.dp))
                YellowButton(
                    if (phoneState is PhoneAuthState.Sending) "Sending OTP…" else "Send OTP",
                    enabled = phoneDigits.length == 10 && phoneState !is PhoneAuthState.Sending && activity != null,
                    loading = phoneState is PhoneAuthState.Sending,
                ) { activity?.let { viewModel.sendOtp("+91$phoneDigits", it) } }
            } else {
                Text("Enter the 6-digit code", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(2.dp))
                Text("Sent to +91 $phoneDigits", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
                Spacer(Modifier.height(12.dp))
                DarkField("6-digit code", otp, KeyboardType.Number) { otp = it.filter { c -> c.isDigit() }.take(6) }
                Spacer(Modifier.height(10.dp))
                YellowButton(
                    if (phoneState is PhoneAuthState.Verifying) "Verifying…" else "Verify & continue",
                    enabled = otp.length == 6 && phoneState !is PhoneAuthState.Verifying,
                    loading = phoneState is PhoneAuthState.Verifying,
                ) { viewModel.verifyOtp(otp) }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Change number", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { otp = ""; viewModel.resetPhoneFlow() },
                    )
                    Text(
                        "Resend code", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppYellow,
                        modifier = Modifier.clickable { activity?.let { viewModel.resendOtp(it) } },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(BoxBorder))
            Text("  OR  ", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
            Box(Modifier.weight(1f).height(1.dp).background(BoxBorder))
        }
        Spacer(Modifier.height(16.dp))

        // Google sign-in
        Column(Modifier.fillMaxWidth().background(BoxBg, RoundedCornerShape(16.dp)).border(1.dp, BoxBorder, RoundedCornerShape(16.dp)).padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp))
                    .clickable(enabled = uiState !is LoginUiState.Loading) {
                        try {
                            launcher.launch(viewModel.getSignInIntent())
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Sign-in can't start: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    .padding(13.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (uiState is LoginUiState.Loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = BoxBg, strokeWidth = 2.dp)
                } else {
                    Box(
                        Modifier.size(20.dp).background(
                            Brush.sweepGradient(0f to Color(0xFFEA4335), 0.25f to Color(0xFFFBBC05), 0.5f to Color(0xFF34A853), 0.75f to Color(0xFF4285F4), 1f to Color(0xFFEA4335)),
                            CircleShape,
                        ),
                        contentAlignment = Alignment.Center,
                    ) { Text("G", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (uiState is LoginUiState.Loading) "Signing in…" else "Continue with Google",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BoxBg,
                )
            }

            if (uiState is LoginUiState.Error) {
                Spacer(Modifier.height(8.dp))
                Text((uiState as LoginUiState.Error).message, fontSize = 12.sp, color = Color(0xFFE08A8A), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Sign in with your phone number or Google to continue.",
            fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f), textAlign = TextAlign.Center, lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(18.dp))
        Text(
            "v$versionName",
            fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f), textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        } else {
            // ── Profile setup (asked once after sign-in) ──
            Text("Almost done", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Tell us who's doing the recces.", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.45f))
            Spacer(Modifier.height(18.dp))
            DarkField("First name", pName) { pName = it }
            Spacer(Modifier.height(12.dp))
            DarkField("Surname", pSurname) { pSurname = it }
            Spacer(Modifier.height(12.dp))
            DarkField("Mobile number", pMobile, KeyboardType.Phone) { pMobile = it.filter { c -> c.isDigit() }.take(15) }
            Spacer(Modifier.height(20.dp))
            val ready = pName.isNotBlank() && pMobile.length >= 7
            Box(
                Modifier.fillMaxWidth()
                    .background(if (ready) BrandGradient else SolidColor(AppYellow.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                    .clickable(enabled = ready) { viewModel.saveProfile(pName, pSurname, pMobile); onSignedIn() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Continue", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SplashBg)
            }
        }
    }
}

@Composable
private fun DarkField(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = AppYellow, unfocusedBorderColor = BoxBorder,
            focusedLabelColor = AppYellow, unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
            cursorColor = AppYellow,
            focusedContainerColor = BoxBg, unfocusedContainerColor = BoxBg,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FeatureRow(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).background(AppYellow.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            Text("✓", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = AppYellow)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f), lineHeight = 17.sp)
    }
}

@Composable
private fun YellowButton(text: String, enabled: Boolean, loading: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .background(if (enabled || loading) BrandGradient else SolidColor(AppYellow.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), color = SplashBg, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = SplashBg)
        }
    }
}

/** Unwraps the hosting Activity from a Compose Context (needed for Firebase phone verification). */
private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

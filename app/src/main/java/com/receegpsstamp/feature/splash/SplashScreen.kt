package com.receegpsstamp.feature.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.receegpsstamp.ui.theme.AppYellow
import com.receegpsstamp.ui.theme.SplashBg
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(600), label = "splash")

    LaunchedEffect(Unit) { visible = true; delay(1800); onFinished() }

    Box(Modifier.fillMaxSize().background(SplashBg), contentAlignment = Alignment.Center) {
        Column(Modifier.alpha(alpha), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(88.dp)
                    .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = AppYellow.copy(0.35f), spotColor = AppYellow.copy(0.35f))
                    .background(AppYellow, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("RGS", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = SplashBg, letterSpacing = (-0.5).sp)
            }
            Spacer(Modifier.height(18.dp))
            Text("Recce GPS Stamp", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = (-0.3).sp)
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(7.dp).background(AppYellow, CircleShape))
                Box(Modifier.size(7.dp).background(Color.White.copy(0.2f), CircleShape))
                Box(Modifier.size(7.dp).background(Color.White.copy(0.2f), CircleShape))
            }
        }
    }
}

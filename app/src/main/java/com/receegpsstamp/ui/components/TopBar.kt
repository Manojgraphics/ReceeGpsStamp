package com.receegpsstamp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.receegpsstamp.ui.theme.BrandGrey
import com.receegpsstamp.ui.theme.RgsIcons

@Composable
fun RgsTopBar(
    title: String,
    titleBold: Boolean = false,
    subtitle: String? = null,
    showBack: Boolean = true,
    onNav: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().background(BrandGrey)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 6.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNav, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (showBack) RgsIcons.ArrowBack else RgsIcons.Menu,
                    contentDescription = if (showBack) "Back" else "Menu",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontSize = 16.sp, fontWeight = if (titleBold) FontWeight.Bold else FontWeight.SemiBold,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }
            trailing?.invoke()
        }
    }
}

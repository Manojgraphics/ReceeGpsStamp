package com.receegpsstamp.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.receegpsstamp.ui.theme.NeutralTextSoft

@Composable
fun SectionHeader(text: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(top = 3.dp)) {
        Text(
            text.uppercase(), fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp, color = NeutralTextSoft,
        )
        if (subtitle != null) {
            Spacer(Modifier.width(2.dp))
            Text(subtitle, fontSize = 10.sp, color = NeutralTextSoft)
        }
    }
}

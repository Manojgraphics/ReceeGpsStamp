package com.receegpsstamp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.receegpsstamp.ui.theme.NeutralOutline
import com.receegpsstamp.ui.theme.NeutralSurface
import com.receegpsstamp.ui.theme.NeutralText
import com.receegpsstamp.ui.theme.NeutralTextSoft
import com.receegpsstamp.ui.theme.RgsIcons

@Composable
fun CompactDropdown(
    label: String? = null,
    value: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .border(1.dp, NeutralOutline, RoundedCornerShape(5.dp))
                .clickable { onClick() }
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, fontSize = 14.sp, color = NeutralText, modifier = Modifier.weight(1f))
            Icon(
                RgsIcons.DropDown, contentDescription = null,
                tint = NeutralTextSoft, modifier = Modifier.size(18.dp),
            )
        }
        if (label != null) {
            Text(
                label, fontSize = 11.sp, color = NeutralTextSoft,
                modifier = Modifier
                    .offset(x = 11.dp, y = (-8).dp)
                    .background(NeutralSurface)
                    .padding(horizontal = 5.dp),
            )
        }
    }
}

@Composable
fun CompactTextField(
    label: String? = null,
    value: String,
    onValueChange: (String) -> Unit = {},
    placeholder: String? = null,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = NeutralText),
            cursorBrush = SolidColor(NeutralText),
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .border(1.dp, NeutralOutline, RoundedCornerShape(5.dp))
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, fontSize = 14.sp, color = NeutralTextSoft)
                        }
                        innerTextField()
                    }
                    if (trailingIcon != null) trailingIcon()
                }
            },
        )
        if (label != null) {
            Text(
                label, fontSize = 11.sp, color = NeutralTextSoft,
                modifier = Modifier
                    .offset(x = 11.dp, y = (-8).dp)
                    .background(NeutralSurface)
                    .padding(horizontal = 5.dp),
            )
        }
    }
}

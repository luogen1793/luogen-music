package com.luogen.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.music.ui.theme.LgSecondary
import com.luogen.music.ui.theme.TextDim
import com.luogen.music.ui.theme.TextMain

/**
 * 圆角输入框：无直角边框、无 Material 默认下划线，毛玻璃卡片风格。
 * 用于昵称修改、评论回复等弹窗输入场景。
 */
@Composable
fun RoundInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    maxLength: Int = 200,
    keyboardType: KeyboardType = KeyboardType.Text,
    contentColor: Color = TextMain,
    backgroundColor: Color = Color.White.copy(alpha = 0.07f),
    borderColor: Color = LgSecondary.copy(alpha = 0.35f),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { v -> if (v.length <= maxLength) onValueChange(v) },
            singleLine = singleLine,
            textStyle = TextStyle(color = contentColor, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, color = TextDim, fontSize = 15.sp)
                }
                inner()
            }
        }
    }
}
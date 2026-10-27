package com.lottery.analyzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary

@Composable
fun StatChip(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subText: String? = null,
    backgroundColor: Color = Color(0xFFF1F5F9),
    valueColor: Color = TextPrimary
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (!subText.isNullOrEmpty()) {
            Text(
                text = subText,
                color = TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

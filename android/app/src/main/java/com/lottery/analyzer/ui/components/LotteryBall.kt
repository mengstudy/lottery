package com.lottery.analyzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.ui.theme.BlueDark
import com.lottery.analyzer.ui.theme.BluePrimary
import com.lottery.analyzer.ui.theme.OrangePrimary
import com.lottery.analyzer.ui.theme.RedDark
import com.lottery.analyzer.ui.theme.RedPrimary

enum class BallStyle {
    RED, BLUE, ORANGE, NEUTRAL
}

@Composable
fun LotteryBall(
    number: Int,
    modifier: Modifier = Modifier,
    style: BallStyle = BallStyle.RED,
    size: Dp = 38.dp,
    badgeText: String? = null,
    badgeBgColor: Color = Color(0xFF1E293B),
    isHighlighted: Boolean = true,
    isHeartSelected: Boolean = false, // 是否为自选心水球
    freqCount: Int = 0,               // 他人购买频次
    fontSize: Int = 16
) {
    val gradientBrush = when {
        isHeartSelected -> Brush.radialGradient(
            colors = listOf(Color(0xFFFF5252), Color(0xFFE53935), Color(0xFFB71C1C)),
            radius = 60f
        )
        style == BallStyle.RED -> Brush.radialGradient(
            colors = listOf(Color(0xFFFF7875), RedPrimary, RedDark),
            radius = 60f
        )
        style == BallStyle.BLUE -> Brush.radialGradient(
            colors = listOf(Color(0xFF69C0FF), BluePrimary, BlueDark),
            radius = 60f
        )
        style == BallStyle.ORANGE -> Brush.radialGradient(
            colors = listOf(Color(0xFFFFC069), OrangePrimary, Color(0xFFD46B08)),
            radius = 60f
        )
        else -> Brush.radialGradient(
            colors = listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0), Color(0xFFCBD5E1)),
            radius = 60f
        )
    }

    val textColor = if (style == BallStyle.NEUTRAL && !isHeartSelected) Color(0xFF334155) else Color.White

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(
                    elevation = if (isHighlighted || isHeartSelected) 4.dp else 1.dp,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .background(brush = gradientBrush)
                .then(
                    when {
                        isHeartSelected -> Modifier.border(2.dp, Color(0xFFFFD700), CircleShape) // 心水金色描边
                        !isHighlighted -> Modifier.border(1.dp, Color(0xFFE2E8F0), CircleShape)
                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format("%02d", number),
                color = textColor,
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 1. 他人购买频次角标 (右上角，如 x3)
        if (freqCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-4).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF7C3AED))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "x$freqCount",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (!badgeText.isNullOrEmpty()) {
            // 普通角标 (如遗漏期数)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-3).dp)
                    .clip(CircleShape)
                    .background(badgeBgColor)
                    .size(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

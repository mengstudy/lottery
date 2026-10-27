package com.lottery.analyzer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lottery.analyzer.data.model.LotteryType
import com.lottery.analyzer.ui.theme.BluePrimary
import com.lottery.analyzer.ui.theme.OrangePrimary
import com.lottery.analyzer.ui.theme.RedPrimary
import com.lottery.analyzer.ui.theme.TextPrimary
import com.lottery.analyzer.ui.theme.TextSecondary

@Composable
fun LotteryTypeSelectorBar(
    currentType: LotteryType,
    onSelectType: (LotteryType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LotteryType.values().forEach { type ->
            val isSelected = type == currentType
            val activeColor = when (type) {
                LotteryType.SSQ -> RedPrimary
                LotteryType.DLT -> BluePrimary
                LotteryType.KL8 -> OrangePrimary
            }

            val bgColor by animateColorAsState(
                targetValue = if (isSelected) activeColor else Color.Transparent,
                animationSpec = tween(durationMillis = 200),
                label = "typeBgColor"
            )

            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else TextPrimary,
                animationSpec = tween(durationMillis = 200),
                label = "typeTextColor"
            )

            val subTextColor by animateColorAsState(
                targetValue = if (isSelected) Color.White.copy(alpha = 0.85f) else TextSecondary,
                animationSpec = tween(durationMillis = 200),
                label = "typeSubTextColor"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isSelected) {
                            Modifier.shadow(elevation = 3.dp, shape = RoundedCornerShape(10.dp))
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .clickable { onSelectType(type) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 提示圆点
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else activeColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = type.title,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = when (type) {
                            LotteryType.SSQ -> "6+1红蓝"
                            LotteryType.DLT -> "5+2前后区"
                            LotteryType.KL8 -> "80选20"
                        },
                        fontSize = 10.sp,
                        color = subTextColor
                    )
                }
            }
        }
    }
}

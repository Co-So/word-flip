package com.wordflip.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordflip.core.ui.apple.AppleGlassSurface
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.apple.applePress

/**
 * 展示五个主入口的浮动玻璃导航，并将选中事件交给上层导航控制器处理。
 */
@Composable
fun FloatingTabBar(
    selectedTab: MainTab?,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppleGlassSurface(modifier = modifier, cornerRadius = 26.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .selectableGroup(),
        ) {
            MainTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val interactions = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (selected) {
                                AppleUi.colors.accent.copy(alpha = 0.12f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        )
                        .applePress(interactions)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            interactionSource = interactions,
                            indication = null,
                            onClick = { onSelect(tab) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (selected) AppleUi.colors.accent else AppleUi.colors.secondaryText,
                    )
                    Text(
                        text = tab.label,
                        color = if (selected) AppleUi.colors.accent else AppleUi.colors.secondaryText,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

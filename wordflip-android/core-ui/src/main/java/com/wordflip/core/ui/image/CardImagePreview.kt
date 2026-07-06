package com.wordflip.core.ui.image

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.ui.theme.SageStudyCardBack

/**
 * 图片编辑器 WYSIWYG 卡片预览（REQ-SNAP-5）：与 FlipCard 背面同比例、同圆角，虚线框标出裁剪边界。
 */
@Composable
fun CardImagePreview(
    imageUri: String,
    cn: String,
    transform: ImageTransform,
    filters: ImageFilters,
    showCnOnImage: Boolean,
    onTransformChange: (ImageTransform) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "拖动图片调整位置",
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(3f / 4.2f),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(12.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = SageStudyCardBack,
            ) {
                WordImageBack(
                    imageUri = imageUri,
                    cn = cn,
                    transform = transform,
                    filters = filters,
                    showCnOnImage = showCnOnImage,
                    draggable = true,
                    onTransformChange = onTransformChange,
                )
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val inset = 4.dp.toPx()
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.75f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    ),
                )
            }
        }
        Text(
            text = hint,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )
    }
}

package com.wordflip.core.ui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.ui.theme.SageStudyCardBackText

/**
 * 卡片背面图片展示（REQ-STUDY-19、P3-A07）。
 */
@Composable
fun WordImageBack(
    imageUri: String?,
    cn: String,
    transform: ImageTransform?,
    filters: ImageFilters?,
    showCnOnImage: Boolean,
    modifier: Modifier = Modifier,
    draggable: Boolean = false,
    onTransformChange: ((ImageTransform) -> Unit)? = null,
) {
    val t = transform ?: ImageTransform()
    val f = filters ?: ImageFilters()
    var dragOffsetX by remember(imageUri) { mutableStateOf(t.offsetX) }
    var dragOffsetY by remember(imageUri) { mutableStateOf(t.offsetY) }

    // 外部重置/撤销时同步拖动偏移
    LaunchedEffect(t.offsetX, t.offsetY) {
        dragOffsetX = t.offsetX
        dragOffsetY = t.offsetY
    }

    val colorFilter = remember(f) {
        ColorFilter.colorMatrix(
            ImageFilterMatrix.fromFilters(
                brightness = f.brightness,
                contrast = f.contrast,
                saturate = f.saturate,
                grayscale = f.grayscale,
                sepia = f.sepia,
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (!imageUri.isNullOrBlank()) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = colorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = t.rotate
                        scaleX = t.scale
                        scaleY = t.scale
                        translationX = if (draggable) dragOffsetX * size.width else t.offsetX * size.width
                        translationY = if (draggable) dragOffsetY * size.height else t.offsetY * size.height
                    }
                    .then(
                        if (draggable && onTransformChange != null) {
                            Modifier.pointerInput(imageUri, t.scale, t.rotate) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX += dragAmount.x / size.width
                                    dragOffsetY += dragAmount.y / size.height
                                    onTransformChange(
                                        t.copy(offsetX = dragOffsetX, offsetY = dragOffsetY),
                                    )
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        if (showCnOnImage && t.showCn) {
            Text(
                text = cn,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SageStudyCardBackText,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
        }
    }
}

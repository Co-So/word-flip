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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wordflip.core.model.media.ImageFilters
import com.wordflip.core.model.media.ImageTransform
import com.wordflip.core.ui.theme.SageStudyCardBackText

/**
 * 卡片背面图片展示（REQ-STUDY-19、P3-A07）。
 * <p>
 * 注意：ColorMatrix 滤镜与 graphicsLayer 缩放不可叠在同一节点，否则放大后易整图变黑；
 * 滤镜走软件位图，变换放在外层 Box。
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
    val context = LocalContext.current
    val t = transform ?: ImageTransform()
    // Gson 反序列化空 filters 对象时字段可能为 0，亮度 0 会整图变黑
    val f = (filters ?: ImageFilters()).normalized()
    var dragOffsetX by remember(imageUri) { mutableStateOf(t.offsetX) }
    var dragOffsetY by remember(imageUri) { mutableStateOf(t.offsetY) }
    var loadFailed by remember(imageUri) { mutableStateOf(false) }

    // 外部重置/撤销时同步拖动偏移
    LaunchedEffect(t.offsetX, t.offsetY) {
        dragOffsetX = t.offsetX
        dragOffsetY = t.offsetY
    }
    LaunchedEffect(imageUri) {
        loadFailed = false
    }

    val colorFilter = remember(f) {
        if (f.isIdentity()) {
            null
        } else {
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
    }

    // ColorMatrix 在硬件位图 + 缩放层上易黑屏；有滤镜时禁用硬件位图
    val imageModel = remember(imageUri, colorFilter != null) {
        ImageRequest.Builder(context)
            .data(imageUri)
            .allowHardware(colorFilter == null)
            .crossfade(false)
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (!imageUri.isNullOrBlank()) {
            // 变换与 ColorFilter 分节点，避免放大后 GPU 合成变黑
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = false
                        rotationZ = t.rotate
                        val s = t.scale.coerceIn(0.2f, 3f)
                        scaleX = s
                        scaleY = s
                        translationX = if (draggable) {
                            dragOffsetX * size.width
                        } else {
                            t.offsetX * size.width
                        }
                        translationY = if (draggable) {
                            dragOffsetY * size.height
                        } else {
                            t.offsetY * size.height
                        }
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
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                    onError = { loadFailed = true },
                    onSuccess = { loadFailed = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (loadFailed) {
                Text(
                    text = "图片加载失败",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
            }
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

/**
 * 纠正 Gson/空 JSON 导致的 0 值滤镜（亮度/对比度/饱和度 0 会渲成全黑）。
 */
fun ImageFilters.normalized(): ImageFilters = copy(
    brightness = if (brightness <= 0f) 100f else brightness,
    contrast = if (contrast <= 0f) 100f else contrast,
    saturate = if (saturate <= 0f) 100f else saturate,
    grayscale = grayscale.coerceIn(0f, 100f),
    sepia = sepia.coerceIn(0f, 100f),
)

private fun ImageFilters.isIdentity(): Boolean =
    brightness == 100f &&
        contrast == 100f &&
        saturate == 100f &&
        grayscale == 0f &&
        sepia == 0f

package com.wordflip.core.image

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * 相册选图 + 系统相机拍照（P3-A04）。
 * TakePicture 要求：1) 运行时 CAMERA 权限；2) output 文件必须先 createNewFile()。
 */
@Composable
fun rememberImagePickerLaunchers(
    onImagePicked: (String) -> Unit,
    onCameraDenied: (() -> Unit)? = null,
): ImagePickerLaunchers {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { onImagePicked(it.toString()) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            cameraUri?.let { onImagePicked(it.toString()) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            cameraUri?.let { cameraLauncher.launch(it) }
        } else {
            onCameraDenied?.invoke()
        }
    }

    fun prepareCaptureUri(): Uri? {
        val cacheDir = File(context.cacheDir, "snapshots").apply { mkdirs() }
        val file = File(cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        return try {
            if (!file.exists() && !file.createNewFile()) {
                null
            } else {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }
        } catch (_: IOException) {
            null
        }
    }

    return remember(galleryLauncher, cameraLauncher, permissionLauncher) {
        ImagePickerLaunchers(
            pickFromGallery = {
                galleryLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
            takePhoto = {
                val uri = prepareCaptureUri()
                if (uri == null) {
                    onCameraDenied?.invoke()
                } else {
                    cameraUri = uri
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        cameraLauncher.launch(uri)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            },
        )
    }
}

data class ImagePickerLaunchers(
    val pickFromGallery: () -> Unit,
    val takePhoto: () -> Unit,
)

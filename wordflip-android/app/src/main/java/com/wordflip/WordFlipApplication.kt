package com.wordflip

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用入口；实现 [ImageLoaderFactory] 使 Compose AsyncImage 使用带 JWT 的 Coil 加载器。
 */
@HiltAndroidApp
class WordFlipApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun newImageLoader(): ImageLoader = imageLoader
}

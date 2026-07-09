package com.wordflip.di

import android.content.Context
import coil.ImageLoader
import coil.request.CachePolicy
import com.wordflip.core.network.token.TokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Coil ImageLoader：为媒体代理 URL 附加 JWT，使卡片图可经后端代理加载。
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        tokenStore: TokenStore,
    ): ImageLoader {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = tokenStore.getAccessToken()
            val request = if (!token.isNullOrBlank()) {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                original
            }
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
        return ImageLoader.Builder(context)
            .okHttpClient(client)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}

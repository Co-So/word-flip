package com.wordflip.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.AuthApi
import com.wordflip.core.network.auth.AuthRepository
import com.wordflip.core.network.auth.TokenRefresher
import com.wordflip.core.network.interceptor.AuthInterceptor
import com.wordflip.core.network.interceptor.TokenAuthenticator
import com.wordflip.core.network.token.EncryptedTokenStore
import com.wordflip.core.network.token.TokenStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.wordflip.BuildConfig
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Retrofit / OkHttp / TokenStore Hilt 装配（A-10~A-14）；置于 app 模块避免 KSP 同模块解析 Retrofit 接口失败。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Provides
    @Singleton
    fun provideTokenStore(
        @ApplicationContext context: Context,
    ): EncryptedTokenStore = EncryptedTokenStore(context)

    @Provides
    @Singleton
    fun provideApiErrorParser(gson: Gson): ApiErrorParser = ApiErrorParser(gson)

    @Provides
    @Singleton
    @Named("noAuth")
    fun provideNoAuthOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @Named("noAuth")
    fun provideNoAuthRetrofit(
        @Named("noAuth") okHttpClient: OkHttpClient,
        gson: Gson,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    @Named("noAuth")
    fun provideNoAuthAuthApi(@Named("noAuth") retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideTokenRefresher(
        tokenStore: TokenStore,
        @Named("noAuth") noAuthAuthApi: AuthApi,
    ): TokenRefresher = TokenRefresher(tokenStore, noAuthAuthApi)

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenStore: TokenStore): AuthInterceptor =
        AuthInterceptor(tokenStore)

    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        tokenStore: TokenStore,
        tokenRefresher: TokenRefresher,
    ): TokenAuthenticator = TokenAuthenticator(tokenStore, tokenRefresher)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideAuthRepository(
        authApi: AuthApi,
        tokenStore: TokenStore,
        apiErrorParser: ApiErrorParser,
    ): AuthRepository = AuthRepository(authApi, tokenStore, apiErrorParser)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TokenStoreModule {

    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: EncryptedTokenStore): TokenStore
}

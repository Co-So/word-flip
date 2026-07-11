package com.wordflip.di

import android.content.Context
import com.google.gson.Gson
import com.wordflip.core.network.books.BooksSettingsRepository
import com.wordflip.core.network.groups.GroupsRepository
import com.wordflip.core.network.quiz.QuizRepository
import com.wordflip.core.network.media.WordMediaRepository
import com.wordflip.core.network.study.StudyRepository
import com.wordflip.core.network.today.TodayRepository
import com.wordflip.core.network.gson.WordFlipGson
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.AuthApi
import com.wordflip.core.network.api.BooksApi
import com.wordflip.core.network.api.GroupsApi
import com.wordflip.core.network.api.ImagesApi
import com.wordflip.core.network.api.QuizApi
import com.wordflip.core.network.api.SettingsApi
import com.wordflip.core.network.api.DictsApi
import com.wordflip.core.network.api.StainsApi
import com.wordflip.core.network.api.StudyApi
import com.wordflip.core.network.api.TodayApi
import com.wordflip.core.network.auth.AuthRepository
import com.wordflip.core.network.auth.TokenRefresher
import com.wordflip.core.network.interceptor.AuthInterceptor
import com.wordflip.core.network.interceptor.TokenAuthenticator
import com.wordflip.core.network.settings.PreferencesRepository
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
    fun provideGson(): Gson = WordFlipGson.create()

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

    @Provides
    @Singleton
    fun provideBooksApi(retrofit: Retrofit): BooksApi =
        retrofit.create(BooksApi::class.java)

    @Provides
    @Singleton
    fun provideSettingsApi(retrofit: Retrofit): SettingsApi =
        retrofit.create(SettingsApi::class.java)

    @Provides
    @Singleton
    fun provideDictsApi(retrofit: Retrofit): DictsApi =
        retrofit.create(DictsApi::class.java)

    @Provides
    @Singleton
    fun provideBooksSettingsRepository(
        booksApi: BooksApi,
        settingsApi: SettingsApi,
        apiErrorParser: ApiErrorParser,
    ): BooksSettingsRepository = BooksSettingsRepository(booksApi, settingsApi, apiErrorParser)

    @Provides
    @Singleton
    fun providePreferencesRepository(
        settingsApi: SettingsApi,
        apiErrorParser: ApiErrorParser,
    ): PreferencesRepository = PreferencesRepository(settingsApi, apiErrorParser)

    @Provides
    @Singleton
    fun provideGroupsApi(retrofit: Retrofit): GroupsApi =
        retrofit.create(GroupsApi::class.java)

    @Provides
    @Singleton
    fun provideGroupsRepository(
        groupsApi: GroupsApi,
        apiErrorParser: ApiErrorParser,
    ): GroupsRepository = GroupsRepository(groupsApi, apiErrorParser)

    @Provides
    @Singleton
    fun provideTodayApi(retrofit: Retrofit): TodayApi =
        retrofit.create(TodayApi::class.java)

    @Provides
    @Singleton
    fun provideStudyApi(retrofit: Retrofit): StudyApi =
        retrofit.create(StudyApi::class.java)

    @Provides
    @Singleton
    fun provideTodayRepository(
        todayApi: TodayApi,
        apiErrorParser: ApiErrorParser,
    ): TodayRepository = TodayRepository(todayApi, apiErrorParser)

    @Provides
    @Singleton
    fun provideStudyRepository(
        studyApi: StudyApi,
        apiErrorParser: ApiErrorParser,
    ): StudyRepository = StudyRepository(studyApi, apiErrorParser, BuildConfig.API_BASE_URL)

    @Provides
    @Singleton
    fun provideQuizApi(retrofit: Retrofit): QuizApi =
        retrofit.create(QuizApi::class.java)

    @Provides
    @Singleton
    fun provideQuizRepository(
        quizApi: QuizApi,
        apiErrorParser: ApiErrorParser,
    ): QuizRepository = QuizRepository(quizApi, apiErrorParser)

    @Provides
    @Singleton
    fun provideImagesApi(retrofit: Retrofit): ImagesApi =
        retrofit.create(ImagesApi::class.java)

    @Provides
    @Singleton
    fun provideStainsApi(retrofit: Retrofit): StainsApi =
        retrofit.create(StainsApi::class.java)

    @Provides
    @Singleton
    fun provideWordMediaRepository(
        imagesApi: ImagesApi,
        stainsApi: StainsApi,
        gson: Gson,
        apiErrorParser: ApiErrorParser,
    ): WordMediaRepository = WordMediaRepository(
        imagesApi,
        stainsApi,
        gson,
        apiErrorParser,
        BuildConfig.API_BASE_URL,
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TokenStoreModule {

    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: EncryptedTokenStore): TokenStore
}

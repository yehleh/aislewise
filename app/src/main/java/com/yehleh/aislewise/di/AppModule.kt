package com.yehleh.aislewise.di

import android.content.Context
import androidx.room.Room
import com.yehleh.aislewise.BuildConfig
import com.yehleh.aislewise.data.db.AppDatabase
import com.yehleh.aislewise.data.db.Migrations
import com.yehleh.aislewise.data.db.SeedCallback
import com.yehleh.aislewise.feature.scan.GeminiConfig
import com.yehleh.aislewise.feature.scan.GeminiListImageParser
import com.yehleh.aislewise.feature.scan.ListImageParser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addCallback(SeedCallback)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // A healthy model reads a list in a few seconds; past this, try the next model instead of waiting.
        .callTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    fun provideGeminiConfig(): GeminiConfig =
        GeminiConfig(
            apiKey = BuildConfig.GEMINI_API_KEY,
            models = BuildConfig.GEMINI_MODELS.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindListImageParser(impl: GeminiListImageParser): ListImageParser
}

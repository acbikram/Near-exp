package com.nearexpiry.manager.di

import android.content.Context
import androidx.room.Room
import com.nearexpiry.manager.data.local.database.ExpiryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideExpiryDatabase(@ApplicationContext context: Context): ExpiryDatabase {
        return ExpiryDatabase.getInstance(context)
    }
}

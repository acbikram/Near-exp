package com.nearexpiry.manager.di

import com.nearexpiry.manager.data.repository.ExpiryRepositoryImpl
import com.nearexpiry.manager.data.repository.ProductCatalogRepositoryImpl
import com.nearexpiry.manager.data.repository.ProjectRepositoryImpl
import com.nearexpiry.manager.domain.repository.ExpiryRepository
import com.nearexpiry.manager.domain.repository.ProductCatalogRepository
import com.nearexpiry.manager.domain.repository.ProjectRepository
import com.nearexpiry.manager.utils.AndroidPriceTagDeviceIdProvider
import com.nearexpiry.manager.utils.EncryptedPriceTagPairingStore
import com.nearexpiry.manager.utils.PriceTagDeviceIdProvider
import com.nearexpiry.manager.utils.PriceTagPairingStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindExpiryRepository(impl: ExpiryRepositoryImpl): ExpiryRepository

    @Binds
    @Singleton
    abstract fun bindProductCatalogRepository(impl: ProductCatalogRepositoryImpl): ProductCatalogRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindPriceTagPairingStore(impl: EncryptedPriceTagPairingStore): PriceTagPairingStore

    @Binds
    @Singleton
    abstract fun bindPriceTagDeviceIdProvider(impl: AndroidPriceTagDeviceIdProvider): PriceTagDeviceIdProvider
}

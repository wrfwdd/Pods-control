package com.airpods.control.core.bluetooth

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {

    @Provides
    @Singleton
    fun provideAirPodsDeviceManager(): AirPodsDeviceManager = AirPodsDeviceManager()
}

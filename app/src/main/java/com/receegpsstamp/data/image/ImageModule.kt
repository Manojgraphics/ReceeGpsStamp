package com.receegpsstamp.data.image

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the [ImageCompressor] interface to its default implementation for Hilt injection. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ImageModule {
    @Binds
    abstract fun bindImageCompressor(impl: DefaultImageCompressor): ImageCompressor
}

package com.example.nonton_aja.data

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

object CoilConfig {
    @Volatile
    private var instance: ImageLoader? = null

    fun imageLoader(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .memoryCache {
                    MemoryCache.Builder(context.applicationContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.applicationContext.cacheDir.resolve("image_cache"))
                        .maxSizePercent(0.10)
                        .maxSizeBytes(150L * 1024 * 1024)
                        .build()
                }
                .allowHardware(true)
                .build()
                .also { instance = it }
        }
    }
}

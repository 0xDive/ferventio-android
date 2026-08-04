package io.ferventio.app.data

import android.content.Context
import coil3.ImageLoader
import coil3.bitmapFactoryMaxParallelism
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.memoryCacheMaxSizePercentWhileInBackground
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toOkioPath

object FerventioImageLoader {
    private const val DISK_CACHE_BYTES = 192L * 1_048_576L

    fun create(context: Context): ImageLoader {
        val decodeDispatcher = Dispatchers.IO.limitedParallelism(2)
        val fetchDispatcher = Dispatchers.IO.limitedParallelism(4)

        return ImageLoader.Builder(context.applicationContext)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context.applicationContext, 0.12)
                    .build()
            }
            .memoryCacheMaxSizePercentWhileInBackground(0.35)
            .diskCache {
                DiskCache.Builder()
                    .directory(
                        context.applicationContext.cacheDir
                            .resolve("ferventio-images")
                            .toOkioPath(),
                    )
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .decoderCoroutineContext(decodeDispatcher)
            .fetcherCoroutineContext(fetchDispatcher)
            .bitmapFactoryMaxParallelism(2)
            .crossfade(false)
            .build()
    }
}

package io.ferventio.app.data

import android.content.Context
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageCacheManager(context: Context) {
    private val applicationContext = context.applicationContext

    suspend fun clear(): ImageCacheClearResult = withContext(Dispatchers.IO) {
        val imageLoader = SingletonImageLoader.get(applicationContext)
        val memoryBytes = imageLoader.memoryCache?.size ?: 0L
        val diskBytes = imageLoader.diskCache?.size ?: 0L
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
        ImageCacheClearResult(
            memoryBytes = memoryBytes,
            diskBytes = diskBytes,
        )
    }
}

data class ImageCacheClearResult(
    val memoryBytes: Long,
    val diskBytes: Long,
) {
    val totalBytes: Long get() = memoryBytes + diskBytes
}

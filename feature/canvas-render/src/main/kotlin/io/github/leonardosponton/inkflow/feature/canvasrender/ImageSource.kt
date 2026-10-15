package io.github.leonardosponton.inkflow.feature.canvasrender

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import io.github.leonardosponton.inkflow.core.model.AssetId
import java.io.File
import java.util.concurrent.Executors

/** Supplies decoded images for [ImageElement]s; `null` while loading. */
fun interface ImageSource {
    fun bitmap(asset: AssetId, maxPx: Int): Bitmap?

    companion object {
        val None = ImageSource { _, _ -> null }
    }
}

/**
 * Decodes asset files off the main thread with power-of-two subsampling for the requested size,
 * keeping a memory-bounded cache. [onLoaded] runs on the main thread (invalidate the view).
 */
class AssetImageCache(private val resolve: (AssetId) -> File?, private val onLoaded: () -> Unit) : ImageSource {
    private data class Key(val asset: AssetId, val sample: Int)

    private val cache = object : LruCache<Key, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: Key, value: Bitmap) = value.allocationByteCount
    }
    private val loading = HashSet<Key>()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun bitmap(asset: AssetId, maxPx: Int): Bitmap? {
        val file = resolve(asset) ?: return null
        val sample = sampleFor(file, maxPx)
        val key = Key(asset, sample)
        cache.get(key)?.let { return it }
        if (loading.add(key)) {
            executor.execute {
                val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                main.post {
                    loading.remove(key)
                    if (bmp != null) {
                        cache.put(key, bmp)
                        onLoaded()
                    }
                }
            }
        }
        // While a sharper version loads, fall back to any cached resolution.
        return (1..MAX_SAMPLE).asSequence().map { cache.get(Key(asset, it)) }.firstOrNull { it != null }
    }

    private fun sampleFor(file: File, maxPx: Int): Int {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val largest = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
        var sample = 1
        while (largest / (sample * 2) >= maxPx.coerceAtLeast(MIN_PX) && sample < MAX_SAMPLE) sample *= 2
        return sample
    }

    private companion object {
        const val MAX_BYTES = 96 * 1024 * 1024
        const val MAX_SAMPLE = 32
        const val MIN_PX = 64
    }
}

/** Blocking decoder for off-screen rendering (exports, thumbnails). */
class SyncImageSource(private val resolve: (AssetId) -> File?) : ImageSource {
    private val cache = HashMap<AssetId, Bitmap?>()

    override fun bitmap(asset: AssetId, maxPx: Int): Bitmap? = cache.getOrPut(asset) {
        resolve(asset)?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }
}

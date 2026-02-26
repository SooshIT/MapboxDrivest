package com.drivest.navigation.trafficsigns

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

class TrafficSignsBitmapLoader(private val context: Context) {

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    suspend fun load(assetPath: String, targetWidthPx: Int, targetHeightPx: Int): Bitmap? {
        cache.get(cacheKey(assetPath, targetWidthPx, targetHeightPx))?.let { return it }
        return withContext(Dispatchers.IO) {
            val key = cacheKey(assetPath, targetWidthPx, targetHeightPx)
            cache.get(key)?.let { return@withContext it }
            val resolvedAssetPath = if (assetPath.startsWith("traffic_signs/")) {
                assetPath
            } else {
                "traffic_signs/$assetPath"
            }
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(resolvedAssetPath).use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                val sampleSize = calculateInSampleSize(bounds, targetWidthPx, targetHeightPx)
                val decodeOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = sampleSize
                }
                val bitmap = context.assets.open(resolvedAssetPath).use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
                if (bitmap != null) {
                    cache.put(key, bitmap)
                }
                bitmap
            } catch (error: Exception) {
                Log.w(TAG, "Failed to load traffic sign asset: $resolvedAssetPath", error)
                null
            }
        }
    }

    private fun cacheKey(assetPath: String, width: Int, height: Int): String {
        return "$assetPath@$width x $height"
    }

    private fun cacheSizeKb(): Int {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return max(4 * 1024, maxMemoryKb / 16)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height <= 0 || width <= 0 || reqWidth <= 0 || reqHeight <= 0) {
            return inSampleSize
        }
        while ((height / inSampleSize) > reqHeight * 2 || (width / inSampleSize) > reqWidth * 2) {
            inSampleSize *= 2
        }
        return max(1, inSampleSize)
    }

    private companion object {
        const val TAG = "TrafficSignsBitmapLoader"
    }
}

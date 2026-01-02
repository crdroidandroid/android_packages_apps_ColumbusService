/*
 * SPDX-FileCopyrightText: The Android Open Source Project
 * SPDX-FileCopyrightText: TheParasiteProject
 * SPDX-License-Identifier: GPL-3.0
 */

package org.protonaosp.columbus.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import kotlin.math.roundToInt
import org.protonaosp.columbus.dlog

/** Cache app icon for management. */
class AppIconCacheManager private constructor() {

    private val drawableCache: LruCache<String, Drawable>

    init {
        drawableCache =
            object : LruCache<String, Drawable>(MAX_CACHE_SIZE_IN_KB) {
                override fun sizeOf(key: String, drawable: Drawable): Int {
                    if (drawable is BitmapDrawable) {
                        return drawable.getBitmap().getByteCount() / 1024
                    }
                    // Rough estimate each pixel will use 4 bytes by default.
                    return drawable.getIntrinsicHeight() * drawable.getIntrinsicWidth() * 4 / 1024
                }
            }
    }

    /**
     * Put app icon to cache
     *
     * @param packageName of icon
     * @param uid of packageName
     * @param drawable app icon
     */
    fun put(packageName: String?, uid: Int, drawable: Drawable?) {
        val key: String? = getKey(packageName, uid)
        if (
            key == null ||
                drawable == null ||
                drawable.getIntrinsicHeight() < 0 ||
                drawable.getIntrinsicWidth() < 0
        ) {
            dlog(TAG, "Invalid key or drawable.")
            return
        }
        drawableCache.put(key, drawable.flattenToBitmap())
    }

    /**
     * Get app icon from cache.
     *
     * @param packageName of icon
     * @param uid of packageName
     * @return app icon
     */
    fun get(packageName: String?, uid: Int): Drawable? {
        return getKey(packageName, uid)?.let { key -> drawableCache.get(key)?.mutate() }
            ?: run {
                dlog(TAG, "Invalid key with package or uid.")
                null
            }
    }

    /** Release cache. */
    fun release() {
        sAppIconCacheManager?.drawableCache?.evictAll()
    }

    private fun getKey(packageName: String?, uid: Int): String? {
        if (packageName == null || uid < 0) {
            return null
        }
        return packageName + DELIMITER + UserHandle.getUserId(uid)
    }

    /**
     * Clears as much memory as possible.
     *
     * @see android.content.ComponentCallbacks2#onTrimMemory(int)
     */
    fun trimMemory(level: Int) {
        val manager = sAppIconCacheManager ?: return
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            // Time to clear everything
            manager.drawableCache.trimToSize(0)
        } else if (
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            // Tough time but still affordable, clear half of the cache
            val maxSize: Int = manager.drawableCache.maxSize() ?: return
            manager.drawableCache.trimToSize(maxSize / 2)
        }
    }

    private fun Drawable.flattenToBitmap(): BitmapDrawable {
        if (this is BitmapDrawable) {
            return this
        }

        val width = if (this.intrinsicWidth > 0) this.intrinsicWidth else 1
        val height = if (this.intrinsicHeight > 0) this.intrinsicHeight else 1

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        this.setBounds(0, 0, canvas.width, canvas.height)
        this.draw(canvas)

        return BitmapDrawable(null, bitmap)
    }

    companion object {
        private const val TAG: String = "AppIconCacheManager"

        private const val CACHE_RATIO: Float = 0.1f
        @VisibleForTesting protected val MAX_CACHE_SIZE_IN_KB: Int = getMaxCacheInKb()
        private const val DELIMITER: String = ":"

        @Volatile private var sAppIconCacheManager: AppIconCacheManager? = null

        /** Get an {@link AppIconCacheManager} instance. */
        fun getInstance() =
            sAppIconCacheManager
                ?: synchronized(this) {
                    sAppIconCacheManager ?: AppIconCacheManager().also { sAppIconCacheManager = it }
                }

        private fun getMaxCacheInKb(): Int {
            return (CACHE_RATIO * Runtime.getRuntime().maxMemory() / 1024).roundToInt()
        }
    }
}

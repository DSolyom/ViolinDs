/*
	Copyright 2016 Dániel Sólyom

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package ds.violin.v1.util

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.util.LruCache
import ds.violin.v1.Global
import ds.violin.v1.util.cache.ImageFileCache
import ds.violin.v1.util.common.Bitmaps
import ds.violin.v1.util.common.Debug
import ds.violin.v1.datasource.base.LazyLoader
import ds.violin.v1.datasource.base.LazyLoaderTask
import java.io.FileNotFoundException
import java.net.URL

private val TAG = "ImageLoader"

/**
 * a [LazyLoader] singleton for bitmaps (images)
 *
 * @use call load with an [ImageDescriptor] to start loading
 *      posts completion even if image found in cache
 */
object ImageLoader : LazyLoader<ImageDescriptor, Bitmap>() {

    init {
        needSleepingBetweenRetries = true
    }

    override fun load(descriptor: ImageDescriptor, completion: (ImageDescriptor, Bitmap?, Throwable?) -> Unit): Boolean {
        try {

            // cache first
            var bmp = LruBitmapCache.get(descriptor.prefix + descriptor.url);
            if (bmp != null) {
                Debug.logD(TAG, "loaded from cache: " + descriptor.prefix + " " + descriptor.url);

                /** post [completion] */
                Handler().post {
                    completion(descriptor, bmp, null)
                }
                return false
            }

            if (!descriptor.alwaysInBackground) {

                // file cache if it is safe/fast to load it on the current thread
                bmp = FileCache.get(descriptor)
                if (bmp != null) {

                    /** post [completion] */
                    Handler().post {
                        completion(descriptor, bmp, null)
                    }
                    return false
                }
            }

            /** real [LazyLoader]ing */
            super.load(descriptor, completion)
            return true
        } catch(error: Throwable) {
            completion(descriptor, null, error)
            return false
        }
    }

    override fun createLoaderFor(descriptor: ImageDescriptor, lazyLoader: LazyLoader<ImageDescriptor, Bitmap>):
            LazyLoaderTask<ImageDescriptor, Bitmap> {
        return LazyImageDownloaderTask(descriptor, lazyLoader)
    }

    fun download(descriptor: ImageDescriptor): Bitmap? {
        if (!descriptor.url.substring(0, 4).equals("http")) {
            descriptor.url = "http://" + descriptor.url;
        }

        Debug.logD(TAG, "downloading: " + descriptor.prefix + " " + descriptor.url);

        var bmp: Bitmap?
        try {
            if (descriptor.maxX == -1) {
                bmp = Bitmaps.downloadImage(URL(descriptor.url));
            } else {
                bmp = Bitmaps.getResizedImageFromHttpStream(URL(descriptor.url), descriptor.maxX, descriptor.maxY);
            }
        } catch(e: FileNotFoundException) {

            // no retries
            throw e
        } catch(e: Throwable) {
            Debug.logException(e)
            bmp = null
        }
        if (bmp == null) {

            // not found
            return null;
        }

        // put in bitmap cache
        LruBitmapCache.put(descriptor.prefix + descriptor.url, bmp);

        // put in file cache (in another thread - so we can give this bitmap right away
        Thread()
        {
            FileCache.put(descriptor.prefix + descriptor.url, Bitmaps.compressBitmapToByteArray(bmp!!));
        }.start();

        return bmp;

    }
}

private class LazyImageDownloaderTask(imageDescriptor: ImageDescriptor, lazyLoader: LazyLoader<ImageDescriptor, Bitmap>) :
        LazyLoaderTask<ImageDescriptor, Bitmap> {

    override val retryLimit: Int = 5
    override val key: ImageDescriptor = imageDescriptor
    override val lazyLoader: LazyLoader<ImageDescriptor, Bitmap> = lazyLoader
    override var thread: Thread? = null
    override var retries: Int = 0
    override lateinit var handler: Handler
    override var interrupted = false

    override fun load(descriptor: ImageDescriptor): Bitmap? {
        if (descriptor.alwaysInBackground) {
            val bmp = FileCache.get(descriptor)
            if (bmp != null) {
                return bmp
            }
        }

        return ImageLoader.download(descriptor)
    }

}

/**
 * data class for describing downloadable image
 *
 * @param url the image's origin
 * @param maxXDp the approximate maximum width in dp (will be doubled for large screens) of the loaded image
 *               will scale if the original image was bigger
 *               if left unset or set to 0, will use screen's width as the maximum
 *               if set to -1 the original image will be loaded (be wary of it's size)
 * @param maxYDp the approximate maximum height in dp (will be doubled for large screens) of the loaded image
 *               will scale if the original image was bigger
 *               if left unset or set to 0, will use screen's height as the maximum
 */
data class ImageDescriptor(var url: String, val maxXDp: Int = 0, val maxYDp: Int = 0) {

    var alwaysInBackground = true
    val maxX: Int
    val maxY: Int

    init {
        maxX = when (maxXDp) {
            -1 -> -1
            0 -> Global.screenWidth
            else -> (maxXDp * Global.dipImageMultiplier).toInt()
        }
        maxY = when (maxYDp) {
            -1 -> -1
            0 -> Global.screenHeight
            else -> (maxYDp * Global.dipImageMultiplier).toInt()
        }
    }

    val prefix: String
        get() {
            return "" + maxX + "X" + maxY
        }

    override fun equals(other: Any?): Boolean {
        return other != null && other is ImageDescriptor &&
                url == other.url && maxX == other.maxX && maxY == other.maxY
    }

    override fun hashCode(): Int {
        return (prefix + url).hashCode()
    }
}

private object LruBitmapCache : LruCache<String, Bitmap>({
    val am = Global.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    1024 * am.largeMemoryClass / 7
}()) {

    override fun sizeOf(key: String, bitmap: Bitmap): Int {
        return bitmap.byteCount / 1024;
    }
}

private object FileCache : ImageFileCache(Global.context, "" + Global.context.packageName + "/images/", -1) {

    fun get(descriptor: ImageDescriptor): Bitmap? {
        var bmpBytes = FileCache[descriptor.prefix + descriptor.url]
        if (bmpBytes == null || bmpBytes.size == 0) {

            bmpBytes = FileCache[descriptor.url, descriptor.maxX, descriptor.maxY];

            if (bmpBytes == null || bmpBytes.size == 0) {
                return null;
            }
            put(descriptor.prefix + descriptor.url, bmpBytes);
        }

        val bmp: Bitmap?
        try {
            bmp = Bitmaps.createBitmap(bmpBytes);
            LruBitmapCache.put(descriptor.prefix + descriptor.url, bmp);
        } catch(e: Throwable) {
            Debug.logException(e)
            bmp = null
        }

        Debug.logD(TAG, "loaded from file: " + descriptor.prefix + " " + descriptor.url);
        return bmp;
    }
}
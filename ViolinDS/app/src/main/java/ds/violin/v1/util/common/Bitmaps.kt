/*
	Copyright 2013 Dániel Sólyom

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
package ds.violin.v1.util.common

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

import android.app.Activity
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.provider.MediaStore

object Bitmaps {

    /**
     *
     * @param url
     * @return
     * @throws IOException
     * @throws OutOfMemoryError
     */
    @Throws(IOException::class, OutOfMemoryError::class)
    fun downloadImage(url: URL): Bitmap {
        var stream: InputStream? = null
        try {
            stream = getFlushedHttpStream(url)
            return BitmapFactory.decodeStream(stream)
        } catch (e: IOException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw e
        } finally {
            if (stream != null) {
                stream.close()
            }
        }
    }

    /**
     *
     * @param url
     * @return
     * @throws IOException
     * @throws OutOfMemoryError
     */
    @Throws(IOException::class, OutOfMemoryError::class)
    fun downloadImageAsByteArray(url: URL): ByteArray {
        var stream: InputStream? = null
        try {
            stream = getFlushedHttpStream(url)
            return Files.getFileAsByteArray(stream)
        } catch (e: IOException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw e
        } finally {
            if (stream != null) {
                stream.close()
            }
        }
    }

    /**
     *
     * @param bytes
     * @return
     */
    fun createBitmap(bytes: ByteArray): Bitmap {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * create a thumbnail (png, 100 quality) from byte array
     * this first creates a bitmap from the byte array, then scales that bitmap
     * !please note: this does not checks if the input byteArray is 'small' enough to create the bitmap
     *
     * @param byteArray
     * @param width
     * @param height
     * @throws OutOfMemoryError
     * @return
     */
    @Throws(OutOfMemoryError::class)
    fun createThumbnail(byteArray: ByteArray, width: Int, height: Int): Bitmap {
        val tmp = createBitmap(byteArray)
        val result = Bitmap.createScaledBitmap(tmp, width, height, false)
        tmp.recycle()
        return result
    }

    /**
     * create a thumbnail's byte array
     * this first creates a bitmap from the byte array, then scales that bitmap and converts that to byte array
     * !please note: this does not checks if the input byteArray is 'small' enough to create the bitmap(s)
     *
     * @param byteArray
     * @param maxWidth
     * @param maxHeight
     * @throws OutOfMemoryError
     * @return
     */
    @Throws(OutOfMemoryError::class)
    @JvmOverloads fun createThumbnailByteArray(byteArray: ByteArray, maxWidth: Int, maxHeight: Int,
                                               format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, quality: Int = 100): ByteArray {
        var thumbnailByteArray = byteArray
        val point = findNewBitmapSize(thumbnailByteArray, maxWidth, maxHeight) ?: return thumbnailByteArray

        val tmp = createThumbnail(thumbnailByteArray, point.x, point.y)
        thumbnailByteArray = compressBitmapToByteArray(tmp, format, quality)
        tmp.recycle()
        return thumbnailByteArray
    }

    /**
     * gives back another bitmap resized so size is inside maxWidth and maxHeight
     * !note: does not recycle the input bitmap and may return the original bitmap (if no scaling was necessary)
     *
     * @param bmp
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    fun resizeBitmap(bmp: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val point = findNewBitmapSize(bmp.width, bmp.height, maxWidth, maxHeight)
        return Bitmap.createScaledBitmap(bmp, point.x, point.y, true)
    }

    /**
     * gives back another bitmap resized so size is inside maxWidth and maxHeight
     * !note: may return the original bitmap (if no scaling was necessary)
     *
     * @param bmp
     * @param maxWidth
     * @param maxHeight
     * @param recycle
     * @return
     */
    fun resizeBitmap(bmp: Bitmap, maxWidth: Int, maxHeight: Int, recycle: Boolean): Bitmap {
        val bw = bmp.width
        val bh = bmp.height
        val point = findNewBitmapSize(bw, bh, maxWidth, maxHeight)
        if (bw == point.x && bh == point.y) {
            return bmp
        }
        val ret = Bitmap.createScaledBitmap(bmp, point.x, point.y, true)
        if (recycle) {
            bmp.recycle()
        }
        return ret
    }

    /**
     *
     * @param byteArray
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    fun findNewBitmapSize(byteArray: ByteArray, maxWidth: Int, maxHeight: Int): Point? {

        //Decode image size from byte array
        val o = BitmapFactory.Options()
        o.inJustDecodeBounds = true

        BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, o)

        return findNewBitmapSize(o.outWidth, o.outHeight, maxWidth, maxHeight)
    }

    /**
     *
     * @param oWidth
     * @param oHeight
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    fun findNewBitmapSize(oWidth: Int, oHeight: Int, maxWidth: Int, maxHeight: Int): Point {

        // use image size ratio to find out real width and height
        if (oWidth > maxWidth || oHeight > maxHeight) {
            var nw = maxWidth
            var nh = maxHeight
            val rw = maxWidth.toDouble() / oWidth
            val rh = maxHeight.toDouble() / oHeight
            if (rw > rh) {
                nw = (oWidth * rh).toInt()
            } else {
                nh = (oHeight * rw).toInt()
            }
            return Point(nw, nh)
        } else {
            return if (maxWidth > maxHeight) Point(maxHeight, maxHeight) else Point(maxWidth, maxWidth)
        }
    }

    /**
     *
     * @param byteArray
     * @param approxWidth
     * @param approxHeight
     * @return
     */
    fun createThumbnailApprox(byteArray: ByteArray, approxWidth: Int, approxHeight: Int): Bitmap? {
        try {
            //Decode image size
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true

            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, o)

            var bmp: Bitmap?

            var scale = 1
            if (o.outHeight > approxHeight || o.outWidth > approxWidth) {
                scale = Math.max(Math.pow(2.0, Math.ceil(Math.log(approxHeight.toDouble() / o.outHeight.toDouble()) / Math.log(0.5)).toInt().toDouble()).toInt(),
                        Math.pow(2.0, Math.ceil(Math.log(approxWidth.toDouble() / o.outWidth.toDouble()) / Math.log(0.5)).toInt().toDouble()).toInt())
            }

            //Decode with inSampleSize
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = scale
            bmp = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, o2)

            return bmp
        } catch (e: Throwable) {
            e.printStackTrace()
            return null
        }

    }

    /**
     * @param bmp
     * @return
     */
    fun compressBitmapToByteArray(bmp: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    /**
     *
     * @param bmp
     * @param format
     * @param quality
     * @return
     */
    fun compressBitmapToByteArray(bmp: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bmp.compress(format, quality, stream)
        return stream.toByteArray()
    }

    /**
     *
     * @param context
     * @param uri
     * @param approxWidth
     * @param approxHeight
     * @return
     */
    fun getThumbnailInternal(context: Context, uri: Uri, approxWidth: Int, approxHeight: Int): Bitmap? {
        try {
            var `is`: InputStream = context.contentResolver.openInputStream(uri)

            //Decode image size
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true

            BitmapFactory.decodeStream(`is`, null, o)
            `is`.close()

            var bmp: Bitmap?

            var scale = 1
            if (o.outHeight > approxHeight || o.outWidth > approxWidth) {
                scale = Math.max(Math.pow(2.0, Math.ceil(Math.log(approxHeight.toDouble() / o.outHeight.toDouble()) / Math.log(0.5)).toInt().toDouble()).toInt(),
                        Math.pow(2.0, Math.ceil(Math.log(approxWidth.toDouble() / o.outWidth.toDouble()) / Math.log(0.5)).toInt().toDouble()).toInt())
            }

            //Decode with inSampleSize
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = scale
            `is` = context.contentResolver.openInputStream(uri)
            bmp = BitmapFactory.decodeStream(`is`, null, o2)
            `is`.close()

            return bmp
        } catch (e: Throwable) {
            e.printStackTrace()
            return null
        }

    }

    /**
     *
     * @param imageFileName
     * @param approxWidth
     * @param approxHeight
     * @return
     */
    fun getThumbnail(imageFileName: String, approxWidth: Int, approxHeight: Int): Bitmap? {
        val f = File(imageFileName)
        if (!f.exists()) {
            return null
        }

        try {
            //Decode image size
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true

            var fis = FileInputStream(f)
            BitmapFactory.decodeStream(fis, null, o)
            fis.close()

            var bmp: Bitmap?

            var scale = 1
            if (o.outHeight > approxHeight || o.outWidth > approxWidth) {
                scale = Math.max(Math.pow(2.0, Math.floor(Math.log(approxHeight.toDouble() / o.outHeight.toDouble()) / Math.log(0.5)).toInt().toDouble()).toInt(),
                        Math.pow(2.0, Math.floor(Math.log(approxWidth.toDouble() / o.outWidth.toDouble()) / Math.log(0.5)).toInt().toDouble()).toInt())
            }

            //Decode with inSampleSize
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = scale
            fis = FileInputStream(f)
            bmp = BitmapFactory.decodeStream(fis, null, o2)
            fis.close()

            return bmp
        } catch (e: Throwable) {
            throw e
        }
    }

    /**
     *
     * @param url
     * @param approxWidth
     * @param approxHeight
     * @return
     */
    @Throws(IOException::class)
    fun getResizedImageFromHttpStream(url: URL, approxWidth: Int, approxHeight: Int): Bitmap {
        var stream: InputStream? = null
        try {
            //Decode image size
            val o = BitmapFactory.Options()

            o.inJustDecodeBounds = true
            stream = getFlushedHttpStream(url)
            BitmapFactory.decodeStream(stream, null, o)
            Debug.logD("Bitmaps", "original size: " + o.outWidth + "x" + o.outHeight + " ")
            var bmp: Bitmap?

            var scale = 1
            if (o.outHeight > approxHeight || o.outWidth > approxWidth) {
                scale = Math.max(Math.pow(2.0, Math.ceil(Math.log(approxHeight.toDouble() / o.outHeight.toDouble()) / Math.log(0.5)).toInt().toDouble()).toInt(),
                        Math.pow(2.0, Math.ceil(Math.log(approxWidth.toDouble() / o.outWidth.toDouble()) / Math.log(0.5)).toInt().toDouble()).toInt())
            }

            //Decode with inSampleSize
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = scale
            stream = getFlushedHttpStream(url)
            bmp = BitmapFactory.decodeStream(stream, null, o2)
            Debug.logD("Bitmaps", "new size: " + bmp!!.width + "x" + bmp.height + " ")
            return bmp
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
        } finally {
            if (stream != null) {
                stream.close()
            }
        }
    }

    /**
     *
     * @param activity
     * @param uri
     * @return
     */
    fun getImageFileNameFromUri(activity: Activity, uri: Uri): String? {
        val ret: String?
        try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = activity.contentResolver.query(uri, proj, null, null, null)
            val index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor.moveToFirst()

            ret = cursor.getString(index)
            cursor.close()
        } catch (e: Exception) {
            ret = null
        }

        return ret
    }

    /**
     * @param url
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    fun getFlushedHttpStream(url: URL): FlushedInputStream {
        val conn = url.openConnection() as HttpURLConnection
        conn.doInput = true
        conn.useCaches = false
        conn.connect()

        val stream = conn.inputStream
        try {
            stream.reset()
        } catch (e: Throwable) {
        }

        return FlushedInputStream(stream)
    }

    // manipulations

    fun fastBlur(sentBitmap: Bitmap, radius: Int, fromX: Int, fromY: Int,
                 width: Int, height: Int, brightnessMod: Float, scale: Float): Bitmap? {
        var usedradius = radius

        // Stack Blur v1.0 from
        // http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html
        //
        // Java Author: Mario Klingemann <mario at quasimondo.com>
        // http://incubator.quasimondo.com
        // created Feburary 29, 2004
        // Android port : Yahel Bouaziz <yahel at kayenko.com>
        // http://www.kayenko.com
        // ported april 5th, 2012

        // This is a compromise between Gaussian Blur and Box blur
        // It creates much better looking blurs than Box Blur, but is
        // 7x faster than my Gaussian Blur implementation.
        //
        // I called it Stack Blur because this describes best how this
        // filter works internally: it creates a kind of moving stack
        // of colors whilst scanning through the image. Thereby it
        // just has to add one new block of color to the right side
        // of the stack and remove the leftmost color. The remaining
        // colors on the topmost layer of the stack are either added on
        // or reduced by one, depending on if they are on the right or
        // on the left side of the stack.
        //
        // If you are using this algorithm in your code please add
        // the following line:
        //
        // Stack Blur Algorithm by Mario Klingemann <mario@quasimondo.com>

        // added brightness modification by DS (january 7th, 2013)

        // implemented for kotlin by DS (2016)

        val bitmap: Bitmap

        if (scale == 1.0f) {
            bitmap = sentBitmap.copy(sentBitmap.config, true)
        } else {
            bitmap = Bitmap.createScaledBitmap(sentBitmap,
                    (sentBitmap.width * scale).toInt(), (sentBitmap.height * scale).toInt(), false)
        }

        if (usedradius < 1) {
            return null
        }

        val w = (width * scale).toInt()
        val h = (height * scale).toInt()

        val pix = IntArray(w * h)

        bitmap.getPixels(pix, 0, w, fromX, fromY, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = usedradius + usedradius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(Math.max(w, h))

        var divsum = div + 1 shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = i / divsum
            i++
        }

        yi = 0
        yw = yi

        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = usedradius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        val originRadius = usedradius
        y = 0
        while (y < h) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            i = -usedradius
            while (i <= usedradius) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))]
                sir = stack[i + usedradius]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = usedradius

            x = 0
            while (x < w) {

                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - usedradius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (y == 0) {
                    vmin[x] = Math.min(x + usedradius + 1, wm)
                }
                p = pix[yw + vmin[x]]

                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
                x++
            }
            yw += w
            y++
        }

        usedradius = originRadius

        x = 0
        while (x < w) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            yp = -usedradius * w
            i = -usedradius
            while (i <= usedradius) {
                yi = Math.max(0, yp) + x

                sir = stack[i + usedradius]

                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]

                rbs = r1 - Math.abs(i)

                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs

                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }

                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = usedradius
            y = 0
            while (y < h) {
                pix[yi] = 0xff000000.toInt() or ((dv[rsum] * brightnessMod).toInt() shl 16) or ((dv[gsum] * brightnessMod).toInt() shl 8) or (dv[bsum] * brightnessMod).toInt()

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - usedradius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w
                }
                p = x + vmin[y]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
                y++
            }
            x++
        }

        bitmap.setPixels(pix, 0, w, fromX, fromY, w, h)

        return bitmap
    }

    // for jpegs
    class FlushedInputStream(inputStream: InputStream) : FilterInputStream(inputStream) {

        @Throws(IOException::class)
        override fun skip(n: Long): Long {
            var totalBytesSkipped = 0L
            while (totalBytesSkipped < n) {
                var bytesSkipped = `in`.skip(n - totalBytesSkipped)
                if (bytesSkipped == 0L) {
                    val byteSkipped = read()
                    if (byteSkipped < 0) {
                        break  // we reached EOF
                    } else {
                        bytesSkipped = 1 // we read one byte
                    }
                }
                totalBytesSkipped += bytesSkipped
            }
            return totalBytesSkipped
        }
    }
}


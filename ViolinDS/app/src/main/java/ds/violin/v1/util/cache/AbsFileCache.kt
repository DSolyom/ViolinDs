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

package ds.violin.v1.util.cache

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.Arrays
import java.util.HashMap

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import ds.violin.v1.util.common.Debug
import ds.violin.v1.util.common.deleteDirRec

private object absFileCacheLock { }

abstract class AbsFileCache<T>(context: Context, dir: String, maxSize: Long = -1L) : Caching<String, T> {

    private var CT: Thread? = null
    private var DT: Thread? = null

    var cacheDir: String? = null
        internal set
    internal var size: Long = 0

    var maxSize: Long = maxSize
        set(newValue) {
            if (field != -1L && size > maxSize) {
                deleteAboveMax(File(cacheDir))
            }
        }

    init {
        when (context.externalCacheDir) {
            null -> {
            }
            else -> {
                cacheDir = context.externalCacheDir.toString() + "/" + dir
                val fd = File(cacheDir)
                fd.mkdirs()
                if (fd.exists()) {
                    size = 0L
                    countCurrentSizeInThread(fd)
                } else {
                    cacheDir = null
                }
            }
        }
    }

    /**
     *
     */
    override fun has(filename: String): Boolean {
        if (maxSize == 0L || cacheDir == null || Environment.MEDIA_MOUNTED != Environment.getExternalStorageState()) {
            return false
        }

        synchronized (getLock(filename)) {
            val exists = File(cacheDir, getRealFilename(filename)).exists()
            removeLock(filename)
            return exists
        }
    }

    /**
     * get file content from file cache
     *
     * @param filename
     * @return
     */
    override operator fun get(filename: String): T? {
        synchronized (getLock(filename)) {
            if (maxSize == 0L || cacheDir == null || Environment.MEDIA_MOUNTED != Environment.getExternalStorageState()) {
                return null
            }

            var inStream: FileInputStream? = null
            var result: T?
            try {
                inStream = FileInputStream(File(cacheDir, getRealFilename(filename)))
                result = getObjectFromStream(inStream)
            } catch (e: Throwable) {
                return null
            } finally {
                if (inStream != null) {
                    try {
                        inStream.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
            }

            if (result == null) {

                // file exists but could not get from filesystem - remove that file
                File(cacheDir, filename).delete()
            }

            removeLock(filename)

            return result
        }
    }

    /**
     * set content into file cache
     */
    override fun put(filename: String, value: T): Boolean {
        synchronized (getLock(filename)) {
            val file = getOutFile(filename)
            if (file != null && !file.exists()) {
                // only return value if file already exists - need to delete this to overwrite
                try {
                    val outStream = FileOutputStream(file)

                    putObjectIntoStream(value, outStream)

                    outStream.flush()
                    outStream.close()

                    synchronized (absFileCacheLock) {
                        size += file.length()
                        if (size > currentMaxSize) {

                            Debug.logW("file cache max size exceeded", "" + size)

                            if (DT != null) {

                                // already doing this
                                return true
                            }
                            DT = object : Thread() {

                                override fun run() {
                                    deleteAboveMax(File(cacheDir))
                                    DT = null
                                }
                            }
                            DT!!.start()
                        }
                    }
                } catch (e: Throwable) {
                    removeLock(filename)
                    throw (e)
                }

            }
        }

        removeLock(filename)

        return true
    }

    private fun getLock(filename: String): Any {
        synchronized (absFileCacheLock) {
            var lock: Any? = mFileCacheLocks[filename]
            if (lock != null) {
                mFileCacheLockCounts.put(filename, mFileCacheLockCounts[filename]!! + 1)
                return lock
            }
            lock = Any()
            mFileCacheLockCounts.put(filename, 1)
            return lock
        }
    }

    private fun removeLock(filename: String) {
        synchronized (absFileCacheLock) {
            if (mFileCacheLocks.remove(filename) == null) {
                return
            }
            val count = mFileCacheLockCounts[filename]!!
            if (count == 1) {
                mFileCacheLockCounts.remove(filename)
            } else {
                mFileCacheLockCounts.put(filename, count - 1)
            }
        }
    }

    /**

     * @param filename
     * *
     * @return
     */
    fun getUri(filename: String): Uri? {
        if (maxSize == 0L || cacheDir == null || Environment.MEDIA_MOUNTED != Environment.getExternalStorageState()) {
            return null
        }
        return Uri.fromFile(File(cacheDir!! + getRealFilename(filename)))
    }

    /**

     * @return
     */
    private val currentMaxSize: Long
        get() {
            var maxSize = maxSize
            try {
                val stat = StatFs(Environment.getExternalStorageDirectory().path)
                val sdAvailSize: Long

                if (Build.VERSION.SDK_INT < 18) {
                    sdAvailSize = stat.availableBlocks.toLong() * stat.blockSize.toLong()
                } else {
                    sdAvailSize = stat.availableBlocksLong * stat.blockSizeLong
                }
                if (maxSize == -1L || maxSize > sdAvailSize / 10) {
                    maxSize = sdAvailSize / 10
                }
                return maxSize
            } catch (e: Throwable) {
                return 1024L * 1024L
            }

        }

    /**

     * @param filename
     * *
     * @return
     */
    fun getOutFile(filename: String): File? {
        var outfilename = filename
        if (maxSize == 0L || cacheDir == null || Environment.MEDIA_MOUNTED != Environment.getExternalStorageState()) {
            return null
        }

        outfilename = getRealFilename(outfilename)
        File(cacheDir, outfilename.substring(0, outfilename.lastIndexOf("/"))).mkdirs()
        return File(cacheDir, outfilename)
    }

    /**
     * get object from given stream

     * @param inStream
     * *
     * @return
     */
    protected abstract fun getObjectFromStream(inStream: FileInputStream): T

    /**
     * set object to given stream

     * @param value
     * *
     * @param outStream
     */
    protected abstract fun putObjectIntoStream(value: T, outStream: FileOutputStream)

    /**
     * count size on 'disc' in thread

     * @param dir
     */
    private fun countCurrentSizeInThread(dir: File) {
        if (CT != null) {

            // already doing this
            return
        }
        CT = object : Thread() {

            override fun run() {
                synchronized (this) {
                    val size = countAllSizeRec(dir)
                    this@AbsFileCache.size = size

                    Debug.logW("file cache current size", "" + size)

                    if (maxSize != -1L && size > maxSize) {
                        Debug.logW("file cache max size exceeded", "" + size)
                        deleteAboveMax(dir)
                    }
                    CT = null
                }
            }
        }
        CT!!.start()
    }

    /**
     * size on 'disc'

     * @return
     */
    private fun countAllSizeRec(dir: File): Long {
        var size: Long = 0L

        val list = dir.list() ?: return 0

        for (file in list) {
            val fFile = File(dir, file)
            if (fFile.isDirectory) {
                size += countAllSizeRec(fFile)
            } else {
                size += fFile.length()
            }
        }
        return size
    }

    override fun remove(filename: String) {
        File(getRealFilename(filename)).delete()
    }

    /**
     * !note: not thread safe - just deletes the cache folder
     */
    override fun clear() {
        if (cacheDir == null) {
            return
        }
        deleteDirRec(cacheDir!!)
    }

    /**
     * delete oldest files till size is below or equal to max size
     */
    @Synchronized private fun deleteAboveMax(dir: File) {
        val l = dir.list()
        val maxSize = currentMaxSize * 3 / 4

        val list = arrayOfNulls<FileModifiedInfo>(l.size)
        val s = l.size
        for (i in 0..s - 1) {
            list[i] = FileModifiedInfo()
            list[i]!!.file = File(dir, l[i])
            list[i]!!.modified = list[i]!!.file!!.lastModified()
        }

        Arrays.sort<FileModifiedInfo>(list) { f1, f2 ->
            java.lang.Long.valueOf(f1.modified)!!.compareTo(
                    f2.modified)
        }
        for (info in list) {
            val fFile = info!!.file
            if (fFile!!.isDirectory) {
                deleteAboveMax(fFile)
                if (fFile.list().size == 0) {
                    fFile.delete()
                }
            } else {
                size -= fFile.length()
                fFile.delete()
            }
            if (size <= maxSize) {
                break
            }
        }
        Debug.logW("file cache above max deleted - new size", "" + size)
    }

    fun getRealFilename(filename: String): String {
        return filename.replace("\\\\", "/").replace(":", "___")
    }

    private class FileModifiedInfo {
        internal var modified: Long = 0
        internal var file: File? = null
    }

    companion object {

        private val mFileCacheLocks = HashMap<String, Any>()
        private val mFileCacheLockCounts = HashMap<String, Int>()
    }
}

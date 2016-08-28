/*
	Copyright 2011 Dániel Sólyom

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

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

object Files {

    @Throws(IOException::class, OutOfMemoryError::class)
    fun getFileAsByteArray(url: URL): ByteArray {
        var iStream: InputStream? = null
        try {
            iStream = getHttpStream(url)
            return getFileAsByteArray(iStream)
        } catch (e: IOException) {
            throw e
        } catch (e: OutOfMemoryError) {
            throw e
        } finally {
            if (iStream != null) {
                iStream.close()
            }
        }
    }

    /**
     *
     * @param iStream
     * @return
     * @throws IOException
     * @throws OutOfMemoryError
     */
    @Throws(IOException::class, OutOfMemoryError::class)
    fun getFileAsByteArray(iStream: InputStream): ByteArray {
        try {
            iStream.reset()
        } catch (e: Throwable) {
        }

        val byteBuffer = ByteArrayOutputStream()

        val bufferSize = 8 * 1024
        val buffer = ByteArray(bufferSize)

        while (true) {
            var len = iStream.read(buffer)
            if (len == -1) {
                break
            }
            byteBuffer.write(buffer, 0, len)
        }

        return byteBuffer.toByteArray()
    }

    /**

     * @param url
     * *
     * @return
     * *
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun getHttpStream(url: URL): InputStream {
        val conn = url.openConnection() as HttpURLConnection

        conn.doInput = true
        conn.useCaches = true
        conn.connect()

        return conn.inputStream
    }

    @Throws(IOException::class)
    fun toByteArray(file: String): ByteArray {
        return toByteArray(File(file))
    }

    @Throws(IOException::class)
    fun toByteArray(file: File): ByteArray {
        // Open file
        val f = RandomAccessFile(file, "r")

        try {
            // Get and check length
            val longlength = f.length()
            val length = longlength.toInt()
            if (length.toLong() != longlength) throw IOException("File size >= 2 GB")

            // Read file and return data
            val data = ByteArray(length)
            f.readFully(data)
            return data
        } finally {
            f.close()
        }
    }

    @Throws(IOException::class)
    fun toBase64(file: String): String {
        return toBase64(File(file))
    }

    @Throws(IOException::class)
    fun toBase64(file: File): String {
        return String(Base64.encode(toByteArray(file), 0)).trim()
    }

    /**

     * @param stream
     * *
     * @param outFile
     * *
     * @param closeAtEnd
     * *
     * @throws Throwable
     */
    @Throws(Throwable::class)
    fun putInputSteramIntoFile(stream: InputStream, outFile: File, closeAtEnd: Boolean) {
        try {
            val output = FileOutputStream(outFile)
            try {
                try {
                    val buffer = ByteArray(1024)
                    var read: Int

                    while (true) {
                        read = stream.read(buffer)
                        if (read == -1) {
                            break
                        }
                        output.write(buffer, 0, read)
                    }

                    output.flush()
                } finally {
                    output.close()
                }
            } catch (e: Exception) {
                Debug.logException(e)
                throw e
            }

        } finally {
            if (closeAtEnd) {
                stream.close()
            }
        }
    }

    val isExternalStorageWritable: Boolean
        get() {
            val state = Environment.getExternalStorageState()
            return Environment.MEDIA_MOUNTED == state
        }

    fun getPathFromUri(context: Context, uri: Uri): String? {
        val isKitKatOrAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

        // DocumentProvider
        if (isKitKatOrAbove && DocumentsContract.isDocumentUri(context, uri)) {

            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                val type = split[0]

                if ("primary".equals(type, ignoreCase = true)) {
                    return "" + Environment.getExternalStorageDirectory() + "/" + split[1]
                }

            } else if (isDownloadsDocument(uri)) {

                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id))

                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                val type = split[0]

                var contentUri: Uri? = null
                if ("image" == type) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == type) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == type) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])

                return getDataColumn(context, contentUri!!, selection, selectionArgs)
            }// MediaProvider
            // DownloadsProvider
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }// File
        // MediaStore (and general)

        return null
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.

     * @param context The context.
     * *
     * @param uri The Uri to query.
     * *
     * @param selection (Optional) Filter used in the query.
     * *
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * *
     * @return The value of the _data column, which is typically a file path.
     */
    fun getDataColumn(context: Context, uri: Uri, selection: String?,
                      selectionArgs: Array<String>?): String? {

        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null)
            if (cursor != null && cursor!!.moveToFirst()) {
                val column_index = cursor!!.getColumnIndexOrThrow(column)
                return cursor!!.getString(column_index)
            }
        } finally {
            if (cursor != null)
                cursor!!.close()
        }
        return null
    }


    /**
     * @param uri The Uri to check.
     * *
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.getAuthority()
    }

    /**
     * @param uri The Uri to check.
     * *
     * @return Whether the Uri authority is DownloadsProvider.
     */
    fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.getAuthority()
    }

    /**
     * @param uri The Uri to check.
     * *
     * @return Whether the Uri authority is MediaProvider.
     */
    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.getAuthority()
    }
}

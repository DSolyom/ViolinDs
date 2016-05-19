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

package ds.violin.v1.util.common

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException

import android.os.Environment
import android.util.Log
import ds.violin.v1.Global

object Debug {

    /**
     *
     */
    fun logNativeHeapAllocatedSize() {
        logE("Debug", "NativeHeapSize: " + android.os.Debug.getNativeHeapAllocatedSize())
    }

    /**
     * print stack trace of an exception in debug mode
     *
     * @param e
     */
    fun logException(e: Throwable?) {
        if (e != null && Global.isDebug) {
            e.printStackTrace()
        }
    }

    /**
     * log to file
     *
     * @param filename
     * @param text
     */
    fun logToFile(filename: String, text: String) {
        val dir = File("" + Environment.getExternalStorageDirectory() + "/log")
        dir.mkdirs()
        val logFile = File(dir, filename)
        if (!logFile.exists()) {
            try {
                logFile.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
        try {
            val buf = BufferedWriter(FileWriter(logFile, true))
            buf.append(text)
            buf.newLine()
            buf.close()
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }

    }

    /**
     * log error in infp mode
     *
     * @param title
     * @param error
     */
    fun logI(title: String, error: String) {
        if (Global.isDebug) {
            Log.i(title, error)
        }
    }

    /**
     * log error in debug mode
     *
     * @param title
     * @param error
     */
    fun logE(title: String, error: String) {
        if (Global.isDebug) {
            Log.e(title, error)
        }
    }

    /**
     * log warning in debug mode
     *
     * @param title
     * @param warning
     */
    fun logW(title: String, warning: String) {
        if (Global.isDebug) {
            Log.w(title, warning)
        }
    }

    /**
     * log debug in debug mode
     *
     * @param title
     * @param warning
     */
    fun logD(title: String, warning: String) {
        if (Global.isDebug) {
            Log.d(title, warning)
        }
    }

    /**
     * log error
     *
     * @param title
     * @param error
     */
    fun logEA(title: String, error: String) {
        Log.e(title, error)
    }

}

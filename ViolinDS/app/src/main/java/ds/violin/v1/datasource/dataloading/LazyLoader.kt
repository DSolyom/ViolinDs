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

package ds.violin.v1.datasource.dataloading

import android.os.Handler
import ds.violin.v1.util.common.Debug
import ds.violin.v1.datasource.dataloading.Interruptable
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private object loaderQueueCompletionLock {}

private val TAG = "LazyLoader"

private class RetryLimitExceededError(message: String? = null) : Throwable(message) {}

/**
 * A laizy loader class using a ThreadPoolExecutor to start loading in the background
 *
 * @use load(key, completion block)
 *
 * @param KEY type of the key to identify who's the result and for what
 * @param RESULT type of the result
 */
abstract class LazyLoader<KEY, RESULT> {

    val tpe: ThreadPoolExecutor
    val tpeQueue = LinkedBlockingQueue<Runnable>()
    val loadingQueue = HashMap <KEY, ArrayList<(KEY, RESULT?, Throwable?) -> Unit>>()
    var needSleepingBetweenRetries = false

    init {
        val availableProcs = Runtime.getRuntime().availableProcessors()
        tpe = ThreadPoolExecutor(
                Math.max(1, availableProcs / 2),
                Math.max(1, availableProcs),
                1,
                TimeUnit.SECONDS,
                tpeQueue
        )
    }

    /**
     * load [RESULT] for [KEY]
     *
     * @param key what to load and with what to identify
     * @param completion to be called when finished or in case of error
     * @return true if new thread will be created false if [completion] is added for an already queued [key]
     */
    open fun load(key: KEY, completion: (KEY, RESULT?, Throwable?) -> Unit): Boolean {
        synchronized(loaderQueueCompletionLock) {
            val completionList = loadingQueue[key]
            when {
                completionList == null -> {
                    // new key
                    loadingQueue.put(key, arrayListOf(completion))

                    // put in queue for loading
                    val loader = createLoaderFor(key, this)
                    loader.handler = Handler()
                    tpe.execute(loader)
                    return true
                }
                completionList.contains(completion) -> {
                    return false
                }
                else -> {
                    completionList.add(completion)
                    return false
                }
            }
        }
    }

    fun onSuccessFor(key: KEY, result: RESULT) {
        Debug.logD(TAG, "load succeeded for " + key)

        val completionList = loadingQueue[key]
        if (completionList != null) {
            for (completion in completionList) {
                completion(key, result, null)
            }
        }
        loadingQueue.remove(key)
    }

    fun onFailFor(key: KEY, error: Throwable) {
        Debug.logD(TAG, "load failed for " + key)

        val completionList = loadingQueue[key]
        if (completionList != null) {
            for (completion in completionList) {
                completion(key, null, error)
            }
        }
        loadingQueue.remove(key)
    }

    fun stopLoading(key: KEY) {
        synchronized(loaderQueueCompletionLock) {
            for (runnable in tpeQueue) {
                if (key == (runnable as LazyLoaderTask<*, *>).key) {
                    runnable.interrupt()
                    break
                }
            }

            Debug.logD(TAG, "stopped loading for " + key)

            loadingQueue.remove(key)
        }
    }

    fun stopLoading(key: KEY, completion: (KEY, RESULT?, Throwable?) -> Unit) {
        synchronized(loaderQueueCompletionLock) {
            val completionList = loadingQueue[key]
            if (completionList != null) {
                completionList.remove(completion)

                Debug.logD(TAG, "removed one completion for " + key)

                if (completionList.isEmpty()) {
                    stopLoading(key)
                }
            }
        }
    }

    abstract fun createLoaderFor(key: KEY, lazyLoader: LazyLoader<KEY, RESULT>): LazyLoaderTask<KEY, RESULT>
}

/**
 * the task to load the results with
 */
interface LazyLoaderTask<KEY, RESULT> : Runnable, Interruptable {

    val retryLimit: Int

    val key: KEY
    val lazyLoader: LazyLoader<KEY, RESULT>

    var thread: Thread?
    var retries: Int
    var handler: Handler

    override fun run() {
        thread = Thread.currentThread()

        if (interrupted || thread!!.isInterrupted) {
            return
        }

        if (lazyLoader.needSleepingBetweenRetries) {
            try {

                // sleep a bit - more for every retry
                // always sleep mainly for images, because if not
                // there is some kind of os bug preventing some images to show
                // also we use sleep to skip unwanted elements while scrolling a list
                Thread.sleep(retries * 275L + 75L);
            } catch (e: InterruptedException) {
                return
            }
        }

        if (interrupted || thread!!.isInterrupted) {
            return
        }

        try {
            val result = load(key)
            if (result != null) {
                handler.post {
                    if (!interrupted && !thread!!.isInterrupted) {
                        lazyLoader.onSuccessFor(key, result)
                    }
                }
                return
            }
        } catch(error: Throwable) {

            // real problem - no retries
            handler.post {
                if (!interrupted && !thread!!.isInterrupted) {
                    lazyLoader.onFailFor(key, error)
                }
            }
            return
        }

        // failed
        retries += 1
        if (retries == retryLimit) {
            handler.post {
                if (!interrupted && !thread!!.isInterrupted) {
                    val message = "max number of retries exceeded (" + key.toString() + ")"
                    Debug.logE(TAG, message);
                    lazyLoader.onFailFor(key, RetryLimitExceededError(message))
                }
            }
            return
        }

        // retry
        if (!interrupted && !thread!!.isInterrupted) {
            run()
        }
    }

    override fun interrupt() {
        thread?.interrupt()
        super.interrupt()
    }

    /**
     * override for the actual loading
     */
    fun load(key: KEY): RESULT?
}

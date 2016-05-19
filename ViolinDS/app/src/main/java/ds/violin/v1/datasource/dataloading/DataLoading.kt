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
import android.util.Log
import java.util.*

interface Interruptable {

    var interrupted: Boolean

    /**
     * interrupt something
     *
     * check for {@var interrupted} before using any result and don't call completion if there is any
     * even better to stop the task in case it's asynchronous
     *
     * !note: if can't (in case of asynchronous task) it is probably a good idea to throw away this
     * instance at this point and create a new one
     *
     * !note: call from the thread where you started to do what you do or prepare for synchronizing hell
     */
    fun interrupt() {
        interrupted = true
    }
}

/**
 * can load data
 */
interface DataLoading : Interruptable {
    var loadingParams: MutableMap<String, Any>
    var loading: Boolean

    fun load(completion: (result: Any?, error: Throwable?) -> Unit)
}

/**
 * #Private
 */
class SingleUseBackgroundWorkerThread(dataLoader: BackgroundDataLoading, loadId: String?) :
        Thread(), Interruptable {

    /** this is here, because [BackgroundDataLoading] is an interface, which can't contain companions */
    companion object {

        /** saved threads by [BackgroundDataLoading.loadId] */
        @JvmStatic internal val savedThreads: HashMap<String, SingleUseBackgroundWorkerThread> = HashMap()

        @JvmStatic fun cleanup() {
            savedThreads.clear()
        }
    }

    val loadId: String? = loadId
    internal var dataLoader: BackgroundDataLoading? = dataLoader
    override var interrupted: Boolean = false
    internal var handler: Handler? = null
    internal var result: Any? = null
    internal var error: Throwable? = null
    internal val dataLoaderLock = Any()

    override fun start() {
        handler = Handler()

        super.start()
    }

    override fun run() {
        if (interrupted) {
            return
        }

        dataLoader?.load({ result, error ->

            if (loadId != null) {

                // when loadId != null:
                // thread too saves the result/error as those must survive through dataLoader's death
                synchronized(this) {
                    this.result = result
                    this.error = error
                }
            }

            synchronized(dataLoaderLock) {
                if (interrupted || dataLoader == null) {
                    return@load
                }

                dataLoader!!.result = result
                dataLoader!!.error = error
            }

            handler?.post {
                if (!interrupted && dataLoader != null) {

                    // 'send' result/error
                    dataLoader!!.onDataLoaded()
                }
            }
        })
    }

    override fun interrupt() {
        synchronized(dataLoaderLock) {
            dataLoader = null
        }
        handler = null
        super<Interruptable>.interrupt()
        super<Thread>.interrupt()
    }

    internal fun delayForNextLoader(dataLoader: DataLoading) {
        synchronized(dataLoaderLock) {
            if (this.dataLoader != dataLoader) {

                // when delayForNextOwner called after an owner / loader change it shouldn't do anything
                return
            }
            this.dataLoader = null
        }
    }
}

/**
 * interface [DataLoading] for background work
 *
 * set [loadId] (only) when the result needs to survive this object's death
 * (ie. when saving something to server which can only be saved ones, like registering a new user)
 *
 * !note: [BackgroundDataLoading] must be able to handle simultaneous loading and [interrupt]ion,
 *        as it can still be loading after [interrupt] was called while already loading a new data too
 *        (and by loading I mean doing any work on the background)
 *
 * @property loadId
 */
interface BackgroundDataLoading : DataLoading {

    /**
     * unique or null - loadId is the identifier for the data -
     * !note: always null unless the result needs to survive this object's death
     *
     * !!note: if unique, it must be unique through the application
     */
    val loadId: String?

    /** = null, #Private */
    var worker: SingleUseBackgroundWorkerThread?
    /** = null, #Private */
    var result: Any?
    /** = null, #Private */
    var error: Throwable?
    /** = null, #Private */
    var owner: ((result: Any?, error: Throwable?) -> Unit)?

    fun loadInBackground(completion: (result: Any?, error: Throwable?) -> Unit): Boolean {
        if (loading) {
            return false
        }

        loading = true
        interrupted = false

        owner = completion

        if (loadId != null) {
            worker = SingleUseBackgroundWorkerThread.savedThreads[loadId!!]
            if (worker != null) {
                synchronized(worker!!) {

                    // worst case scenario is that this will set the same result and error
                    // as the worker at the same time
                    result = worker!!.result
                    error = worker!!.error
                }
            }
        }

        if (worker != null) {
            synchronized(worker!!.dataLoaderLock) {
                if (result != null || error != null) {
                    Handler().post {
                        if (owner == completion) {

                            // still loading the same data for the same owner, good
                            onDataLoaded()
                        }
                    }
                } else {

                    // worker should continue with this loader
                    worker!!.dataLoader = this
                }
            }
            return true
        }

        // new worker
        worker = SingleUseBackgroundWorkerThread(this, loadId)
        if (loadId != null) {
            SingleUseBackgroundWorkerThread.savedThreads[loadId!!] = worker!!
        }
        worker!!.start()

        return true
    }

    fun onDataLoaded() {
        if (owner != null) {
            if (loadId != null) {
                SingleUseBackgroundWorkerThread.savedThreads.remove(loadId!!)
            }
            val result = result
            val error = error
            this.result = null
            this.error = null
            worker = null
            loading = false
            val owner = owner
            this.owner = null
            owner!!(result, error)
        }
    }

    override fun interrupt() {
        if (loadId != null) {
            SingleUseBackgroundWorkerThread.savedThreads.remove(loadId!!)
        }
        loading = false
        worker?.interrupt()
        worker = null
        owner = null
        result = null
        error = null
        super.interrupt()
    }

    fun delayForThis() {
        owner = null
        loading = false
    }

    /** #Internal - delays worker's completion until a new (or the old) owner is set for it */
    fun delayForNextLoader() {
        worker?.delayForNextLoader(this)
        loading = false
        owner = null
    }
}


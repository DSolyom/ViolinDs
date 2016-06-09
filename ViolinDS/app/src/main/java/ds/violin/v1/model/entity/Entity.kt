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

package ds.violin.v1.model.entity

import android.os.Parcelable
import ds.violin.v1.app.violin.LoadingViolin
import ds.violin.v1.datasource.dataloading.BackgroundDataLoading
import ds.violin.v1.datasource.dataloading.DataLoading
import ds.violin.v1.datasource.dataloading.Interruptable
import ds.violin.v1.model.modeling.*
import java.io.Serializable

/**
 * interface for entities who can load themselves
 *
 * !note: use [BackgroundDataLoading] here as [dataLoader] if you need background work
 *        @see [BackgroundDataLoading] and @see [LoadingViolin] for more information
 */
interface SelfLoadable : Interruptable {

    /** lateinit - loader of the data */
    var dataLoader: DataLoading
    /** = false - is data valid, or needs loading? */
    var valid: Boolean

    /**
     * load
     *
     * @param completion
     * @return true if started loading, false if it was not needed, either [SelfLoadable] is [valid] or already loading
     */
    fun load(completion: (entity: SelfLoadable, error: Throwable?) -> Unit): Boolean {
        if (valid == true) {
            return false
        }
        else if (dataLoader is BackgroundDataLoading) {
            return (dataLoader as BackgroundDataLoading).loadInBackground { result, error ->
                onDataLoaded(result, error, completion)
            }
        }
        else {
            dataLoader.load { result, error ->
                onDataLoaded(result, error, completion)
            }
            return true
        }
    }


    /**
     * #Protected - what happens when data is loaded
     */
    fun onDataLoaded(result: Any?, error: Throwable?,
                     completion: (entity: SelfLoadable, error: Throwable?) -> Unit)

    /**
     * invalidates data - telling an controller that it's data is dirty and in need of reloading
     */
    fun invalidate() {
        valid = false
        dataLoader.interrupt()
    }

    override fun interrupt() {
        dataLoader.interrupt()
        super.interrupt()
    }
}

/**
 * if an entity implements [HasSerializableData] and it is valid it's data will be saved in [LoadingViolin]s
 * unless [dataToSerializable] returns null
 */
interface HasSerializableData {

    fun dataToSerializable(): Serializable?
    fun createDataFrom(serializedData: Serializable)
}

/**
 * if an entity implements [HasParcelableData] and it is valid it's data will be saved in [LoadingViolin]s
 * unless [dataToParcelable] returns null
 */
interface HasParcelableData {

    fun dataToParcelable(): Parcelable?
    fun createDataFrom(parcelableData: Parcelable)
}

interface SelfLoadableModeling<MODEL> : Modeling<MODEL>, SelfLoadable {

    override var dataLoader: DataLoading

    override fun onDataLoaded(result: Any?, error: Throwable?,
                              completion: (entity: SelfLoadable, error: Throwable?) -> Unit) {
        valid = error == null

        if (error != null) {
            completion(this, error)
            return
        }

        try {
            when (result) {
                is Modeling<*> -> {
                    values = result.values as MODEL
                }
                else -> values = result as MODEL
            }
        } catch(e: Throwable) {
            valid = false
            completion(this, e)
            return
        }

        completion(this, null)
    }

    override fun invalidate() {
        dataLoader.interrupt()
        super.invalidate()
    }

    override fun interrupt() {
        dataLoader.interrupt()
        super.interrupt()
    }
}

interface SelfLoadableListModeling<LIST, MODEL_TYPE> : ListModeling<LIST, MODEL_TYPE>, SelfLoadable {

    override var dataLoader: DataLoading

    override fun onDataLoaded(result: Any?, error: Throwable?,
                              completion: (entity: SelfLoadable, error: Throwable?) -> Unit) {
        valid = error == null

        if (error != null) {
            completion(this, error)
            return
        }

        try {
            when (result) {
                is ListModeling<*, *> -> enslave(result as ListModeling<LIST, MODEL_TYPE>)
                else -> models = result as LIST
            }
        } catch(e: Throwable) {
            valid = false
            completion(this, e)
            return
        }

        completion(this, null)
    }

    override fun invalidate() {
        dataLoader.interrupt()
        super.invalidate()
    }
}


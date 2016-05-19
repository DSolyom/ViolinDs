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

import ds.violin.v1.datasource.dataloading.DataLoading
import ds.violin.v1.model.modeling.*

interface ContinuousListDataLoading : DataLoading {

    fun setupLoadFor(list: ContinuousListing<*, *>, offset: Int, pageSize: Int) {
        loadingParams["offset"] = offset
        loadingParams["page_size"] = pageSize
    }

}

interface ContinuousListing<LIST, MODEL_TYPE> : SelfLoadableModelListing<LIST, MODEL_TYPE> {

    /** should be at least 3 times higher than elements can be on screen at once - no checks! */
    var pageSize: Int
    var offset: Int
    /** should be at least 4 times of [pageSize] - no checks! */
    var maxSizeInMemory: Int?
    /** =null - presumed size of the list - the max item count if set to anything than null */
    var presumedSize: Int?

    /** =true, #Private */
    var firstLoad: Boolean
    /** =true, #Private */
    var loadingForward: Boolean

    /** =0, #ProtectedGet, #PrivateSet - size change of last load (@see [loadNext] [loadPrevious] */
    var sizeChange: Int
    /** =0, #ProtectedGet, #PrivateSet - offset change of last load (@see [loadNext] [loadPrevious] */
    var offsetChange: Int

    open fun initLoader() {
        if (dataLoader !is ContinuousListDataLoading) {
            throw IllegalArgumentException("dataLoader must implement ContinuousListDataLoading")
        }

        (dataLoader as ContinuousListDataLoading).setupLoadFor(
                this, offset, pageSize
        )
    }

    open fun get(position: Int, completion: (entity: SelfLoadable, error: Throwable?) -> Unit): Modeling<MODEL_TYPE> {
        when {
            position + 1 > size - pageSize / 3 -> loadNext(completion)
            position - 1 < pageSize / 3 -> loadPrevious(completion)
        }
        return get(position)
    }

    override fun invalidate() {
        firstLoad = true
        super.invalidate()
    }

    override fun load(completion: (entity: SelfLoadable, error: Throwable?) -> Unit): Boolean {
        if (firstLoad) {
            initLoader()
            firstLoad = false
        }
        return super.load(completion)
    }

    fun loadNext(completion: (entity: SelfLoadable, error: Throwable?) -> Unit): Boolean {
        if (!valid || !hasNext() || dataLoader.loading) {

            // list is not valid or already at the end
            return false
        }

        loadingForward = true

        (dataLoader as ContinuousListDataLoading).setupLoadFor(
                this, offset + size, pageSize
        )

        /** only invalid [SelfLoadable] can be loaded */
        valid = false
        val loading = load(completion)
        valid = true
        return loading
    }

    fun hasNext(): Boolean {
        return (presumedSize == null || offset + size < presumedSize!!)
    }

    fun loadPrevious(completion: (entity: SelfLoadable, error: Throwable?) -> Unit): Boolean {
        if (!valid || offset == 0 || dataLoader.loading) {

            // list is not valid or already at start
            return false
        }

        loadingForward = false

        val nextOffset = when {
            offset > pageSize -> offset - pageSize
            else -> 0
        }
        val nextPageSize = when {
            nextOffset > 0 -> pageSize
            else -> offset
        }

        (dataLoader as ContinuousListDataLoading).setupLoadFor(
                this, nextOffset, nextPageSize
        )

        /** only invalid [SelfLoadable] can be loaded */
        valid = false
        val loading = load(completion)
        valid = true
        return loading
    }
}

interface ContinuousMutableListing<L, T> : ContinuousListing<L, T> {

    override fun onDataLoaded(result: Any?, error: Throwable?, 
                              completion: (entity: SelfLoadable, error: Throwable?) -> Unit) {

        if (!valid && error == null) {

            /** list got invalidated - need to clear before adding the results */
            clear()
        }

        if (size == 0 || error != null) {

            // first load or at least doesn't matter
            return super.onDataLoaded(result, error, completion)
        }

        // got only new parts here
        var newParts: L
        try {
            newParts = when (result) {
                is ModelListing<*, *> -> result.models as L
                else -> result as L
            }
        } catch(e: Throwable) {
            valid = false
            completion(this, e)
            return
        }

        val oldSize = size

        when (loadingForward) {
            true -> {

                // add new modelings
                addAll(newParts)

                if (size - oldSize < pageSize) {
                    // newParts's size = size - oldSize

                    // loaded last ones
                    presumedSize = offset + size
                }

                // need to remove from top?
                offsetChange = when {
                    maxSizeInMemory == null -> 0
                    size > maxSizeInMemory!! -> pageSize * 2
                    else -> 0
                }

                if (offsetChange > 0) {

                    // remove {@var offsetChange} elements from the top of the list
                    remove(offsetChange)

                    // and modify offset
                    offset += offsetChange
                }

            }
            false -> {

                // add new modelings
                addAll(0, newParts)

                val newPartsSize = size - oldSize

                offsetChange = -newPartsSize
                offset += offsetChange

                // remove from bottom of the list if needed
                val toRemove = when {
                    maxSizeInMemory != null -> (size - maxSizeInMemory!!) * 2
                    else -> 0

                }

                if (toRemove > 0) {
                    remove(size - toRemove, toRemove)
                }
            }
        }

        sizeChange = size - oldSize

        completion(this, error)
    }
}

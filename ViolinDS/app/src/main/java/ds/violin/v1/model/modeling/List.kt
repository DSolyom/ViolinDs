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

package ds.violin.v1.model.modeling

import android.database.Cursor
import ds.violin.v1.model.entity.HasSerializableData
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable

interface ModelListing<LIST, MODEL> {

    /** #ProtectedSet - the models - !note: the actual reference may change (@see [JSONArrayModelListing.addAll]) */
    var models: LIST
    val size: Int

    fun get(index: Int): Modeling<MODEL>

    fun add(index: Int, value: MODEL)
    fun add(index: Int, value: Modeling<MODEL>)

    fun addAll(index: Int = size, otherModels: LIST)
    fun addAll(otherModels: LIST) {
        addAll(size, otherModels)
    }

    fun remove(index: Int = 0, count: Int)
    fun remove(count: Int) {
        remove(0, count)
    }

    /** your models are now my models too */
    fun enslave(other: ModelListing<LIST, MODEL>) {
        models = other.models
    }

    /** remove all models */
    fun clear()
}

interface MutableListModelListing<MODEL> : ModelListing<MutableList<MODEL>, MODEL> {

    override val size: Int
        get() {
            return models.size ?: 0
        }

    override fun add(index: Int, value: MODEL) {
        models.add(index, value)
    }

    override fun add(index: Int, value: Modeling<MODEL>) {
        models.add(index, value.values!!)
    }

    override fun remove(index: Int, count: Int) {
        val till = when {
            size > index + count -> count
            else -> count + index - size
        }

        for (i in 0..till - 1) {
            models.removeAt(index)
        }
    }

    override fun addAll(index: Int, otherModels: MutableList<MODEL>) {
        otherModels.addAll(index, otherModels)
    }

    override fun clear() {
        models.clear()
    }

}

interface JSONArrayModelListing : ModelListing<JSONArray, JSONObject>, HasSerializableData {

    override val size: Int
        get() {
            return models.length() ?: 0
        }

    override fun get(index: Int): JSONModel {
        return JSONModel(models.getJSONObject(index))
    }

    override fun add(index: Int, value: JSONObject) {
        addAll(index, JSONArray().put(value))
    }

    override fun add(index: Int, value: Modeling<JSONObject>) {
        addAll(index, JSONArray().put(value.values))
    }

    override fun remove(index: Int, count: Int) {
        val till = when {
            size > index + count -> count
            else -> size - index
        }

        for (i in 0..till - 1) {
            models.remove(index)
        }
    }

    override fun addAll(index: Int, otherModels: JSONArray) {

        // screw JSONArray for not having option to put at start of list or merge not to say
        // put other JSONArray in the middle
        if (index == models.length()) {
            for (i in 0..otherModels.length() - 1) {
                models.put(otherModels.get(i))
            }
        } else if (index == 0) {
            val oldModels = models
            models = JSONArray()
            for (i in 0..otherModels.length() - 1) {
                models.put(otherModels.get(i))
            }
            for (i in 0..oldModels.length() - 1) {
                models.put(oldModels.get(i))
            }
        } else {
            val oldModels = models
            models = JSONArray()
            for (i in 0..index - 1) {
                models.put(oldModels.get(i))
            }
            for (i in 0..otherModels.length() - 1) {
                models.put(otherModels.get(i))
            }
            for (i in index..oldModels.length() - 1) {
                models.put(oldModels.get(i))
            }
        }
    }

    override fun clear() {
        models = JSONArray()
    }

    override fun dataToSerializable(): Serializable {
        return models.toString()
    }

    override fun createDataFrom(serializedData: Serializable) {
        models = JSONArray(serializedData as String)
    }
}

interface CursorModelListing : ModelListing<CursorModel, Cursor> {

    override val size: Int
        get() {
            return models.values?.count ?: 0
        }

    /**
     *  !note: returned value is unsafe - only hold in local scope - __cursorindex may change
     *
     *  @return
     */
    override fun get(index: Int): CursorModel {
        models._cursorPosition = index
        return models
    }

    override fun add(index: Int, value: Cursor) {
        throw UnsupportedOperationException()
    }

    override fun add(index: Int, value: Modeling<Cursor>) {
        throw UnsupportedOperationException()
    }

    override fun addAll(index: Int, otherModels: CursorModel) {
        throw UnsupportedOperationException()
    }

    override fun remove(index: Int, count: Int) {
        throw UnsupportedOperationException()
    }

    override fun clear() {
        throw UnsupportedOperationException()
    }
}

/**
 * [JSONArrayModelListing] used in [JSONArrayEntity]
 */
open class JSONArrayModelList(models: JSONArray = JSONArray()) : JSONArrayModelListing {

    override var models: JSONArray = models
}
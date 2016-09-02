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
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.JSONValue
import java.io.Serializable

interface ListModeling<LIST, VALUE> {

    /** #ProtectedSet - the values - !note: the actual reference may change (@see [JSONArrayListModeling.addAll]) */
    var values: LIST
    val size: Int

    operator fun get(index: Int): VALUE?

    fun add(value: VALUE)
    fun add(index: Int, value: VALUE)
    fun add(index: Int, value: Modeling<VALUE, Any>)

    fun addAll(index: Int = size, otherValues: LIST)
    fun addAll(otherValues: LIST) {
        addAll(size, otherValues)
    }

    fun contains(value: VALUE) : Boolean

    fun remove(index: Int, count: Int = 1)

    /** your models are now my models too */
    fun enslave(other: ListModeling<LIST, VALUE>) {
        values = other.values
    }

    /** remove all models */
    fun clear()
}

interface MutableListModeling<VALUE> : ListModeling<MutableList<VALUE>, VALUE> {

    override val size: Int
        get() {
            return values.size
        }

    override fun add(value: VALUE) {
        values.add(size, value)
    }

    override fun add(index: Int, value: VALUE) {
        values.add(index, value)
    }

    override fun add(index: Int, value: Modeling<VALUE, Any>) {
        values.add(index, value.values!!)
    }

    override fun contains(value: VALUE) : Boolean {
        return values.contains(value)
    }

    override fun remove(index: Int, count: Int) {
        val till = when {
            size > index + count -> count
            else -> count + index - size
        }

        for (i in 0..till-1) {
            values.removeAt(index)
        }
    }

    override fun addAll(index: Int, otherValues: MutableList<VALUE>) {
        values.addAll(index, otherValues)
    }

    override fun clear() {
        values.clear()
    }

}

interface JSONArrayListModeling : ListModeling<JSONArray, JSONObject>, HasSerializableData {

    override val size: Int
        get() {
            return values.size
        }

    override fun get(index: Int): JSONObject {
        return values[index] as JSONObject
    }

    override fun add(value: JSONObject) {
        values.add(size, value)
    }

    override fun add(index: Int, value: JSONObject) {
        val jsonArray = JSONArray()
        jsonArray.add(value)
        addAll(index, jsonArray)
    }

    override fun add(index: Int, value: Modeling<JSONObject, Any>) {
        val jsonArray = JSONArray()
        jsonArray.add(value.values)
        addAll(index, jsonArray)
    }

    override fun contains(value: JSONObject) : Boolean {
        return values.contains(value)
    }

    override fun remove(index: Int, count: Int) {
        val till = when {
            size > index + count -> count
            else -> size - index
        }

        for (i in 0..till - 1) {
            values.removeAt(index)
        }
    }

    override fun addAll(index: Int, otherValues: JSONArray) {

        // screw JSONArray for not having option to put at start of list or merge not to say
        // put other JSONArray in the middle
        if (index == values.size) {
            for (model in otherValues) {
                values.add(model)
            }
        } else if (index == 0) {
            val oldModels = values
            values = JSONArray()
            for (model in otherValues) {
                values.add(model)
            }
            for (model in oldModels) {
                values.add(model)
            }
        } else {
            val oldModels = values
            values = JSONArray()
            for (i in 0..index - 1) {
                values.add(oldModels[i])
            }
            for (i in 0..otherValues.size - 1) {
                values.add(otherValues[i])
            }
            for (i in index..oldModels.size - 1) {
                values.add(oldModels[i])
            }
        }
    }

    override fun clear() {
        values = JSONArray()
    }

    override fun dataToSerializable(): Serializable {
        return values.toString()
    }

    override fun createDataFrom(serializedData: Serializable) {
        values = JSONValue.parse(serializedData as String) as JSONArray
    }
}

interface CursorListModeling : ListModeling<CursorModel, Cursor> {

    override val size: Int
        get() {
            return values.values?.count ?: 0
        }

    /**
     *  !note: returned value is unsafe - only hold in local scope - _cursorPosition may change
     *
     *  @return
     */
    override fun get(index: Int): Cursor? {
        values._cursorPosition = index
        return values.values
    }

    override fun contains(value: Cursor) : Boolean {
        throw UnsupportedOperationException()
    }

    override fun add(value: Cursor) {
        throw UnsupportedOperationException()
    }

    override fun add(index: Int, value: Cursor) {
        throw UnsupportedOperationException()
    }

    override fun add(index: Int, value: Modeling<Cursor, Any>) {
        throw UnsupportedOperationException()
    }

    override fun addAll(index: Int, otherValues: CursorModel) {
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
 * [JSONArrayListModeling] used in [JSONArrayEntity]
 */
open class JSONArrayListModel(values: JSONArray = JSONArray()) : JSONArrayListModeling {

    override var values: JSONArray = values
}
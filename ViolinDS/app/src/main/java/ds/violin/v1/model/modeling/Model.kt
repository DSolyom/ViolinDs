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
import ds.violin.v1.datasource.sqlite.*
import ds.violin.v1.model.entity.HasSerializableData
import org.json.JSONObject
import java.io.Serializable
import java.util.*

interface GetSetModeling {

    fun has(key: String): Boolean
    fun get(key: String): Any?
    fun put(key: String, value: Any?)

    fun remove(key: String) {
        put(key, null)
    }
}

interface Modeling<T> : GetSetModeling {

    var values: T?

}

interface IterableModeling<T> : Modeling<T>, Iterable<String> {

}

interface MapModeling<VALUE> : IterableModeling<MutableMap<String, VALUE>> {

    override fun get(key: String): Any? {
        return values!![key]
    }

    override fun put(key: String, value: Any?) {
        if (value == null) {
            values!!.remove(key)
        } else {
            values!![key] = value as VALUE
        }
    }

    override fun has(key: String): Boolean {
        return values?.containsKey(key) ?: false
    }

    fun clear() {
        values!!.clear()
    }

    override operator fun iterator(): Iterator<String> {
        return values!!.keys.iterator()
    }
}

interface JSONModeling : IterableModeling<JSONObject>, HasSerializableData {

    override fun get(key: String): Any? {
        return values!!.opt(key)
    }

    override fun put(key: String, value: Any?) {
        values!!.put(key, value)
    }

    override fun has(key: String): Boolean {
        return values?.has(key) ?: false
    }

    fun clear() {
        values = JSONObject()
    }

    override operator fun iterator(): Iterator<String> {
        return values!!.keys()
    }

    override fun dataToSerializable(): Serializable {
        return values.toString()
    }

    override fun createDataFrom(serializedData: Serializable) {
        values = JSONObject(serializedData as String)
    }
}

/**
 *  !note: unsafe - only hold in local scope - cursorPosition may change
 */
interface CursorModeling : Modeling<Cursor> {

    var _cursorPosition: Int

    override fun get(key: String): Any? {

        /** make sure cursor is in [_cursorPosition] at this point */
        val columnIndex = values!!.getColumnIndex(key)
        return when (values!!.getType(columnIndex)) {
            Cursor.FIELD_TYPE_INTEGER -> values!!.getInt(columnIndex)
            Cursor.FIELD_TYPE_STRING -> values!!.getString(columnIndex)
            Cursor.FIELD_TYPE_FLOAT -> {
                try {
                    values!!.getDouble(columnIndex)
                } catch(e: Throwable) {
                    values!!.getFloat(columnIndex)
                }
            }
            else -> null
        }
    }

    override fun has(key: String): Boolean {
        return (values?.getColumnIndex(key) ?: -1) != -1
    }

    override fun put(key: String, value: Any?) {
        throw UnsupportedOperationException()
    }

    override fun remove(key: String) {
        throw UnsupportedOperationException()
    }
}

/**
 * [JSONModeling] used in JSONEntity and [JSONArrayModelListing]
 */
open class JSONModel(values: JSONObject = JSONObject()) : JSONModeling {

    override var values: JSONObject? = values
}

/**
 * [MapModeling] for MapEntity
 */
open class MapModel(values: MutableMap<String, Any> = HashMap()) : MapModeling<Any> {

    override var values: MutableMap<String, Any>? = values
}

/**
 * a [MapModeling] which [HasSerializableData] for SerializableMapEntity and as such
 * can only use [Serializable] data
 */
open class SerializableMapModel(values: MutableMap<String, Serializable> = HashMap()) :
        MapModeling<Serializable>, HasSerializableData {

    override var values: MutableMap<String, Serializable>? = values
        set(value) {
            if (value !is Serializable) {
                throw IllegalArgumentException()
            }
            field = value
        }

    override fun dataToSerializable(): Serializable {
        return values as HashMap
    }

    override fun createDataFrom(serializedData: Serializable) {
        values = serializedData as MutableMap<String, Serializable>
    }

}

/**
 * [CursorModeling] used in [CursorEntity] and [CursorModelListing]
 */
open class CursorModel(values: Cursor? = null, cursorPosition: Int = 0) : CursorModeling {

    override var values: Cursor? = values
        set(value) {
            if (field != null && field != value && !field!!.isClosed) {
                field!!.close()
            }
            field = value
        }

    override var _cursorPosition: Int = cursorPosition

    init {
        values?.moveToPosition(_cursorPosition)
    }
}
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
import ds.violin.v1.util.common.JSONObjectWrapper
import org.json.simple.JSONObject
import org.json.simple.JSONValue
import java.io.Serializable
import java.util.*

interface GetSetModeling<VALUE> {

    fun has(key: String): Boolean
    operator fun get(key: String): VALUE?
    operator fun set(key: String, value: VALUE?)
    fun remove(key: String) : VALUE?
}

interface Modeling<T, VALUE> : GetSetModeling<VALUE> {

    var values: T?

}

interface IterableModeling<T, VALUE> : Modeling<T, VALUE>, Iterable<Any?> {

}

interface MapModeling<VALUE> : IterableModeling<MutableMap<String, VALUE>, VALUE> {

    override fun get(key: String): VALUE? {
        return values!![key]
    }

    override fun set(key: String, value: VALUE?) {
        if (value == null) {
            values!!.remove(key)
        } else {
            values!![key] = value
        }
    }

    override fun has(key: String): Boolean {
        return values?.containsKey(key) ?: false
    }

    override fun remove(key: String) : VALUE? {
        return values?.remove(key)
    }

    fun clear() {
        values!!.clear()
    }

    override operator fun iterator(): Iterator<Any?> {
        return values!!.keys.iterator()
    }
}

interface JSONModeling : IterableModeling<JSONObject, Any>, HasSerializableData, Serializable {

    override fun get(key: String): Any? {
        return values!![key]
    }

    override fun set(key: String, value: Any?) {
        values!!.put(key, value)
    }

    override fun has(key: String): Boolean {
        return values?.contains(key) ?: false
    }

    override fun remove(key: String) : Any? {
        return values?.remove(key)
    }

    fun clear() {
        values = JSONObject()
    }

    override operator fun iterator(): Iterator<Any?> {
        return values!!.keys.iterator()
    }

    override fun dataToSerializable(): Serializable {
        return JSONObjectWrapper(values)
    }

    override fun createDataFrom(serializedData: Serializable) {
        values = (serializedData as JSONObjectWrapper).values!!
    }
}

/**
 *  !note: unsafe - only hold in local scope - cursorPosition may change
 */
interface CursorModeling : Modeling<Cursor, Any> {

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

    override fun set(key: String, value: Any?) {
        throw UnsupportedOperationException()
    }

    override fun remove(key: String) {
        throw UnsupportedOperationException()
    }
}

/**
 * [JSONModeling] used in JSONEntity and [JSONArrayListModeling]
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
 * a [MapModeling] which is [Serializable] and [HasSerializableData] for SerializableMapEntity and
 * as such can only use [Serializable] data
 */
open class SerializableMapModel(values: MutableMap<String, Serializable> = HashMap()) :
        MapModeling<Serializable>, Serializable, HasSerializableData {

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
 * [CursorModeling] used in [CursorEntity]
 */
open class CursorModel(values: Cursor? = null, cursorPosition: Int = 0) : CursorModeling {

    override var values: Cursor? = values
        set(value) {
            if (field != null && field != value && !field!!.isClosed) {
                field!!.close()
            }
            value?.moveToPosition(_cursorPosition)
            field = value
        }

    override var _cursorPosition: Int = cursorPosition
        set(value) {
            values?.moveToPosition(value)
            field = value
        }

    init {
        values?.moveToPosition(_cursorPosition)
    }
}
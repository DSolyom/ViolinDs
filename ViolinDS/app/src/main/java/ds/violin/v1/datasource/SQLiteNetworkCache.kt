/*
    Copyright 2016 Dániel Sólyom

    Licensed under the Apache License, Version 2.0 (the "License")
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package ds.violin.v1.datasource

import ds.violin.v1.Global
import ds.violin.v1.datasource.SQLiteDatabase
import ds.violin.v1.datasource.base.RequestDescriptor
import ds.violin.v1.datasource.networking.RTPKey
import ds.violin.v1.datasource.sqlite.Column
import ds.violin.v1.datasource.sqlite.SQLiteQueryParams
import ds.violin.v1.datasource.sqlite.Table
import ds.violin.v1.model.JSONArrayEntity
import ds.violin.v1.util.cache.Caching
import ds.violin.v1.util.common.Debug
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import java.util.*

class SQLiteNetworkCache : Caching<RTPKey, String> {

    companion object {
        const val theCacheTableName = "the_cache"

        val theDB: SQLiteDatabase by lazy {
            object : SQLiteDatabase("__violin_network_cache_", 1,
                    arrayOf(Table(theCacheTableName,
                            arrayOf(Column("recipient", Column.TYPE_TEXT or Column.UNIQUE_GROUP),
                                    Column("target", Column.TYPE_TEXT or Column.UNIQUE_GROUP),
                                    Column("params", Column.TYPE_TEXT or Column.UNIQUE_GROUP),
                                    Column("value", Column.TYPE_TEXT)),
                            null)
                    ), arrayOf()) {

                override fun createDescriptor(requestName: String, params: Any?): RequestDescriptor<*>? {

                    // we have only one query request:
                    return RequestDescriptor(theCacheTableName,
                            SQLiteQueryParams(
                                    select = arrayListOf("value"),
                                    where = params as HashMap<String, Array<String>?>
                            )
                    )
                }

                override fun updateDB(db: android.database.sqlite.SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
                }

            }
        }
    }

    override fun put(key: RTPKey, value: String): Boolean {
        var ret = false
        synchronized(theDB) {
            val valuesJSON = JSONObject()
            valuesJSON.put("recipient", key.recipient)
            valuesJSON.put("target", key.target)
            valuesJSON.put("params", key.params)
            valuesJSON.put("value", value)
            val json = JSONArray()
            json.add(valuesJSON)

            val writer = theDB.prepareWriter(Global.context, theCacheTableName, json)
            writer?.execute { added, error ->
                if (error != null) {
                    Debug.logException(error)
                } else {
                    ret = added == 1
                }
            }
        }

        return ret
    }

    override fun get(key: RTPKey): String? {
        var ret: String? = null

        synchronized(theDB) {
            val query = theDB.prepareQuery(Global.context, theCacheTableName,
                    hashMapOf<String, Array<String>?> (
                            "recipient = ?" to arrayOf(key.recipient),
                            "target = ?" to arrayOf(key.target),
                            "params = ?" to arrayOf(key.params!!)
                    )
            )

            query?.execute { result, error ->
                if (error != null) {
                    Debug.logException(error)
                } else if (result!!.cursor!!.moveToFirst()) {
                    ret = result.cursor!!.getString(0)
                }
            }
        }
        return ret
    }

    override fun remove(key: RTPKey) {

        synchronized(theDB) {
            var deleteStatement = "DELETE FROM ${theCacheTableName} WHERE recipient='$key.recipient' AND target='$key.target'"
            if (key.params != null) {
                deleteStatement += " AND " + "params='" + key.params + "'"
            }

            theDB.getWritableDatabase(Global.context).execSQL(deleteStatement)
        }
    }

    override fun has(id: RTPKey): Boolean {
        throw UnsupportedOperationException("use get instead")
    }

    override fun clear() {
        theDB.getWritableDatabase(Global.context).execSQL("DELETE FROM ${theCacheTableName}")
        theDB.getWritableDatabase(Global.context).execSQL("vacuum")
    }

}
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

import android.database.sqlite.SQLiteDatabase
import ds.violin.v1.datasource.base.RequestDescriptor
import ds.violin.v1.datasource.sqlite.Column
import ds.violin.v1.datasource.sqlite.SQLiteModelDataExecuting
import ds.violin.v1.datasource.sqlite.SQLiteStatementExecutor
import ds.violin.v1.datasource.sqlite.Table
import ds.violin.v1.model.modeling.JSONModel
import ds.violin.v1.util.common.Debug
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import java.util.*

open class JoinedTableSQLiteModelStatementExecutor(dbTables: Array<Table>) : SQLiteModelDataExecuting {

    companion object {
        const val TAG_LOG = "JoinedTableSQLMSE"
    }

    val dbTables: Array<Table> = dbTables

    override lateinit var request: RequestDescriptor<Any>
    override lateinit var recipient: SQLiteDatabase

    override var sending: Boolean = false
    override var interrupted: Boolean = false

    override fun executeForResult(): Int? {
        try {

            recipient.beginTransaction()

            val result = bulkExecute(request)

            recipient.setTransactionSuccessful()
            recipient.endTransaction()
            recipient.execSQL("VACUUM")

            return result
        } catch(e: Throwable) {
            recipient.endTransaction()
            throw e
        }
    }

    open fun bulkExecute(request: RequestDescriptor<Any>): Int? {
        val params = request.params as JSONModel
        val table = request.target as Table
        val data = params["data"] as JSONArray
        val truncate = params["truncate"] as Boolean? ?: false
        var errorCount = 0

        val joinedTables = ArrayList<String>()
        val vsTables = ArrayList<String>()

        for (column in table.columns) {

            // collect joined tables
            if ((column.type and (Column.JOINED_TABLE or Column.VS_TABLE)) > 0 &&
                    (column.type and Column.JOINED_ON_DELETE_TABLE) == 0) {

                if (truncate) {
                    recipient.execSQL("DELETE FROM " + column.name)
                }

                if (column.type and Column.JOINED_TABLE > 0) {

                    // joined table
                    vsTables.add("")
                    joinedTables.add(column.name)
                } else {

                    // vs table
                    if (column.name.startsWith(table.name + "_vs_")) {

                        // maintable vs joined
                        joinedTables.add(column.name.substring(table.name.length + 4))
                        vsTables.add(column.name)
                    } else if (column.name.endsWith("_vs_" + table.name)) {

                        // joined vs maintable
                        joinedTables.add(column.name.substring(0, column.name.length - table.name.length - 4))
                        vsTables.add(column.name)
                    }
                }
            }
        }

        val insertExecutor = SQLiteStatementExecutor()
        var replaceExecutor: SQLiteStatementExecutor? = null

        // prepare rows
        prepareRows(table, data)

        // model for statement executors
        val rowModel = JSONModel()
        rowModel.values = data[0] as JSONObject

        insertExecutor.createStatements(table, rowModel, recipient)

        val sJTs = joinedTables.size
        /** data for joined 'sub' tables */
        val subRowsData = arrayOfNulls<JSONArray>(sJTs)
        /** data for vs tables */
        val vsTablesData = arrayOfNulls<JSONArray>(sJTs)

        for (row in data) {
            if (interrupted) {
                throw(InterruptedException())
            }

            row as JSONObject

            if (params["parent"] != null) {

                // add parent table id
                row.put(params["parent"], params["parentId"])
            }

            rowModel.values = row

            var idValue = when (table.idColumn != null && row.containsKey(table.idColumn)) {
                true -> row[table.idColumn]
                false -> null
            }
            // check if idValue is valid (ok at least it's not 0 when it should be Int)
            if (idValue !is Int || idValue != 0) {

                /** inserting for id */
                idValue = insertExecutor.bind(table, rowModel)
                try {
                    val tmp = insertExecutor.executeStatements()
                    if (idValue == null && tmp != null) {
                        idValue = tmp
                    }
                } catch(e: Throwable) {
                    ++errorCount
                    Debug.logE(TAG_LOG, "Execute error - table: " + table.name + " data: " + row.toString())
                    continue
                }
            }
            if (idValue == null || (idValue is Int && idValue == 0)) {

                /** no idValue no joined tables */
                continue
            }

            /** if row changes after first insert it needs to be inserted again */
            var rowChanged = false

            // sub and joined tables
            for (j in 0..sJTs - 1) {
                if (subRowsData[j] == null) {
                    subRowsData[j] = JSONArray()
                }
                var joinedTable: Table? = findTableByName(joinedTables[j], dbTables)
                joinedTable!!

                val tableIdColumnName = getTableIdColumnName(table, joinedTable)
                val joinedTableIdColumnName = getTableIdColumnName(joinedTable, table)

                if (vsTables[j].length == 0) {

                    // sub table
                    try {
                        // check if joinedTable has id column for this entity
                        // check if truncate needed if it has
                        if (joinedTable.findColumn(tableIdColumnName) != null && !truncate) {

                            // joined sub table was not truncated so we need to remove all connected entries from them
                            recipient.delete(joinedTable.name, "`$tableIdColumnName`=?", arrayOf(idValue.toString()))
                        }

                        var subdata: Any? = row[joinedTable.name] ?: continue

                        if (subdata is JSONObject) {

                            // single object
                            subdata.put(tableIdColumnName, idValue)
                            subRowsData[j]!!.add(subdata)

                            // if our table has an id row for joinedTable we need to update it (1 v 1 connection)
                            val idColumnForJoinedTable = table.findColumn(joinedTableIdColumnName)
                            if (idColumnForJoinedTable != null && !row.containsKey(idColumnForJoinedTable.name)) {
                                row.put(idColumnForJoinedTable.name, subdata[joinedTable.idColumn])
                                rowChanged = true

                                Debug.logE("MCJSONToDb", "adding new entity id " + idColumnForJoinedTable.name + ": " + subdata[joinedTable.idColumn])
                            }
                        } else {

                            // list
                            subdata as JSONArray
                            for (k in 0..subdata.size - 1) {
                                val subdataObj = subdata.get(k) as JSONObject
                                subdataObj.put(tableIdColumnName, idValue)

                                // maybe has position in column but not in data?
                                if (!subdataObj.containsKey("position")) {

                                    // can put position in even if there is no column for it
                                    // it'll be just skipped
                                    subdataObj.put("position", k);
                                }
                                subRowsData[j]!!.add(subdataObj)
                            }
                        }
                    } catch (e: Throwable) {
                        Debug.logE(TAG_LOG, "expected ${joinedTable.name} subdata object but got something else")
                        Debug.logException(e);
                    }
                } else {

                    // vs table
                    if (vsTablesData[j] == null) {
                        vsTablesData[j] = JSONArray()
                    }

                    try {
                        val vsTableName = vsTables[j]
                        if (!truncate) {

                            /**
                             * vs table was not truncated so we need to remove all connections
                             * with this entry from them
                             */
                            recipient.delete(vsTableName, "`$tableIdColumnName`=?", arrayOf(idValue.toString()))
                        }

                        var vsData = row[joinedTable.name] ?: continue

                        if (vsData !is JSONArray) {
                            val tmp = JSONArray()
                            tmp.add(vsData as JSONObject)
                            vsData = tmp
                        }

                        // real joined table data
                        for (subRow in vsData) {
                            subRow as JSONObject

                            // add the new subrow
                            subRowsData[j]!!.add(subRow)

                            // add to vs table
                            val vsTableRow = JSONObject()
                            vsTableRow.put(tableIdColumnName, idValue)
                            vsTableRow.put(joinedTableIdColumnName, vsTableRow[joinedTable.idColumn])
                            vsTablesData[j]!!.add(vsTableRow)
                        }

                        // also remove all vs table data for main table id
                        recipient.delete(vsTableName, "`$tableIdColumnName`=?", arrayOf(idValue.toString()))
                    } catch (e: Throwable) {

                        Debug.logException(e)

                        // no values for this joined tables
                        continue;
                    }
                }
            }

            if (rowChanged) {
                if (replaceExecutor == null) {
                    replaceExecutor = SQLiteStatementExecutor()
                    replaceExecutor.method = SQLiteStatementExecutor.Method.UPDATE
                    replaceExecutor.createStatements(table, rowModel, recipient)
                }

                replaceExecutor.bind(table, rowModel)

                try {
                    replaceExecutor.executeStatements()
                } catch (e: Throwable) {
                    Debug.logE(TAG_LOG, "Execute error - table: " + table.name + " data: " + row.toString())
                    continue
                }
            }

            // execute for joined tables
            for (i in 0..sJTs - 1) {

                // vs table data if we got any
                val paramsModel = JSONModel()
                paramsModel.put("truncate", false)
                if (vsTablesData[i] != null && vsTablesData[i]!!.size > 0) {
                    paramsModel.put("data", vsTablesData[i])
                    val subRequest = RequestDescriptor<Any>(
                            findTableByName(vsTables[i], dbTables)!!,
                            paramsModel
                    )

                    bulkExecute(subRequest)
                }

                // and bulk execute collected joined table data
                paramsModel.put("truncate", vsTablesData[i] == null && truncate)
                paramsModel.put("data", subRowsData[i])
                val subRequest = RequestDescriptor<Any>(
                        findTableByName(joinedTables[i], dbTables)!!,
                        paramsModel
                )

                bulkExecute(subRequest)
            }
        }

        return data.size - errorCount
    }

    /**
     * prepare rows - called before inserting anything to the [table]
     *              - change data here to (ie.) synchronize it with db's structure
     *
     * !note: if you want to modify specific row's data use [prepareRow]
     */
    open fun prepareRows(table: Table, data: JSONArray) {

    }

    /**
     * prepare row - called before inserting the row
     *             - change data here (ie.) when it's format not matching the [table]'s column's format
     */
    open fun prepareRow(table: Table, row: JSONObject) {

    }

    /**
     * get the [table]'s id column's name in the [joinedTable]
     */
    open fun getTableIdColumnName(table: Table, joinedTable: Table): String {
        return table.name + "_" + table.idColumn
    }

    /**
     *
     */
    fun findTableByName(tableName: String, tables: Array<Table>): Table? {
        for (dbTable in dbTables) {
            if (dbTable.name == tableName) {
                return dbTable
            }
        }
        return null
    }
}
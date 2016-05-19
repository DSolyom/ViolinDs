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

package ds.violin.v1.datasource.sqlite

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import ds.violin.v1.model.modeling.IterableModeling
import ds.violin.v1.datasource.base.*
import ds.violin.v1.util.common.Debug
import org.json.JSONObject
import java.util.*

data class SQLiteQueryParams(var distinct: Boolean = true,
                             var select: MutableList<String>? = null,
                             var joined: HashMap<String, JoinType>? = null,
                             var where: HashMap<String, Array<String>?>? = null,
                             var groupBy: MutableList<String>? = null,
                             var having: MutableList<String>? = null,
                             var orderBy: MutableList<String>? = null,
                             var limit: String? = null
)

data class SQLiteResult(val cursor: Cursor?,
                        val count: Int)

interface SQLiteSessionHandling : SessionHandling<SQLiteOpenHelper> {

    companion object {
        val openHelpers = HashMap<String, SQLiteOpenHelper>()
    }

    val tables: Array<Table>
    val connections: Array<TableConnection>

    val dbName: String
    val dbVersion: Int

    fun ensureState(context: Context) {
        if (!openHelpers.containsKey(dbName)) {
            openHelpers[dbName] = object : SQLiteOpenHelper(context, dbName, null, dbVersion) {

                override fun onCreate(db: SQLiteDatabase?) {
                    createDB(db)
                }

                override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
                    updateDB(db, oldVersion, newVersion)
                }
            }
        }
        state = openHelpers[dbName]!!
    }

    fun createDB(db: SQLiteDatabase?) {
        for (table in tables) {
            SQLiteUtils.createTable(table, db!!)
        }
    }

    fun updateDB(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int)
}

interface SQLiteQueryExecuting : RequestExecuting<SQLiteQueryParams, SQLiteDatabase, SQLiteResult> {

    val connections: Array<TableConnection>

    override fun executeForResult(): SQLiteResult? {

        val queryParams = request.params!!

        val tableWJoins: String = createTableWithJoins(request.target as String, queryParams.joined)
        val columns: Array<String>? = queryParams.select?.toTypedArray() ?: null
        val selection: String? = createSelection(queryParams.where?.keys ?: null)
        val selectionArgs: Array<String>? = createSelectionArgs(queryParams.where)
        val groupBy: String? = queryParams.groupBy?.joinToString { "," } ?: null
        val having: String? = createSelection(queryParams.having)
        val orderBy: String? = queryParams.orderBy?.joinToString { "," } ?: null

        val cursor = recipient.query(queryParams.distinct, tableWJoins, columns, selection, selectionArgs, groupBy,
                having, orderBy, queryParams.limit)

        return SQLiteResult(cursor, cursor.count)
    }

    /**
     * #Private
     */
    fun createTableWithJoins(mainTable: String, joined: HashMap<String, JoinType>?): String {
        var tableWJoins = mainTable

        if (joined != null)
            for (joinedTable in joined.keys) {
                tableWJoins += when (joined[joinedTable]) {
                    JoinType.INNER -> " JOIN " + joinedTable
                    else -> " LEFT JOIN " + joinedTable
                }

                val joinedTmp = joinedTable.split(" AS ")
                val joinedTableName = joinedTmp[0]
                val joinedTableAlias = when (joinedTmp.size) {
                    1 -> joinedTmp[0]
                    else -> joinedTmp[1]
                }

                for (connection in connections) {
                    if (connection.leftName == mainTable && connection.rightName == joinedTableName) {
                        tableWJoins += " ON " +
                                mainTable + "." + SQLiteUtils.quoteName(connection.leftColumn) +
                                " = " +
                                joinedTableAlias + "." + SQLiteUtils.quoteName(connection.rightColumn)
                        break
                    }
                    if (connection.leftName == joinedTableName && connection.rightName == mainTable) {
                        tableWJoins += " ON " +
                                joinedTableAlias + "." + SQLiteUtils.quoteName(connection.leftColumn) +
                                " = " +
                                mainTable + "." + SQLiteUtils.quoteName(connection.leftColumn)
                        break
                    }
                }
            }
        return tableWJoins
    }

    /**
     * #Private
     */
    fun createSelection(selections: Collection<String>?): String? {
        if (selections == null || selections.size == 0) {
            return null
        }

        var ret = ""

        for (selection in selections) {
            ret += " AND " + selection
        }

        return ret.substring(5)
    }

    /**
     * #Private
     */
    fun createSelectionArgs(selections: HashMap<String, Array<String>?>?): Array<String>? {
        if (selections == null || selections.size == 0) {
            return null
        }

        var merged = arrayOf<String>()

        for (args in selections.values) {
            if (args == null) {
                continue
            }
            merged += args
        }

        return merged
    }
}

interface SQLiteModelStatementExecuting : RequestExecuting<Any, SQLiteDatabase, Int?> {

    enum class Method {
        INSERT, INSERT_UPDATE, UPDATE, OVERWRITE
    }

    /** method of the execution */
    var method: Method

    /** lateinit - #Private */
    var columns: Array<Column?>
    /** =0 - #Private */
    var columnCount: Int
    /** #Private */
    var insertStatement: SQLiteStatement?
    /** #Private */
    var updateStatement: SQLiteStatement?

    /**
     * currently only supporting insert or update
     *
     * @param target
     * @param model
     */
        fun createStatements(target: Table, model: IterableModeling<*>, recipient: SQLiteDatabase) {

        val columns = target.columns

        columnCount = 0

        var columnNames = ""
        var insertUniqueNames = ""
        var insertQmarks = ""
        var updateWhere = ""
        var updateSet = ""
        var hasUnique = false

        for (column in columns) {
            if ((column.type and Column.JOINED_TABLE) > 0 || !model.has(column.name)) {
                continue
            }

            ++columnCount
        }

        this.columns = arrayOfNulls<Column?>(columnCount)

        var next = 0;
        var last = columnCount;

        for (column in columns) {
            if ((column.type and Column.JOINED_TABLE) > 0 || !model.has(column.name)) {
                continue;
            }

            insertQmarks += ",?";

            if ((column.type and (Column.UNIQUE or Column.PRIMARY)) == 0) {
                columnNames += "," + column.name;
                updateSet += ", " + column.name + " = ? ";
                this.columns[next++] = column;
            } else {
                hasUnique = true;
                insertUniqueNames = ", " + column.name + insertUniqueNames;
                updateWhere = ", " + column.name + " = ? " + updateWhere;

                // update where columns go to the end for easier bind
                this.columns[--last] = column;
            }
        }

        if (columnNames.length > 0) {
            columnNames += insertUniqueNames;
        } else {
            columnNames = insertUniqueNames;
        }

        if (method != Method.UPDATE) {
            insertStatement = recipient.compileStatement("INSERT OR ABORT INTO " + target.name +
                    " (" + columnNames.substring(1) + ") VALUES (" + insertQmarks.substring(1) + ")");
        } else {
            insertStatement = null
        }

        if (hasUnique && method != Method.INSERT) {
            updateStatement = recipient.compileStatement("UPDATE " + target.name + " SET " +
                    updateSet.substring(1) + " WHERE " + updateWhere.substring(1));
        } else {
            updateStatement = null;
        }
    }

    /**
     * bind model data to the [insertStatement] and [updateStatement]
     *
     * @param table
     * @param model
     * @return
     */
    open fun bind(target: Table, model: IterableModeling<*>): Any? {

        // in case previous one was simple insert
        if (updateStatement != null) {
            updateStatement!!.clearBindings();
        }

        var idValue: Any? = null
        var type: Int?
        var value: Any? = null

        // set values for columns
        for (i in 0..columnCount - 1) {
            try {
                if (columns[i] == null) {
                    continue;
                }

                val columnName = columns[i]!!.name
                type = columns[i]!!.type
                value = model.get(columnName)

                if ((value == null && model.has(columnName) || value == JSONObject.NULL || method == Method.OVERWRITE)
                        && (type and Column.NULL) > 0) {
                    insertStatement!!.bindNull(i + 1);
                    if (updateStatement != null) {
                        updateStatement!!.bindNull(i + 1);
                    }
                    // Debug.logD("JSONToDb", "binding null for " + mColumns[i].name);
                    continue;
                }
                if (value == null) {
                    continue;
                }

                type = type and Column.TYPE_MASK;

                bind(value, type, columnName, i)

                if (columnName == target.idColumn) {
                    idValue = value
                }
            } catch(e: Throwable) {
                Debug.logException(e);

                val valStr: String

                try {
                    valStr = value.toString()
                } catch(e2: Throwable) {
                    valStr = "unknown";
                }

                android.util.Log.e("Bad data format", "table: " + target.name + " / column: " +
                        (columns[i]?.name ?: "unknown") + " / value: " + valStr);
                android.util.Log.e("Bad data format (raw)", model.toString());
            }
        }
        return idValue
    }

    /**
     * #Protected - bind value data
     *
     * override if value need's to be changed before binding
     *
     * @param value
     * @param type
     * @param columnName
     * @param position
     */
    open fun bind(value: Any, type: Int, columnName: String, position: Int) {
        when (type) {
            Column.TYPE_INTEGER -> {
                val longValue: Long = when (value) {
                    is String -> value.toLong()
                    is Int -> value.toLong()
                    is Number -> value.toLong()
                    is Long -> value.toLong()
                    else -> throw ClassCastException("value '$value' could not be cast to Long")
                }
                insertStatement!!.bindLong(position + 1, longValue)
                if (updateStatement != null) {
                    updateStatement!!.bindLong(position + 1, longValue)
                }
            }

            Column.TYPE_BOOLEAN -> {
                if (value !is Boolean) {
                    throw ClassCastException("value '$value' could not be cast to Boolean")
                }
                var longValue = 0L
                if (value) {
                    longValue = 1L
                }
                insertStatement!!.bindLong(position + 1, longValue)
                if (updateStatement != null) {
                    updateStatement!!.bindLong(position + 1, longValue)
                }
            }

            Column.TYPE_REAL -> {
                val doubleValue: Double = when (value) {
                    is String -> value.toDouble()
                    is Int -> value.toDouble()
                    is Number -> value.toDouble()
                    is Long -> value.toDouble()
                    is Float -> value.toDouble()
                    else -> throw ClassCastException("value '$value' could not be cast to Double")
                }
                insertStatement!!.bindDouble(position + 1, doubleValue)
                if (updateStatement != null) {
                    updateStatement!!.bindDouble(position + 1, doubleValue)
                }
            }

            else -> {
                insertStatement!!.bindString(position + 1, value.toString())
                if (updateStatement != null) {
                    updateStatement!!.bindString(position + 1, value.toString())
                }
            }
        }

        // Debug.logD("JSONToDb", "binded " + value + " for " + mColumns[i].name);
    }

    /**
     * execute statements after binding - will return the inserted id or null
     */
    fun executeStatements(): Any? {
        var ret: Any? = null
        try {
            if (insertStatement != null) {

                /** will return the inserted id - this is not always the same as the row's id */
                ret = insertStatement!!.executeInsert()
            }
        } catch (e: SQLiteException) {
            if (updateStatement == null) {
                throw e
            }
        }
        if (ret == null && updateStatement != null) {
            executeUpdate()
        }
        return ret
    }

    /**
     * #Private, execute the update statement
     */
    fun executeUpdate() {
        val ret = updateStatement!!.executeUpdateDelete()
        if (ret == 0) {
            throw SQLiteException("error executing update-delete")
        }
    }
}
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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import ds.violin.v1.model.modeling.JSONArrayModelListing
import ds.violin.v1.datasource.sqlite.*
import ds.violin.v1.datasource.base.Api
import ds.violin.v1.datasource.base.RequestDescriptor
import ds.violin.v1.model.modeling.IterableModeling

/**
 * basic [SQLiteQueryExecuting] to execute normal query, good for most cases
 */
open class BaseSQLiteQueryExecutor(connections: Array<TableConnection>) : SQLiteQueryExecuting {

    override val connections: Array<TableConnection> = connections


    override lateinit var request: RequestDescriptor<SQLiteQueryParams>
    override lateinit var recipient: SQLiteDatabase

    override var sending: Boolean = false
    override var interrupted: Boolean = false
}

/**
 * basic [SQLiteModelStatementExecuting] to insert/update a [JSONArrayModelListing] into a table
 * this only works for data belonging to only this one table
 */
open class BaseSQLiteModelStatementExecutor() : SQLiteModelStatementExecuting {

    override var method: SQLiteModelStatementExecuting.Method = SQLiteModelStatementExecuting.Method.INSERT_UPDATE

    override lateinit var request: RequestDescriptor<Any>
    override lateinit var recipient: SQLiteDatabase

    override lateinit var columns: Array<Column?>
    override var columnCount: Int = 0

    override var insertStatement: SQLiteStatement? = null
    override var updateStatement: SQLiteStatement? = null

    override var sending: Boolean = false
    override var interrupted: Boolean = false

    override fun executeForResult(): Int? {
        try {
            // always use transaction with bulk execution
            recipient.beginTransaction()

            val data: JSONArrayModelListing = request.params as JSONArrayModelListing

            val dSize = data.size
            if (dSize == 0) {

                // no data - nothing to do
                recipient.endTransaction()

                // no row's were modified
                return 0
            }

            var insertCount = 0

            /**
             * create statement with the first row of the data if you would need data with variable
             * columns to insert into the same table, [createStatements] should get a 'row' where
             * all required columns are filled (probably with dummy data)
             */
            createStatements(request.target as Table, data.get(0) as IterableModeling<*>, recipient)

            for (i in 0..dSize - 1) {

                /** binding row values - this returns the value of the [Table.idColumn] in the row */
                bind(request.target as Table, data.get(i))

                // statement is a go
                if (executeStatements() != null) {
                    ++insertCount
                }
            }

            recipient.setTransactionSuccessful()

            // return the count of the newly inserted
            return insertCount
        } catch(e: Throwable) {
            recipient.endTransaction()
            throw e
        } finally {
            try {
                recipient.endTransaction()
            } catch(e: Throwable) {
                ;
            }
        }
    }
}

/**
 * class to create [RequestDescriptor]s, [SQLiteQueryExecuting]s or [SQLiteModelStatementExecuting]s
 * and handle the database session's with [SQLiteOpenHelper]s
 *
 * @use override to create your own database api by defining the way [RequestDescriptor] are created
 *      from queryName and params (override [AbsSQLiteDatabase.createDescriptor])
 *      also you may want to override [ensureState] if you need more than one session for the same host
 *
 *      use [prepareQuery] or [prepareWriter] for a [SQLiteQueryExecuting] or [SQLiteModelStatementExecuting]
 *      and call [SQLiteQueryExecuting.execute] or [SQLiteModelStatementExecuting.execute] in them
 *
 *      !note: take hold of your executors when using them in the background, to be able to interrupt them
 *      !note: don't be afraid to create as many [AbsSQLiteDatabase] instance as you like,
 *             states are handled statically
 *      TODO: do something about that statically handled state to last through the application lifecycle
 *      TODO: for now [SQLiteSessionHandling.openHelpers] is held in Application
 *
 * @param name - name of the database to use
 * @param version - version of the database to use
 * @param tables - tables needed in the database - will be created at first use of [ensureState] for given [name]
 * @param connections - connections between tables for easier joins (or for harder for that matter because
 *                      using this will prevent you to create multiply connections between the same two tables)
 */
abstract class AbsSQLiteDatabase(name: String, version: Int, tables: Array<Table>, connections: Array<TableConnection>) :
        Api, SQLiteSessionHandling {

    override val tables: Array<Table> = tables
    override val connections: Array<TableConnection> = connections
    override val dbName: String = name
    override val dbVersion: Int = version
    override lateinit var state: SQLiteOpenHelper

    /**
     * prepare an [SQLiteQueryExecuting] for execution
     */
    fun prepareQuery(context: Context, queryName: String, params: Any?): SQLiteQueryExecuting? {

        val query = createDescriptor(queryName, params) as RequestDescriptor<SQLiteQueryParams>?
                ?: throw NoSuchMethodException("Could not create request descriptor")

        val executor = createQueryExecutor()
        ensureState(context)
        executor.prepare(query, state.writableDatabase)
        return executor
    }

    /**
     * prepare an [SQLiteModelStatementExecuting] for execution
     */
    fun prepareWriter(context: Context, target: String, params: Any): SQLiteModelStatementExecuting? {

        var request: RequestDescriptor<Any>? = null
        for (table in tables) {
            if (table.name == target) {
                request = RequestDescriptor(table, params)
            }
        }
        if (request == null) {
            throw NoSuchMethodException("Could not find table $target")
        }

        val executor = createWriteExecutor()
        ensureState(context)
        executor.prepare(request, state.writableDatabase)
        return executor
    }

    /**
     * get a writable [SQLiteDatabase] for your set [dbName] and [dbVersion] to do whatever you like directly
     */
    fun getWritableDatabase(context: Context): SQLiteDatabase {
        ensureState(context)
        return state.writableDatabase
    }

    /**
     * create [SQLiteQueryExecuting]
     */
    open fun createQueryExecutor(): SQLiteQueryExecuting {
        return BaseSQLiteQueryExecutor(connections)
    }

    /**
     * override to create your own [SQLiteModelStatementExecuting]
     */
    open fun createWriteExecutor(): SQLiteModelStatementExecuting {
        return BaseSQLiteModelStatementExecutor()
    }
}
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

import android.database.sqlite.SQLiteDatabase
import java.util.*

data class Column(val name: String, val type: Int) {

    companion object {
        const val TYPE_INTEGER = 1
        const val TYPE_TEXT = 2
        const val TYPE_REAL = 3
        const val TYPE_BOOLEAN = 4

        const val TYPE_MASK = TYPE_INTEGER or TYPE_TEXT or TYPE_REAL or TYPE_BOOLEAN

        const val PRIMARY = 256
        const val NULL = 512
        const val INDEX = 1024
        const val AUTOINCREMENT = 2048
        const val UNIQUE = 4096

        /**
         * mark columns as [UNIQUE_GROUP] when they are considered unique together when
         * using [SQLiteStatementExecutor.Method.UPDATE]
         */
        const val UNIQUE_GROUP = 8192

        const val VS_TABLE = 32768
        const val JOINED_TABLE = 65536
        const val JOINED_ON_DELETE_TABLE = 131072

        const val JOINED_MASK = VS_TABLE or JOINED_TABLE or JOINED_ON_DELETE_TABLE

        const val UNICODE = 262144
        const val NOCASE = 524288

        const val COLLATE_MASK = UNICODE or NOCASE
    }
}

enum class JoinType {
    LEFT, INNER
}

class Table(val name: String, val columns: Array<Column>, val idColumn: String? = "id") {

    fun findColumn(name: String): Column? {
        for(column in columns) {
            if (name == column.name) {
                return column
            }
        }
        return null
    }
}

class TableConnection(val leftName: String, val leftColumn: String, val rightName: String, val rightColumn: String)

object SQLiteUtils {

    /**
     * create sqlite database table
     *
     * @param table
     * @param db
     */
    fun createTable(table: Table, db: SQLiteDatabase) {
        var statement = "CREATE TABLE " + quoteName(table.name) + " ("

        val indexes = ArrayList<String>()
        var strColumns = ""

        var first = true
        for (column in table.columns) {
            if ((column.type and Column.TYPE_MASK) == 0) {
                continue
            }

            if (first) {
                first = false
            } else {
                strColumns += ", "
            }
            strColumns += columnNameAndDef(column.name, column.type)

            if ((column.type and Column.INDEX) == 0) {
                continue
            }
            indexes.add("CREATE INDEX " + table.name + column.name + "_index on " +
                    quoteName(table.name) + "(" + column.name + ");")

        }

        statement += strColumns + ");"

        db.execSQL(statement)

        for (strIndex in indexes) {
            db.execSQL(strIndex)
        }
    }

    /**
     * remove table from sqlite database
     *
     * @param table
     * @param db
     */
    fun drop(table: Table, db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS '" + table.name + "'")
    }

    /**
     * SQLite full column definition statement
     */
    fun columnNameAndDef(name: String, type: Int): String {
        return quoteName(name) + " " + columnDefString(type);
    }

    /**
     * SQLite column definition statement
     */
    fun columnDefString(type: Int): String {
        var ret = when (type and Column.TYPE_MASK) {
            Column.TYPE_INTEGER -> "INTEGER"
            Column.TYPE_BOOLEAN -> "BOOLEAN"
            Column.TYPE_REAL -> "REAL"
            else -> "TEXT"
        }

        if ((type and Column.COLLATE_MASK) > 0) {
            ret += " COLLATE";
        }
        if ((type and Column.UNICODE) > 0) {
            ret += " UNICODE";
        }
        if ((type and Column.NOCASE) > 0) {
            ret += " NOCASE";
        }

        if ((type and Column.PRIMARY) > 0) {
            ret += " PRIMARY KEY";
        } else {
            if ((type and Column.NULL) > 0) {
            ret += " NULL";
        } else {
            ret += " NOT NULL";
        }
        }
        if ((type and Column.AUTOINCREMENT) > 0) {
            ret += " AUTOINCREMENT";
        }
        if ((type and Column.UNIQUE) > 0) {
            ret += " UNIQUE";
        }

        return ret;
    }

    /**
     * quote column or table name
     *
     * @param name
     * @return
     */
    fun quoteName(name: String): String {
        val parts = name.split(" AS ")
        var first = parts[0].trim()

        if (first[0] != '`') {
            val subParts = first.split("\\.");
            first = "`" + subParts[0] + "`";
            if (subParts.size == 2) {
                first += ".`" + subParts[1] + "`";
            }
        }
        if (parts.size == 2) {
            return first + " AS " + "`" + parts[1].trim() + "`"
        }
        return first
    }
}
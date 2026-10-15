package io.github.leonardosponton.inkflow.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.leonardosponton.inkflow.domain.dbmodel.ColumnRef
import io.github.leonardosponton.inkflow.domain.dbmodel.DbColumn
import io.github.leonardosponton.inkflow.domain.dbmodel.DbIndex
import io.github.leonardosponton.inkflow.domain.dbmodel.DbTableContent
import io.github.leonardosponton.inkflow.domain.dbmodel.DdlGenerator
import io.github.leonardosponton.inkflow.domain.dbmodel.SqlDialect
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** The generated SQLite DDL must run on a real SQLite engine and enforce the keys it declares. */
@RunWith(AndroidJUnit4::class)
class SqliteDdlTest {
    private val customers = DbTableContent(
        "customers",
        listOf(
            DbColumn("c1", "id", "serial", primaryKey = true),
            DbColumn("c2", "email", "varchar(200)", notNull = true, unique = true, comment = "login"),
            DbColumn("c3", "active", "boolean", defaultValue = "true"),
            DbColumn("c4", "created_at", "timestamp", defaultValue = "CURRENT_TIMESTAMP"),
        ),
        indexes = listOf(DbIndex("idx_email", listOf("c2"), unique = true)),
    )
    private val orders = DbTableContent(
        "orders",
        listOf(
            DbColumn("o1", "id", "bigint", primaryKey = true),
            DbColumn("o2", "customer_id", "integer", notNull = true, references = ColumnRef("cust", "c1")),
            DbColumn("o3", "total", "decimal(10,2)", defaultValue = "0"),
            DbColumn("o4", "ref", "uuid"),
        ),
    )
    private val items = DbTableContent(
        "order_items",
        listOf(
            DbColumn("i1", "order_id", "bigint", primaryKey = true, references = ColumnRef("ord", "o1")),
            DbColumn("i2", "line", "int", primaryKey = true),
            DbColumn("i3", "qty", "int", notNull = true, defaultValue = "1"),
        ),
    )

    @Test
    fun generatedSqliteDdlExecutesAndEnforcesConstraints() {
        val ddl = DdlGenerator(SqlDialect.SQLITE).generate(mapOf("items" to items, "ord" to orders, "cust" to customers))
        val db = SQLiteDatabase.create(null)
        try {
            ddl.split(";").map { it.lines().filterNot { l -> l.trim().startsWith("--") }.joinToString("\n").trim() }
                .filter { it.isNotEmpty() }
                .forEach(db::execSQL)
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO customers (email) VALUES ('a@b.c')")
            db.execSQL("INSERT INTO orders (id, customer_id) VALUES (1, 1)")
            db.execSQL("INSERT INTO order_items (order_id, line) VALUES (1, 1)")
            db.rawQuery("SELECT qty, (SELECT active FROM customers) FROM order_items", null).use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals(1, it.getInt(1))
            }
            expectConstraint { db.execSQL("INSERT INTO orders (id, customer_id) VALUES (2, 99)") }
            expectConstraint { db.execSQL("INSERT INTO customers (email) VALUES ('a@b.c')") }
            expectConstraint { db.execSQL("INSERT INTO order_items (order_id, line) VALUES (1, 1)") }
            expectConstraint { db.execSQL("INSERT INTO orders (id) VALUES (3)") }
        } finally {
            db.close()
        }
    }

    private fun expectConstraint(block: () -> Unit) {
        try {
            block()
            fail("constraint not enforced")
        } catch (expected: SQLiteConstraintException) {
            // ok
        }
    }
}

package io.github.leonardosponton.inkflow.data.repository

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.leonardosponton.inkflow.data.local.InkflowDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Creates the exported schema of every version and runs all registered migrations. */
@RunWith(AndroidJUnit4::class)
class DatabaseSchemaTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), InkflowDatabase::class.java)

    @Test
    fun currentSchemaOpensAndMigrationsValidate() {
        helper.createDatabase(DB, 1).close()
        helper.runMigrationsAndValidate(DB, InkflowDatabase.VERSION, true, *InkflowDatabase.MIGRATIONS)
    }

    private companion object {
        const val DB = "schema-test"
    }
}

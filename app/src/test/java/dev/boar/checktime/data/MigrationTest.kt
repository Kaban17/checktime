package dev.boar.checktime.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Без room-testing: БД версии 1 строится по экспортированной схеме, затем её открывает текущий Room. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    private fun createV1(file: File) {
        val schema = JSONObject(File("schemas/dev.boar.checktime.data.AppDatabase/1.json").readText())
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO groups (id, name, color, sortOrder) VALUES (1, 'Личное', 1, 0)")
            db.execSQL("INSERT INTO categories (id, groupId, name, color, sortOrder, archived) VALUES (1, 1, 'Сон', 2, 0, 0)")
            db.execSQL("INSERT INTO segments (id, startAt, endAt, categoryId) VALUES (1, 0, 60000, 1)")
            db.version = 1
        }
    }

    @Test fun v1DataSurvivesAndGoalsTableAppears() = runTest {
        val file = context.getDatabasePath("migration-test.db").also { it.parentFile?.mkdirs(); it.delete() }
        createV1(file)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(listOf("Сон"), db.categoryDao().categories().map { it.name })
            assertEquals(1, db.segmentDao().all().size)
            db.goalDao().insert(WeeklyGoal(categoryId = 1, minutes = 60, validFrom = 20_000))
            assertEquals(listOf(60), db.goalDao().observeAll().first().map { it.minutes })
        } finally {
            db.close()
            file.delete()
        }
    }
}

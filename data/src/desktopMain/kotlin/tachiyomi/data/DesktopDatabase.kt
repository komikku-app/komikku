package tachiyomi.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.db.SqlDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConnectionFactory
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import tachiyomi.core.common.desktop.DesktopDirs
import java.io.File

/**
 * KMK: desktop database setup (docs/kmp-desktop/PLAN.md, D7).
 *
 * Same stack as the Android app (`AppModule`): SQLDelight over `sqldelight-androidx-driver` with the
 * bundled SQLite driver (`androidx.sqlite:sqlite-bundled`, JVM natives inside the jar), the same
 * schema and migrations ([Database.Schema]; the driver creates a new database or migrates an existing
 * one from its `user_version` when the first connection opens), foreign keys on, statement cache 25
 * and `busy_timeout = 3000` on every connection. No SQLCipher on desktop.
 */
object DesktopDatabase {

    /** Same file name as the Android database. */
    const val DATABASE_NAME = "tachiyomi.db"

    /** `<dataDir>/databases/tachiyomi.db` (same layout as the compat `Context.getDatabasePath`). */
    fun defaultFile(dataDir: File = DesktopDirs.dataDir): File =
        dataDir.resolve("databases").resolve(DATABASE_NAME)

    /** Driver for a database file; parent directories are created. */
    fun createDriver(file: File = defaultFile()): SqlDriver {
        file.absoluteFile.parentFile?.mkdirs()
        return createDriver(AndroidxSqliteDatabaseType.File(file.absolutePath))
    }

    /** Driver for any database type (e.g. [AndroidxSqliteDatabaseType.Memory] in tests). */
    fun createDriver(databaseType: AndroidxSqliteDatabaseType): SqlDriver {
        return AndroidxSqliteDriver(
            connectionFactory = object : AndroidxSqliteConnectionFactory {
                override val driver: SQLiteDriver = BundledSQLiteDriver()

                override fun createConnection(name: String): SQLiteConnection {
                    return driver.open(name).apply {
                        execSQL("PRAGMA busy_timeout = 3000")
                    }
                }
            },
            databaseType = databaseType,
            schema = Database.Schema,
            configuration = AndroidxSqliteConfiguration(
                isForeignKeyConstraintsEnabled = true,
                cacheSize = 25,
            ),
        )
    }

    /** [Database] with the same column adapters as the Android app. */
    fun createDatabase(driver: SqlDriver): Database {
        return Database(
            driver = driver,
            historyAdapter = History.Adapter(
                last_readAdapter = DateColumnAdapter,
            ),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = UpdateStrategyColumnAdapter,
                memoAdapter = MemoColumnAdapter,
            ),
            chaptersAdapter = Chapters.Adapter(
                memoAdapter = MemoColumnAdapter,
            ),
        )
    }

    /**
     * The app's [DatabaseHandler] implementation (despite its name, `AndroidDatabaseHandler` is
     * plain JVM code) over [database] and [driver].
     */
    fun createHandler(
        database: Database,
        driver: SqlDriver,
        queryDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): DatabaseHandler = AndroidDatabaseHandler(database, driver, queryDispatcher)
}

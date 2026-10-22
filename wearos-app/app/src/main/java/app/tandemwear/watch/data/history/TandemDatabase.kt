package app.tandemwear.watch.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 2 (Phase 5.4) adds the `calls` table. [MIGRATION_1_2] keeps the existing history;
 * tested in TandemDatabaseMigrationTest with a real version-1 database.
 */
@Database(entities = [HistoryEntry::class, SeenApp::class, CallEntry::class], version = 2, exportSchema = false)
abstract class TandemDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun callDao(): CallDao

    companion object {
        const val NAME = "tandemwear.db"

        /** Same SQL Room generates for [CallEntry]; the migration test validates it. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `calls` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`caller` TEXT NOT NULL, `appName` TEXT NOT NULL, `outcome` TEXT NOT NULL, `at` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calls_at` ON `calls` (`at`)")
            }
        }

        @Volatile private var instance: TandemDatabase? = null

        fun get(context: Context): TandemDatabase = instance ?: synchronized(this) {
            instance ?: builder(context, NAME)
                // Only for downgrades or unknown versions: 1 → 2 always migrates
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }

        /** Builder with every migration (tests open it without the destructive fallback). */
        fun builder(context: Context, name: String): Builder<TandemDatabase> =
            Room.databaseBuilder(context.applicationContext, TandemDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
    }
}

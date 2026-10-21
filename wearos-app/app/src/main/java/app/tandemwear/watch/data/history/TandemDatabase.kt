package app.tandemwear.watch.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HistoryEntry::class, SeenApp::class], version = 1, exportSchema = false)
abstract class TandemDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile private var instance: TandemDatabase? = null

        fun get(context: Context): TandemDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TandemDatabase::class.java, "tandemwear.db")
                // Local cache of notifications: on a schema change, start empty instead of migrating
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}

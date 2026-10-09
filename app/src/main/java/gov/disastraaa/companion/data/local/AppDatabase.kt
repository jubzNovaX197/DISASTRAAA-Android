package gov.disastraaa.companion.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import gov.disastraaa.companion.data.local.dao.LocationDao
import gov.disastraaa.companion.data.local.entity.QueuedLocationEntity
import gov.disastraaa.companion.utils.Constants

@Database(
    entities = [QueuedLocationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add columns for user and session isolation & retry tracking
                db.execSQL("ALTER TABLE queued_locations ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE queued_locations ADD COLUMN sessionId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE queued_locations ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")

                // Create indices for performance and isolation queries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_queued_locations_userId_sessionId_isSynced ON queued_locations(userId, sessionId, isSynced)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_queued_locations_timestamp ON queued_locations(timestamp)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    Constants.DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

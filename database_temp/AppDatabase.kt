package com.campus.panicbutton.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.campus.panicbutton.database.dao.AlertDao
import com.campus.panicbutton.database.dao.CampusBlockDao
import com.campus.panicbutton.database.dao.PendingOperationDao
import com.campus.panicbutton.database.entities.AlertEntity
import com.campus.panicbutton.database.entities.CampusBlockEntity
import com.campus.panicbutton.database.entities.PendingOperationEntity

/**
 * Room database for offline data caching and synchronization
 */
@Database(
    entities = [
        AlertEntity::class,
        CampusBlockEntity::class,
        PendingOperationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun alertDao(): AlertDao
    abstract fun campusBlockDao(): CampusBlockDao
    abstract fun pendingOperationDao(): PendingOperationDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        private const val DATABASE_NAME = "campus_panic_button_db"
        
        /**
         * Get database instance using singleton pattern
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration() // For development - remove in production
                .build()
                
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Clear database instance (useful for testing)
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}
package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Subscription::class, XrayConfig::class, XraySettings::class],
    version = 2,
    exportSchema = false
)
abstract class XrayDatabase : RoomDatabase() {
    abstract fun xrayDao(): XrayDao

    companion object {
        @Volatile
        private var INSTANCE: XrayDatabase? = null

        fun getDatabase(context: Context): XrayDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    XrayDatabase::class.java,
                    "nexus_xray_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

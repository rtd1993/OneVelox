package com.onevelox.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DangerEntity::class],
    version = 5,
    exportSchema = false
)
abstract class OneVeloxDatabase : RoomDatabase() {
    abstract fun dangerDao(): DangerDao
}

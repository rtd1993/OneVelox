package com.onevelox.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONObject
import java.io.File

/**
 * Copies [ASSET_DB] from the APK into a temp file, then ATTACH+INSERT into the
 * working Room database. Room cannot use createFromAsset here: the bundled file
 * has no room_master_table, so createFromAsset + fallbackToDestructiveMigration
 * would wipe the preloaded POI on first open.
 */
class BundledItaliaDb(private val context: Context) {

    data class Meta(
        val generatedAt: String,
        val remoteTimestamp: String?,
        val count: Int,
        val incomplete: Boolean
    )

    fun exists(): Boolean = runCatching {
        context.assets.open(ASSET_DB).use { true }
    }.getOrDefault(false)

    fun cacheFile(name: String): File = File(context.cacheDir, name)

    fun installInto(db: SupportSQLiteDatabase): Meta {
        if (!exists()) {
            throw IllegalStateException("italia.db non presente nell'APK")
        }
        val dest = File(context.cacheDir, BUNDLED_COPY)
        try {
            context.assets.open(ASSET_DB).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            return installFromFile(db, dest)
        } finally {
            dest.delete()
        }
    }

    fun installFromFile(db: SupportSQLiteDatabase, source: File): Meta {
        if (!source.exists() || source.length() < 100L) {
            throw IllegalStateException("File SQLite non valido")
        }
        val path = source.absolutePath.replace("'", "''")
        db.execSQL("ATTACH DATABASE '$path' AS bundled")
        try {
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM danger_points")
                db.execSQL(
                    """
                    INSERT INTO danger_points (
                      id, name, type, allowedSpeedKmh, distanceMeters, headingDeg, side,
                      branchRoadName, latitudeDeg, longitudeDeg, segmentEndLatitudeDeg,
                      segmentEndLongitudeDeg, segmentLengthMeters, restrictionSchedule, sourceDataset
                    )
                    SELECT
                      id, name, type, allowedSpeedKmh, distanceMeters, headingDeg, side,
                      branchRoadName, latitudeDeg, longitudeDeg, segmentEndLatitudeDeg,
                      segmentEndLongitudeDeg, segmentLengthMeters, restrictionSchedule, sourceDataset
                    FROM bundled.danger_points
                    """.trimIndent()
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            return readMeta(db)
        } finally {
            runCatching { db.execSQL("DETACH DATABASE bundled") }
        }
    }

    private fun readMeta(db: SupportSQLiteDatabase): Meta {
        val fromTable = runCatching { readMetaTable(db) }.getOrNull()
        if (fromTable != null) return fromTable
        return readMetaSidecar()
    }

    private fun readMetaTable(db: SupportSQLiteDatabase): Meta {
        val map = mutableMapOf<String, String>()
        db.query("SELECT k, v FROM bundled.poi_meta").use { cursor ->
            while (cursor.moveToNext()) {
                map[cursor.getString(0)] = cursor.getString(1).orEmpty()
            }
        }
        return Meta(
            generatedAt = map["generatedAt"].orEmpty().ifBlank { SNAPSHOT_DATE },
            remoteTimestamp = map["remoteTimestamp"]?.takeIf { it.isNotBlank() },
            count = map["count"]?.toIntOrNull() ?: 0,
            incomplete = map["incomplete"].equals("true", ignoreCase = true)
        )
    }

    private fun readMetaSidecar(): Meta {
        val raw = runCatching {
            context.assets.open(ASSET_META).bufferedReader().use { it.readText() }
        }.getOrNull()
        if (raw.isNullOrBlank()) {
            return Meta(SNAPSHOT_DATE, null, 0, false)
        }
        val json = JSONObject(raw)
        return Meta(
            generatedAt = json.optString("generatedAt").ifBlank { SNAPSHOT_DATE },
            remoteTimestamp = json.optString("remoteTimestamp").takeIf { it.isNotBlank() },
            count = json.optInt("count"),
            incomplete = json.optBoolean("incomplete")
        )
    }

    companion object {
        const val ASSET_DB = "poi/italia.db"
        const val ASSET_META = "poi/italia.meta.json"
        const val WORKING_DB_NAME = "italia.db"
        const val SNAPSHOT_DATE = "2026-08-15"
        private const val BUNDLED_COPY = "italia-bundled.db"
        private const val LEGACY_ROOM_DB = "onevelox.db"

        fun deleteLegacyWorkingCopy(context: Context) {
            val old = context.getDatabasePath(LEGACY_ROOM_DB)
            if (!old.exists()) return
            old.delete()
            File(old.path + "-wal").delete()
            File(old.path + "-shm").delete()
        }
    }
}

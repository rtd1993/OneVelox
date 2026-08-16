package com.onevelox.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ItaliaCatalogRemote {

    data class CatalogMeta(
        val generatedAt: String,
        val remoteTimestamp: String?,
        val count: Int,
        val incomplete: Boolean,
        val file: String,
        val sizeBytes: Long?,
        val sha256: String?
    ) {
        fun effectiveTimestamp(): String = remoteTimestamp?.takeIf { it.isNotBlank() } ?: generatedAt
    }

    suspend fun fetchMeta(): CatalogMeta = withContext(Dispatchers.IO) {
        val url = "$META_URL?t=${System.currentTimeMillis()}"
        val raw = httpGetText(url, 12_000, 20_000)
        val json = JSONObject(raw)
        CatalogMeta(
            generatedAt = json.optString("generatedAt").ifBlank { BundledItaliaDb.SNAPSHOT_DATE },
            remoteTimestamp = json.optString("remoteTimestamp").takeIf { it.isNotBlank() },
            count = json.optInt("count"),
            incomplete = json.optBoolean("incomplete"),
            file = json.optString("file").ifBlank { "italia.db" },
            sizeBytes = json.optLong("sizeBytes").takeIf { it > 0L },
            sha256 = json.optString("sha256").takeIf { it.isNotBlank() }
        )
    }

    suspend fun downloadDb(
        dest: File,
        meta: CatalogMeta,
        onBytes: (downloaded: Long, total: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        val version = meta.effectiveTimestamp()
        val encoded = java.net.URLEncoder.encode(version, Charsets.UTF_8.name())
        val url = "$DB_URL?v=$encoded"
        val conn = open(url, "application/octet-stream", 15_000, 180_000)
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("Download catalogo HTTP ${conn.responseCode}")
            }
            val expected = meta.sizeBytes ?: conn.contentLengthLong.takeIf { it > 0L } ?: -1L
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            BufferedInputStream(conn.inputStream).use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        onBytes(downloaded, expected)
                    }
                }
            }
            if (expected > 0L && downloaded != expected) {
                dest.delete()
                throw IllegalStateException("Dimensione catalogo non valida: $downloaded vs $expected")
            }
            val actualHash = digest.digest().joinToString("") { b -> "%02x".format(b) }
            val expectedHash = meta.sha256?.lowercase()
            if (!expectedHash.isNullOrBlank() && actualHash != expectedHash) {
                dest.delete()
                throw IllegalStateException("Hash SHA-256 del catalogo non corrisponde")
            }
            if (!looksLikeSqlite(dest)) {
                dest.delete()
                throw IllegalStateException("Il file scaricato non è un database SQLite")
            }
            dest
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val META_URL = "https://raw.githubusercontent.com/rtd1993/OneVelox/main/DBs/italia.meta.json"
        const val DB_URL = "https://raw.githubusercontent.com/rtd1993/OneVelox/main/DBs/italia.db"

        fun isRemoteNewer(remoteTimestamp: String?, localTimestamp: String?): Boolean {
            val remote = parseTimestamp(remoteTimestamp) ?: return false
            val local = parseTimestamp(localTimestamp) ?: return true
            return remote > local
        }

        fun parseTimestamp(raw: String?): Instant? {
            if (raw.isNullOrBlank()) return null
            val value = raw.trim()
            runCatching { Instant.parse(value) }.getOrNull()?.let { return it }
            return runCatching {
                LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant()
            }.getOrNull()
        }

        private fun looksLikeSqlite(file: File): Boolean {
            if (!file.exists() || file.length() < 100L) return false
            val header = ByteArray(16)
            file.inputStream().use { stream ->
                if (stream.read(header) < 16) return false
            }
            return header.decodeToString(throwOnInvalidSequence = false).startsWith("SQLite format 3")
        }

        private fun httpGetText(url: String, connectMs: Int, readMs: Int): String {
            val conn = open(url, "application/json", connectMs, readMs)
            return try {
                if (conn.responseCode !in 200..299) {
                    throw IllegalStateException("Catalogo GitHub HTTP ${conn.responseCode}")
                }
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }

        private fun open(url: String, accept: String, connectMs: Int, readMs: Int): HttpURLConnection {
            val conn = (URL(url).openConnection() as? HttpURLConnection)
                ?: throw IllegalStateException("Impossibile aprire $url")
            conn.connectTimeout = connectMs
            conn.readTimeout = readMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "OneVelox/1.0")
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("Cache-Control", "no-cache")
            return conn
        }
    }
}

package com.pulseloop.diagnostics

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.pulseloop.data.PulseLoopDatabase
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant

/**
 * Ported from DiagnosticsExporter.swift.
 * Builds a shareable diagnostics bundle (JSON): app/OS/device info + recent WearableLog timeline.
 */
object DiagnosticsExporter {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Serialize a diagnostics report to pretty-printed JSON.
     */
    suspend fun exportJSON(db: PulseLoopDatabase, maxLogs: Int = 500): String {
        val root = buildJsonObject {
            put("generatedAt", Instant.now().toString())

            // App info
            putJsonObject("app") {
                put("version", "1.0.0")
                put("platform", "Android")
                put("sdkVersion", Build.VERSION.SDK_INT)
            }

            // Device info
            putJsonObject("device") {
                put("model", Build.MODEL)
                put("manufacturer", Build.MANUFACTURER)
                put("osVersion", Build.VERSION.RELEASE)
                val device = db.deviceDao().current()
                if (device != null) {
                    put("wearableType", device.deviceTypeRaw)
                    put("wearableName", device.name)
                    put("capabilities", device.capabilitiesRaw)
                    put("firmware", device.firmwareVersion ?: "?")
                    put("lastSyncAt", device.lastSyncAt?.let { Instant.ofEpochMilli(it).toString() } ?: "")
                }
            }

            // Logs
            val logs = db.openHelper.readableDatabase.query(
                "wearable_logs", null, null, null, null, null,
                "timestamp DESC", maxLogs.toString()
            )
            putJsonArray("logs") {
                while (logs.moveToNext()) {
                    addJsonObject {
                        put("at", Instant.ofEpochMilli(logs.getLong(logs.getColumnIndexOrThrow("timestamp"))).toString())
                        put("category", logs.getString(logs.getColumnIndexOrThrow("categoryRaw")))
                        put("level", logs.getString(logs.getColumnIndexOrThrow("levelRaw")))
                        put("message", logs.getString(logs.getColumnIndexOrThrow("message")))
                        val meta = logs.getString(logs.getColumnIndexOrThrow("metadataJSON"))
                        if (meta != null && meta != "null") put("metadata", meta)
                        val dt = logs.getString(logs.getColumnIndexOrThrow("deviceTypeRaw"))
                        if (!dt.isNullOrEmpty()) put("deviceType", dt)
                    }
                }
            }
            logs.close()
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * Write the report to a temporary file and return its File.
     */
    suspend fun exportFile(context: Context, db: PulseLoopDatabase): File {
        val report = exportJSON(db)
        val stamp = Instant.now().toString().replace(":", "-")
        val file = File(context.cacheDir, "pulseloop-diagnostics-$stamp.json")
        file.writeText(report)
        return file
    }

    /**
     * Create a share intent for the diagnostics file.
     */
    suspend fun shareIntent(context: Context, db: PulseLoopDatabase): Intent {
        val file = exportFile(context, db)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PulseLoop Diagnostics")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

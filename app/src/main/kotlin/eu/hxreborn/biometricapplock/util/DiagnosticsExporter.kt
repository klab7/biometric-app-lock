package eu.hxreborn.biometricapplock.util

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import eu.hxreborn.biometricapplock.BuildConfig
import eu.hxreborn.biometricapplock.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticsExporter {
    private const val MAX_LINES = 5000

    private const val CONTACT_EMAIL = "hxreborn@duck.com"

    // system_server hooks log to LSPosed's own file, the module process logs to logcat
    private const val HOOK_LOG_COMMAND =
        "( grep -h ${Logger.TAG} /data/adb/lspd/log.old/verbose_*.log; " +
            "grep -h ${Logger.TAG} /data/adb/lspd/log/verbose_*.log ) 2>/dev/null | " +
            "tail -n $MAX_LINES"

    private const val LOGCAT_COMMAND = "logcat -d -s ${Logger.TAG} 2>/dev/null | tail -n $MAX_LINES"

    class NoLogsException : Exception()

    suspend fun export(
        context: Context,
        framework: String?,
    ): File =
        withContext(Dispatchers.IO) {
            val hookLog = RootShell.exec(HOOK_LOG_COMMAND).out
            val appLog = RootShell.exec(LOGCAT_COMMAND).out
            if (hookLog.isEmpty() && appLog.isEmpty()) throw NoLogsException()
            val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            File(dir, "biometricapplock-$stamp.log").apply {
                writeText(body(framework, hookLog, appLog))
            }
        }

    // SAF write to a user-picked location keeps the export permission-free
    suspend fun saveTo(
        context: Context,
        file: File,
        destination: Uri,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(destination)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: return@withContext false
                true
            }.getOrDefault(false)
        }

    fun share(
        context: Context,
        file: File,
    ) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = ClipDescription.MIMETYPE_TEXT_PLAIN
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_EMAIL, arrayOf(CONTACT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, "BiometricAppLock ${BuildConfig.VERSION_NAME} logs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.diagnostics_share_title)),
        )
    }

    private fun body(
        framework: String?,
        hookLog: List<String>,
        appLog: List<String>,
    ): String =
        buildString {
            appendLine("BiometricAppLock diagnostics")
            appendLine("version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("package ${BuildConfig.APPLICATION_ID}")
            appendLine("device ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android ${Build.VERSION.RELEASE} sdk ${Build.VERSION.SDK_INT}")
            appendLine("xposed framework: ${framework ?: "unknown"}")
            appendSection("system_server hooks (LSPosed log)", hookLog)
            appendSection("module process (logcat)", appLog)
        }

    private fun StringBuilder.appendSection(
        title: String,
        lines: List<String>,
    ) {
        appendLine("---- $title ----")
        if (lines.isEmpty()) appendLine("(none)") else lines.forEach { appendLine(it) }
    }
}

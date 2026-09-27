package com.example.juzzics.common.share

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gives Juzzics to a friend who doesn't have it, without internet: the app's own install file
 * through the share sheet (Quick Share, Bluetooth...). They open it and install.
 */
object ShareApp {

    sealed interface Result {
        data class Ready(val intent: Intent) : Result
        /** installed from the Play Store in several parts: one file can't be shared */
        data object InstalledInParts : Result
        /**
         * installed by Android Studio's Run button: marked "test only", which other phones
         * refuse to install from a file
         */
        data object TestBuild : Result
        data class Failed(val reason: String) : Result
    }

    /** copies the install file (a few MB, takes a moment) and returns the share-sheet intent */
    suspend fun prepare(context: Context): Result = withContext(Dispatchers.IO) {
        runCatching {
            val info = context.applicationInfo
            if ((info.flags and ApplicationInfo.FLAG_TEST_ONLY) != 0) return@runCatching Result.TestBuild
            if (!info.splitSourceDirs.isNullOrEmpty()) return@runCatching Result.InstalledInParts
            val version = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: ""
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            // a friendly name: the receiving phone shows it
            val apk = File(dir, "Juzzics${if (version.isNotBlank()) "-$version" else ""}.apk")
            File(info.sourceDir).copyTo(apk, overwrite = true)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
            val send = Intent(Intent.ACTION_SEND)
                .setType("application/vnd.android.package-archive")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Result.Ready(Intent.createChooser(send, "Send Juzzics to a friend"))
        }.getOrElse { Result.Failed(it.message ?: "Couldn't prepare the app file") }
    }
}

package pl.wraven.mpkfastroute.core.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object AppUpdateInstaller {
    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}

package com.rushworks.jarvis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releasePageUrl: String
)

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

class UpdateManager(
    private val context: Context
) {

    fun checkForUpdate(
        onResult: (UpdateCheckResult) -> Unit
    ) {
        Thread {
            try {
                val connection = (
                    URL(LATEST_RELEASE_API)
                        .openConnection() as HttpURLConnection
                    ).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty(
                        "User-Agent",
                        "JARVIS-Android-Updater"
                    )
                    setRequestProperty(
                        "Accept",
                        "application/vnd.github+json"
                    )
                }

                val code = connection.responseCode
                if (code !in 200..299) {
                    post(
                        onResult,
                        UpdateCheckResult.Error(
                            "GitHub respondeu HTTP $code."
                        )
                    )
                    return@Thread
                }

                val json = JSONObject(
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }
                )

                val tag = json
                    .optString("tag_name")
                    .removePrefix("v")

                val pageUrl = json
                    .optString("html_url")

                val assets = json.getJSONArray("assets")
                var downloadUrl: String? = null

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (
                        asset.optString("name")
                            .equals("JARVIS.apk", ignoreCase = true)
                    ) {
                        downloadUrl = asset
                            .optString("browser_download_url")
                        break
                    }
                }

                if (tag.isBlank() || downloadUrl.isNullOrBlank()) {
                    post(
                        onResult,
                        UpdateCheckResult.Error(
                            "A release mais recente não contém JARVIS.apk."
                        )
                    )
                    return@Thread
                }

                if (
                    compareVersions(
                        tag,
                        BuildConfig.VERSION_NAME
                    ) > 0
                ) {
                    post(
                        onResult,
                        UpdateCheckResult.Available(
                            UpdateInfo(
                                versionName = tag,
                                downloadUrl = downloadUrl,
                                releasePageUrl = pageUrl
                            )
                        )
                    )
                } else {
                    post(
                        onResult,
                        UpdateCheckResult.UpToDate
                    )
                }
            } catch (e: Exception) {
                post(
                    onResult,
                    UpdateCheckResult.Error(
                        e.message ?: "Não consegui verificar atualizações."
                    )
                )
            }
        }.start()
    }

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun downloadAndInstall(
        info: UpdateInfo,
        onStatus: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                post(onStatus, "Baixando JARVIS ${info.versionName}...")

                val updateDir = File(
                    context.cacheDir,
                    "updates"
                ).apply {
                    mkdirs()
                }

                val apkFile = File(
                    updateDir,
                    "JARVIS-${info.versionName}.apk"
                )

                val connection = (
                    URL(info.downloadUrl)
                        .openConnection() as HttpURLConnection
                    ).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty(
                        "User-Agent",
                        "JARVIS-Android-Updater"
                    )
                }

                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                post(onStatus, "Download concluído. Abrindo instalador...")

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )

                val installIntent = Intent(
                    Intent.ACTION_VIEW
                ).apply {
                    setDataAndType(
                        uri,
                        "application/vnd.android.package-archive"
                    )
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }

                context.startActivity(installIntent)
            } catch (e: Exception) {
                post(
                    onError,
                    e.message ?: "Falha ao baixar a atualização."
                )
            }
        }.start()
    }

    private fun compareVersions(
        a: String,
        b: String
    ): Int {
        val pa = a.split(".").map {
            it.toIntOrNull() ?: 0
        }

        val pb = b.split(".").map {
            it.toIntOrNull() ?: 0
        }

        val size = maxOf(pa.size, pb.size)

        for (i in 0 until size) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }

            if (va != vb) return va.compareTo(vb)
        }

        return 0
    }

    private fun <T> post(
        callback: (T) -> Unit,
        value: T
    ) {
        android.os.Handler(
            android.os.Looper.getMainLooper()
        ).post {
            callback(value)
        }
    }

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/" +
                "kalilrodrigues827-crypto/" +
                "JARVIS-ANDROID/releases/latest"
    }
}

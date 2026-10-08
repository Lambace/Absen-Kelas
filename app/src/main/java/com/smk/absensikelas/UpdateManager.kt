package com.smk.absensikelas

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val activity: Activity) {

    private val repoUrl = "https://api.github.com/repos/Lambace/Absen-Kelas/releases/latest"
    private var downloadId: Long = -1
    private var onCompleteReceiver: BroadcastReceiver? = null

    fun cekUpdate(onResult: (isNew: Boolean, newVersion: String, downloadUrl: String, fileName: String) -> Unit) {
        Thread {
            try {
                val connection = URL(repoUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "AbsensiKelas-UpdateChecker")

                val code = connection.responseCode
                if (code == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val tagGithub = json.getString("tag_name").replace("v", "").replace("V", "").trim()
                    val assets = json.getJSONArray("assets")

                    if (assets.length() > 0) {
                        val asset = assets.getJSONObject(0)
                        val url = asset.getString("browser_download_url")
                        val name = asset.getString("name")

                        val pInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                        val currentVer = pInfo.versionName.replace("v", "").replace("V", "").trim()

                        activity.runOnUiThread {
                            onResult(tagGithub != currentVer, tagGithub, url, name)
                        }
                    } else {
                        showToast("Release di GitHub tidak memiliki file APK (assets kosong).")
                    }
                } else {
                    showToast("GitHub merespons kode $code. Pastikan release sudah di-PUBLISH (bukan draft).")
                }
                connection.disconnect()
            } catch (e: Exception) {
                showToast("Gagal cek update: ${e.message}")
            }
        }.start()
    }

    fun downloadDanInstall(url: String, fileName: String) {
        val folder = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.filesDir
        val fileLama = File(folder, fileName)
        if (fileLama.exists()) fileLama.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Mengunduh Update Absensi Kelas")
            .setDescription("Sedang mengunduh versi terbaru...")
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)
        Toast.makeText(activity, "Mulai mengunduh di latar belakang...", Toast.LENGTH_SHORT).show()

        onCompleteReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    try { ctx?.unregisterReceiver(this) } catch (_: Exception) {}
                    val f = ctx?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx?.filesDir
                    if (ctx != null && f != null) installApk(ctx, File(f, fileName))
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(onCompleteReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            activity.registerReceiver(onCompleteReceiver, filter)
        }
    }

    private fun installApk(ctx: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(ctx, "File APK tidak ditemukan setelah unduhan.", Toast.LENGTH_LONG).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(intent)
    }

    private fun showToast(msg: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun unregister() {
        onCompleteReceiver?.let {
            try { activity.unregisterReceiver(it) } catch (_: Exception) {}
        }
    }
}
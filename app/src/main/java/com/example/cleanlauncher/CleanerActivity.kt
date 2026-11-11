package com.example.cleanlauncher

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.widget.*

class CleanerActivity : Activity() {

    private lateinit var stats: TextView
    private val reqPerm = 1
    private val reqDelete = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 100, 40, 40)
        }
        stats = TextView(this).apply { textSize = 16f }
        col.addView(stats)
        fun btn(label: String, action: () -> Unit) = col.addView(Button(this).apply {
            text = label
            setOnClickListener { action() }
        })
        btn("Закрыть фоновые приложения") { killBackground() }
        btn("Очистить кеш лаунчера") { clearOwnCache() }
        btn("Кеш других приложений (настройки)") { openStorageSettings() }
        btn("Большие видео") { findLargeVideos() }
        setContentView(ScrollView(this).apply { addView(col) })
        refresh()
    }

    private fun refresh() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val st = StatFs(Environment.getDataDirectory().path)
        fun f(b: Long) = Formatter.formatFileSize(this, b)
        stats.text = "RAM свободно: ${f(mi.availMem)} из ${f(mi.totalMem)}\n" +
            "Хранилище свободно: ${f(st.availableBytes)} из ${f(st.totalBytes)}\n"
    }

    private fun killBackground() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .forEach { am.killBackgroundProcesses(it.packageName) }
        Handler(Looper.getMainLooper()).postDelayed({
            val after = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
            val diff = (after - before).coerceAtLeast(0)
            Toast.makeText(this, "Освобождено: ${Formatter.formatFileSize(this, diff)}", Toast.LENGTH_LONG).show()
            refresh()
        }, 1000)
    }

    private fun clearOwnCache() {
        cacheDir.deleteRecursively()
        externalCacheDir?.deleteRecursively()
        Toast.makeText(this, "Кеш лаунчера очищен", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun openStorageSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
        }
    }

    // ---- Большие видео ----
    private fun mediaPermission() =
        if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_VIDEO
        else android.Manifest.permission.READ_EXTERNAL_STORAGE

    private fun findLargeVideos() {
        if (checkSelfPermission(mediaPermission()) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(mediaPermission()), reqPerm)
            return
        }
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val items = mutableListOf<Triple<Uri, String, Long>>()
        contentResolver.query(
            uri,
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE),
            null, null, "${MediaStore.Video.Media.SIZE} DESC"
        )?.use { c ->
            while (c.moveToNext() && items.size < 20) {
                items.add(Triple(ContentUris.withAppendedId(uri, c.getLong(0)), c.getString(1), c.getLong(2)))
            }
        }
        if (items.isEmpty()) {
            Toast.makeText(this, "Видео не найдено", Toast.LENGTH_SHORT).show(); return
        }
        val labels = items.map { "${it.second} — ${Formatter.formatFileSize(this, it.third)}" }.toTypedArray()
        val checked = BooleanArray(items.size)
        AlertDialog.Builder(this)
            .setTitle("Отметьте, что удалить")
            .setMultiChoiceItems(labels, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Удалить") { _, _ ->
                deleteUris(items.filterIndexed { i, _ -> checked[i] }.map { it.first })
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 30) {
            // Система сама покажет окно подтверждения
            val pi = MediaStore.createDeleteRequest(contentResolver, uris)
            startIntentSenderForResult(pi.intentSender, reqDelete, null, 0, 0, 0)
        } else {
            try {
                uris.forEach { contentResolver.delete(it, null, null) }
                refresh()
            } catch (e: SecurityException) {
                Toast.makeText(this, "Нет прав на удаление", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == reqPerm && res.firstOrNull() == PackageManager.PERMISSION_GRANTED) findLargeVideos()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqDelete) refresh()
    }
}

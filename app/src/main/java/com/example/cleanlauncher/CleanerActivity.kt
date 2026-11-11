package com.example.cleanlauncher

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.StatFs
import android.os.UserHandle
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.view.Gravity
import android.widget.*

class CleanerActivity : Activity() {

    private lateinit var stats: TextView
    private val reqPerm = 1
    private val reqDelete = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Th.get(this)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(48), dp(16), dp(24))

        val title = TextView(this)
        title.text = "Очистка и настройки"
        title.textSize = 24f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(p.text)
        col.addView(title)

        stats = TextView(this)
        stats.textSize = 15f
        stats.setTextColor(p.text)
        stats.background = Th.round(p.card, dp(20).toFloat())
        stats.setPadding(dp(16), dp(16), dp(16), dp(16))
        stats.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        col.addView(stats)

        // Переключатель автоочистки (как на iPhone)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = Th.round(p.card, dp(20).toFloat())
        row.setPadding(dp(16), dp(14), dp(16), dp(14))
        row.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        val rowText = TextView(this)
        rowText.text = "Автоочистка каждые 15 минут\nзакрывает фоновые приложения и чистит кеш лаунчера"
        rowText.textSize = 13f
        rowText.setTextColor(p.text)
        rowText.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        val sw = Switch(this)
        sw.isChecked = Th.prefs(this).getBoolean("auto", false)
        sw.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        sw.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(p.accent, 0xFF8E8E93.toInt())
        )
        sw.setOnCheckedChangeListener { _, on ->
            Th.prefs(this).edit().putBoolean("auto", on).apply()
            if (on) AutoCleanJob.schedule(this) else AutoCleanJob.cancel(this)
            Toast.makeText(this, if (on) "Автоочистка включена" else "Автоочистка выключена", Toast.LENGTH_SHORT).show()
        }
        row.addView(rowText)
        row.addView(sw)
        col.addView(row)

        fun desc(t: String) {
            val d = TextView(this)
            d.text = t
            d.textSize = 12f
            d.setTextColor(p.sub)
            d.setPadding(dp(8), dp(4), dp(8), 0)
            col.addView(d)
        }

        col.addView(pill("⚡ Закрыть фоновые приложения", p) { killBackground() })
        desc("Освобождает оперативную память прямо сейчас.")
        col.addView(pill("🧹 Очистить кеш лаунчера", p) { clearOwnCache() })
        desc("Стирает временные файлы самого лаунчера.")
        col.addView(pill("📦 Кеш моих приложений", p) { showCacheList() })
        desc("Список приложений по размеру кеша. Нажми на приложение, затем «Очистить кеш».")
        col.addView(pill("🎬 Большие видео", p) { findLargeVideos() })
        desc("20 самых больших видео. Отметь и удали.")
        col.addView(pill("🎨 Тема: ${Th.names[Th.idx(this)]}", p, false) { pickTheme() })
        desc("Тёмная, светлая или неон.")

        val scroll = ScrollView(this)
        scroll.background = Th.bg(this)
        scroll.addView(col)
        setContentView(scroll)
        refresh()
    }

    private fun refresh() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val st = StatFs(Environment.getDataDirectory().path)
        fun f(b: Long) = Formatter.formatFileSize(this, b)
        stats.text = "🧠 Память (RAM): свободно ${f(mi.availMem)} из ${f(mi.totalMem)}\n\n" +
            "💾 Хранилище: свободно ${f(st.availableBytes)} из ${f(st.totalBytes)}"
    }

    private fun pickTheme() {
        AlertDialog.Builder(this)
            .setTitle("Выбери тему")
            .setSingleChoiceItems(Th.names.toTypedArray(), Th.idx(this)) { d, i ->
                Th.prefs(this).edit().putInt("theme", i).apply()
                d.dismiss()
                recreate()
            }
            .show()
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

    // ---- Кеш приложений ----
    private fun hasUsage(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        else
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showCacheList() {
        if (!hasUsage()) {
            AlertDialog.Builder(this)
                .setTitle("Нужен доступ")
                .setMessage("Чтобы узнать размер кеша, включи для CleanLauncher «Доступ к статистике использования» и вернись назад.")
                .setPositiveButton("Открыть настройки") { _, _ ->
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton("Отмена", null)
                .show()
            return
        }
        Toast.makeText(this, "Считаю кеш…", Toast.LENGTH_SHORT).show()
        Thread {
            val ssm = getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            val pm = packageManager
            val list = pm.getInstalledApplications(0).mapNotNull { ai ->
                try {
                    val s = ssm.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT, ai.packageName, UserHandle.getUserHandleForUid(ai.uid)
                    )
                    Triple(ai.packageName, pm.getApplicationLabel(ai).toString(), s.cacheBytes)
                } catch (e: Exception) {
                    null
                }
            }.sortedByDescending { it.third }.take(30)
            runOnUiThread {
                if (list.isEmpty()) {
                    Toast.makeText(this, "Не удалось получить список", Toast.LENGTH_LONG).show()
                } else {
                    val labels = list.map { "${it.second} — ${Formatter.formatFileSize(this, it.third)}" }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Кеш приложений")
                        .setItems(labels) { _, i ->
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${list[i].first}"))
                            )
                        }
                        .show()
                }
            }
        }.start()
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
            .setTitle("Отметь, что удалить")
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

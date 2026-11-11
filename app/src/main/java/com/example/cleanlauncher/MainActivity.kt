package com.example.cleanlauncher

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class P(val bg: Int, val card: Int, val text: Int, val sub: Int, val accent: Int, val bg2: Int)

object Th {
    val names = listOf("Тёмная", "Светлая", "Неон")
    private val all = listOf(
        P(0xFF0B0B12.toInt(), 0xFF1C1C28.toInt(), 0xFFFFFFFF.toInt(), 0xFF9A9AB0.toInt(), 0xFF6C5CE7.toInt(), 0xFF171726.toInt()),
        P(0xFFF2F2F7.toInt(), 0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFF6E6E73.toInt(), 0xFF007AFF.toInt(), 0xFFE5E5EA.toInt()),
        P(0xFF12002B.toInt(), 0xFF26104A.toInt(), 0xFFFFFFFF.toInt(), 0xFFC4A8FF.toInt(), 0xFFFF2D95.toInt(), 0xFF05001A.toInt())
    )
    fun prefs(c: Context) = c.getSharedPreferences("cl", Context.MODE_PRIVATE)
    fun idx(c: Context) = prefs(c).getInt("theme", 0)
    fun get(c: Context) = all[idx(c)]
    fun bg(c: Context) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(get(c).bg2, get(c).bg))
    fun round(color: Int, r: Float) = GradientDrawable().apply { setColor(color); cornerRadius = r }
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.pill(label: String, p: P, filled: Boolean = true, onClick: () -> Unit): TextView {
    val t = TextView(this)
    t.text = label
    t.textSize = 16f
    t.gravity = Gravity.CENTER
    t.setTextColor(if (filled) Color.WHITE else p.text)
    t.background = Th.round(if (filled) p.accent else p.card, dp(20).toFloat())
    t.setPadding(dp(16), dp(14), dp(16), dp(14))
    t.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
    t.setOnClickListener { onClick() }
    return t
}

class MainActivity : Activity() {

    private var apps: List<ResolveInfo> = emptyList()

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val p = Th.get(this)
        val pm = packageManager
        apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = Th.bg(this)
        root.setPadding(dp(16), dp(48), dp(16), dp(16))

        val title = TextView(this)
        title.text = "CleanLauncher"
        title.textSize = 26f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(p.text)
        root.addView(title)

        val hint = TextView(this)
        hint.text = "Нажми на иконку — откроется. Удерживай — меню приложения."
        hint.textSize = 13f
        hint.setTextColor(p.sub)
        hint.setPadding(0, dp(4), 0, dp(8))
        root.addView(hint)

        root.addView(pill("🧹 Очистка и настройки", p) {
            startActivity(Intent(this, CleanerActivity::class.java))
        })

        val grid = GridView(this)
        grid.numColumns = 4
        grid.verticalSpacing = dp(16)
        grid.setPadding(0, dp(16), 0, 0)
        grid.setSelector(ColorDrawable(Color.TRANSPARENT))
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(i: Int) = apps[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, parent: ViewGroup): View {
                val info = apps[i]
                val cell = LinearLayout(this@MainActivity)
                cell.orientation = LinearLayout.VERTICAL
                cell.gravity = Gravity.CENTER
                val icon = ImageView(this@MainActivity)
                icon.setImageDrawable(info.loadIcon(pm))
                icon.layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
                val label = TextView(this@MainActivity)
                label.text = info.loadLabel(pm)
                label.setTextColor(p.text)
                label.textSize = 11f
                label.maxLines = 1
                label.gravity = Gravity.CENTER
                label.setPadding(0, dp(4), 0, 0)
                cell.addView(icon)
                cell.addView(label)
                return cell
            }
        }
        grid.setOnItemClickListener { _, _, pos, _ ->
            pm.getLaunchIntentForPackage(apps[pos].activityInfo.packageName)?.let { startActivity(it) }
        }
        grid.setOnItemLongClickListener { _, _, pos, _ -> menu(apps[pos]); true }
        root.addView(grid)
        setContentView(root)
    }

    private fun menu(info: ResolveInfo) {
        val pkg = info.activityInfo.packageName
        val name = info.loadLabel(packageManager).toString()
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(arrayOf("Закрыть в фоне", "Кеш и данные (настройки)", "Удалить приложение")) { _, i ->
                when (i) {
                    0 -> {
                        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg)
                        Toast.makeText(this, "Закрыто: $name", Toast.LENGTH_SHORT).show()
                    }
                    1 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
                    2 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
                }
            }
            .show()
    }

    // Лаунчер не должен закрываться кнопкой «Назад»
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}
}

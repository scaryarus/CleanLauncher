package com.example.cleanlauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pm = packageManager
        val apps: List<ResolveInfo> = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 80, 24, 24)
        }
        val cleanBtn = Button(this).apply {
            text = "🧹 Очистка памяти"
            setOnClickListener { startActivity(Intent(this@MainActivity, CleanerActivity::class.java)) }
        }
        val grid = GridView(this).apply {
            numColumns = 4
            verticalSpacing = 24
            adapter = object : BaseAdapter() {
                override fun getCount() = apps.size
                override fun getItem(p: Int) = apps[p]
                override fun getItemId(p: Int) = p.toLong()
                override fun getView(p: Int, v: View?, parent: ViewGroup): View {
                    val info = apps[p]
                    val cell = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                    }
                    cell.addView(ImageView(this@MainActivity).apply {
                        setImageDrawable(info.loadIcon(pm))
                        layoutParams = LinearLayout.LayoutParams(132, 132)
                    })
                    cell.addView(TextView(this@MainActivity).apply {
                        text = info.loadLabel(pm)
                        setTextColor(Color.WHITE)
                        textSize = 11f
                        maxLines = 1
                        gravity = Gravity.CENTER
                    })
                    return cell
                }
            }
            setOnItemClickListener { _, _, pos, _ ->
                pm.getLaunchIntentForPackage(apps[pos].activityInfo.packageName)?.let { startActivity(it) }
            }
        }
        root.addView(cleanBtn)
        root.addView(grid)
        setContentView(root)
    }

    // Лаунчер не должен закрываться кнопкой «Назад»
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}
}

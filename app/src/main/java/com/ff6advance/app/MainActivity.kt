package com.ff6advance.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private val games = ArrayList<File>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var empty: TextView
    private val romDir by lazy { File(filesDir, "roms").apply { mkdirs() } }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply {
            text = "FF6 Advance"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        val importBtn = Button(this).apply {
            text = "Import Game"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }, 1)
            }
        }
        empty = TextView(this).apply {
            text = "Belum ada game. Tekan Import Game lalu pilih file .gba"
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        val list = ListView(this).apply {
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, pos, _ ->
                startActivity(Intent(this@MainActivity, GameActivity::class.java)
                    .putExtra("rom", games[pos].absolutePath))
            }
            setOnItemLongClickListener { _, _, pos, _ ->
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("Hapus game ini?")
                    .setPositiveButton("Hapus") { _, _ ->
                        val f = games[pos]
                        File(filesDir, "saves/" + f.nameWithoutExtension + ".sav").delete()
                        f.delete()
                        refresh()
                    }
                    .setNegativeButton("Batal", null).show()
                true
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(16), dp(24), dp(16), dp(16))
            addView(title)
            addView(importBtn)
            addView(empty)
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        games.clear()
        romDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name.lowercase() }?.let { games.addAll(it) }
        adapter.clear()
        adapter.addAll(games.map { it.nameWithoutExtension })
        empty.visibility = if (games.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) data?.data?.let { importRom(it) }
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        }

    private fun importRom(uri: Uri) {
        Thread {
            try {
                val safe = (displayName(uri) ?: "game.gba").replace(Regex("[^A-Za-z0-9._ -]"), "_")
                val name = if (safe.contains('.')) safe else "$safe.gba"
                val out = File(romDir, name)
                contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                runOnUiThread {
                    refresh()
                    Toast.makeText(this, "Game diimpor: $name", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Gagal impor: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}

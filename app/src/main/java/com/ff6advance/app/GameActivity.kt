package com.ff6advance.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import java.io.File
import java.nio.ShortBuffer

class GameActivity : Activity() {
    @Volatile private var running = true
    @Volatile private var paused = false
    @Volatile private var pendingSave = false
    private var thread: Thread? = null
    private lateinit var gameView: GameView
    private lateinit var romPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        romPath = intent.getStringExtra("rom") ?: run { finish(); return }

        gameView = GameView(this)
        val controls = ControlsView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(gameView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        hideBars()
        thread = Thread { loop() }.also { it.start() }
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onPause() { super.onPause(); paused = true; pendingSave = true; Native.setPad(0) }
    override fun onResume() { super.onResume(); paused = false }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        thread?.join(3000)
    }

    private fun loop() {
        val sys = File(filesDir, "system").apply { mkdirs() }.absolutePath
        if (!Native.start(romPath, sys)) {
            runOnUiThread { Toast.makeText(this, "Gagal memuat game", Toast.LENGTH_LONG).show(); finish() }
            return
        }
        val saveDir = File(filesDir, "saves").apply { mkdirs() }
        val sav = File(saveDir, File(romPath).nameWithoutExtension + ".sav")
        if (sav.exists()) Native.loadSram(sav.absolutePath)

        val rate = Native.info()[0].toInt()
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()

        val frameBuf = ShortArray(512 * 512)
        val audioBuf = ShortArray(16384)
        var bmp: Bitmap? = null
        var wasPaused = false
        var frames = 0

        while (running) {
            if (paused) {
                if (!wasPaused) { track.pause(); wasPaused = true }
                if (pendingSave) { Native.saveSram(sav.absolutePath); pendingSave = false }
                Thread.sleep(50)
                continue
            }
            if (wasPaused) { track.play(); wasPaused = false }

            Native.runFrame()

            val wh = Native.frame(frameBuf)
            val w = wh ushr 16
            val h = wh and 0xFFFF
            if (w > 0 && h > 0) {
                var bm = bmp
                if (bm == null || bm.width != w || bm.height != h) {
                    bm = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                    bmp = bm
                    gameView.bitmap = bm
                }
                bm.copyPixelsFromBuffer(ShortBuffer.wrap(frameBuf))
                gameView.postInvalidate()
            }

            val n = Native.audio(audioBuf)
            if (n > 0) track.write(audioBuf, 0, n)

            frames++
            if (frames % 600 == 0) Native.saveSram(sav.absolutePath)
        }
        Native.saveSram(sav.absolutePath)
        track.release()
        Native.stop()
    }
}

package com.ff6advance.app

object Native {
    init { System.loadLibrary("frontend") }
    external fun start(romPath: String, sysDir: String): Boolean
    external fun info(): DoubleArray
    external fun runFrame()
    external fun frame(out: ShortArray): Int
    external fun audio(out: ShortArray): Int
    external fun setPad(mask: Int)
    external fun loadSram(path: String): Boolean
    external fun saveSram(path: String): Boolean
    external fun stop()
}

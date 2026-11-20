package com.multifb.profiles.engine

import java.io.File
import top.niunaijun.blackbox.core.env.BEnvironment

/**
 * Menghapus cache saja. Jangan mengganti fungsi ini dengan clearPackage():
 * clearPackage menghapus seluruh data paket termasuk database dan sesi login.
 */
object ProfileCacheCleaner {
    private const val FACEBOOK_PACKAGE = "com.facebook.katana"

    fun clear(userId: Int) {
        val internalData = BEnvironment.getDataDir(FACEBOOK_PACKAGE, userId)
        clearDirectoryContents(File(internalData, "cache"))
        clearDirectoryContents(File(internalData, "code_cache"))

        // Cache eksternal aplikasi virtual, bila dibuat Facebook.
        clearDirectoryContents(BEnvironment.getExternalDataCacheDir(FACEBOOK_PACKAGE, userId))
    }

    private fun clearDirectoryContents(directory: File) {
        if (!directory.exists() && !directory.mkdirs()) return
        directory.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                child.deleteRecursively()
            } else {
                child.delete()
            }
        }
        directory.mkdirs()
    }
}

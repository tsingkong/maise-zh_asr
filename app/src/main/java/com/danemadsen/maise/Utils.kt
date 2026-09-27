package com.danemadsen.maise

import android.content.Context
import java.io.File

/** Copy an asset to the app's files directory (cached; not re-copied on subsequent runs). */
fun copyAssetToFile(context: Context, assetPath: String): File {
    val dest = File(context.filesDir, assetPath)
    if (!dest.exists()) {
        dest.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
    }
    return dest
}


/**
 * 将 assets 中的文件复制到内部存储，返回文件路径
 */
fun copyAssetToFileReturnString(context: Context, filename: String): String {
    val outFile = java.io.File(context.filesDir, filename)
    if (outFile.exists() && outFile.length() > 0) return outFile.absolutePath

    context.assets.open(filename).use { input ->
        java.io.FileOutputStream(outFile).use { output ->
            input.copyTo(output)
        }
    }
    return outFile.absolutePath
}

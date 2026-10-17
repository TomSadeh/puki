package io.github.tomsadeh.puki.ui.canvas

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves a drawing (a PNG) where the family finds it: the gallery's "Puki" album (Pictures/Puki).
 * Android 10 and up need no permission for that. Before 10, a gallery file would need storage
 * permission, so it goes to Puki's own pictures folder instead (Android/data/…/files/Pictures).
 * Nothing leaves the tablet.
 */
fun saveDrawing(context: Context, png: ByteArray): Boolean {
    val name = "puki_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date()) + ".png"
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Puki")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { it.write(png) } ?: return false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return false
            File(dir, name).writeBytes(png)
        }
        true
    } catch (e: Exception) {
        Log.w("Puki", "couldn't save the drawing", e)
        false
    }
}

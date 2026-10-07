package com.sukoon.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** Meal photos for the carb estimate: where the camera writes them, and how they're shrunk for upload. */
object MealPhoto {

    /** Fresh cache file for the camera to write into, exposed through the manifest's FileProvider. */
    fun newCaptureFile(context: Context): File =
        File(context.cacheDir, "photos").apply { mkdirs() }.resolve("meal.jpg")

    /**
     * Decodes [uri] at ≤[MAX_EDGE]px on the long edge and re-encodes as JPEG — a phone photo is
     * 3–8 MB, this is ~150 KB, and food recognition doesn't need more.
     */
    fun loadScaledJpeg(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        // Power-of-2 subsampling while decoding (cheap on memory), then an exact scale to MAX_EDGE.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw AiException("Couldn't read that photo.")
        val scale = MAX_EDGE.toFloat() / max(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
    }

    private const val MAX_EDGE = 1024
}

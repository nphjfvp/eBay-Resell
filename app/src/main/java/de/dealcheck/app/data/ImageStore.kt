package de.dealcheck.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Skaliert eingehende Bilder auf max. 1600 px (längste Seite), dreht sie gemäß EXIF
 * und speichert sie als JPEG im privaten App-Speicher. Diese Kopie wird an die API
 * geschickt und dient als Vorschaubild im Verlauf.
 */
class ImageStore(private val context: Context) {

    private val dir = File(context.filesDir, "images").apply { mkdirs() }

    suspend fun importImage(uri: Uri): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IllegalArgumentException("Bild konnte nicht geöffnet werden")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("Datei ist kein lesbares Bild")
        }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIZE) sample *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Bild konnte nicht gelesen werden")

        val rotation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (_: Exception) {
            0
        }

        val scaled = scaleAndRotate(decoded, rotation)
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (scaled !== decoded) decoded.recycle()
        scaled.recycle()
        file
    }

    private fun scaleAndRotate(src: Bitmap, rotation: Int): Bitmap {
        val longest = max(src.width, src.height)
        val factor = if (longest > MAX_SIZE) MAX_SIZE.toFloat() / longest else 1f
        if (factor == 1f && rotation == 0) return src
        val matrix = Matrix().apply {
            if (factor != 1f) postScale(factor, factor)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    suspend fun toBase64(path: String): String = withContext(Dispatchers.IO) {
        Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP)
    }

    fun delete(paths: List<String>) {
        paths.forEach { File(it).delete() }
    }

    /**
     * Löscht gespeicherte Bilder, die zu keiner Analyse im Verlauf gehören.
     * Nur ältere Dateien, damit gerade importierte Bilder nicht verschwinden.
     */
    suspend fun deleteOrphans(keep: Set<String>) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - ORPHAN_AGE_MS
        dir.listFiles()?.forEach {
            if (it.absolutePath !in keep && it.lastModified() < cutoff) it.delete()
        }
        File(context.cacheDir, "camera").listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    /** Neue Datei für die Kamera-App (Zwischenablage im Cache, per FileProvider freigegeben). */
    fun newCameraFile(): File {
        val camDir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(camDir, "photo_${System.currentTimeMillis()}.jpg")
    }

    companion object {
        const val MAX_SIZE = 1600
        const val JPEG_QUALITY = 85
        private const val ORPHAN_AGE_MS = 24 * 60 * 60 * 1000L
    }
}

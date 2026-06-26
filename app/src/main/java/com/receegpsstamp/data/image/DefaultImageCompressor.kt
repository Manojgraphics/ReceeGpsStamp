package com.receegpsstamp.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Default [ImageCompressor] — downsamples + scales + uprights a photo, one at a time, low memory. */
@Singleton
class DefaultImageCompressor @Inject constructor() : ImageCompressor {

    // Produces a scaled, upright bitmap; re-creating the bitmap also strips all metadata (privacy).
    override fun prepareImageForPdf(sourceFile: File, maxLongestSidePx: Int): Result<Bitmap> = try {
        val rotationDegrees = readOrientationDegrees(sourceFile)                  // EXIF angle first (0 if already stripped)
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOptions)         // read dimensions only
        val sampleSize = calculateSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, maxLongestSidePx)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val sampledBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
            ?: return Result.failure(IllegalStateException("Bitmap decode returned null for ${sourceFile.name}"))
        val uprightBitmap = applyRotation(sampledBitmap, rotationDegrees)        // bake rotation into the pixels
        Result.success(scaleToMaxSide(uprightBitmap, maxLongestSidePx))         // fine-scale to the exact target
    } catch (error: Exception) {
        Result.failure(error)
    }

    // Reads the EXIF orientation tag and returns how many degrees the pixels must rotate (0 if none).
    private fun readOrientationDegrees(sourceFile: File): Int = try {
        when (ExifInterface(sourceFile.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) { 0 }

    // Physically rotates a bitmap so it no longer depends on any EXIF tag to look correct.
    private fun applyRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    // Scales a bitmap so its longest edge equals the target, keeping aspect ratio.
    private fun scaleToMaxSide(bitmap: Bitmap, maxLongestSidePx: Int): Bitmap {
        val longestSide = maxOf(bitmap.width, bitmap.height)
        if (longestSide <= maxLongestSidePx) return bitmap
        val scale = maxLongestSidePx.toFloat() / longestSide
        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    // Returns the largest power-of-2 sample size that keeps the image at/above the target (cheap prepass).
    private fun calculateSampleSize(width: Int, height: Int, maxLongestSidePx: Int): Int {
        var sampleSize = 1
        val longestSide = maxOf(width, height)
        while (maxLongestSidePx > 0 && longestSide / (sampleSize * 2) >= maxLongestSidePx) sampleSize *= 2
        return sampleSize
    }
}

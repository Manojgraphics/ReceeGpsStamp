package com.receegpsstamp.data.image

import android.graphics.Bitmap
import java.io.File

/** Prepares a saved (already-watermarked) photo for a PDF: a scaled-down, upright bitmap with metadata stripped. */
interface ImageCompressor {
    /** Returns a bitmap whose longest edge is [maxLongestSidePx] and whose pixels are upright. Caller recycles it. */
    fun prepareImageForPdf(sourceFile: File, maxLongestSidePx: Int = 2048): Result<Bitmap>
}

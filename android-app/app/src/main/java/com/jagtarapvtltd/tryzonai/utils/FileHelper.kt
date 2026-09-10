package com.jagtarapvtltd.tryzonai.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileHelper {
    /**
     * Converts a Uri to a compressed, downscaled File object.
     * Scale to maxDimension (default 2048px) and compress as JPEG at 85% quality.
     * Prevents high-resolution camera photos (20-30MB) from causing HTTP 413 errors.
     */
    fun uriToFile(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048,
        quality: Int = 90
    ): File? {
        val fileName = "temp_image_${System.currentTimeMillis()}.jpg"
        val tempDir = File(context.filesDir, "tryon_temp").apply { mkdirs() }
        val file = File(tempDir, fileName)

        return try {
            // Check raw file size
            val rawSizeBytes = try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            } catch (e: Exception) { -1L }

            // First decode bounds to get original dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            openInputStreamForUri(context, uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                // Fallback to raw copy if bounds decode failed
                return rawCopyToFile(context, uri, file)
            }

            // SMART THRESHOLD:
            // If image is already normal size (<= 2048px and <= 3.5MB),
            // preserve 100% original pixel quality via raw copy with ZERO re-compression!
            val isSmallDimensions = options.outWidth <= maxDimension && options.outHeight <= maxDimension
            val isSmallFileSize = rawSizeBytes in 1..(3500 * 1024)

            if (isSmallDimensions && isSmallFileSize) {
                return rawCopyToFile(context, uri, file)
            }

            // Calculate sample size for downscaling giant photos (e.g. 20MB+ camera shots)
            var sampleSize = 1
            var width = options.outWidth
            var height = options.outHeight
            while (width / 2 >= maxDimension || height / 2 >= maxDimension) {
                width /= 2
                height /= 2
                sampleSize *= 2
            }

            // Decode actual bitmap with calculated sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            var bitmap: Bitmap? = openInputStreamForUri(context, uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return rawCopyToFile(context, uri, file)

            // Check EXIF rotation if supported
            bitmap = rotateBitmapIfNeeded(context, uri, bitmap)

            // Write compressed JPEG to cache file
            FileOutputStream(file).use { out ->
                bitmap?.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }

            // Clean up bitmap memory
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback copy on any error
            rawCopyToFile(context, uri, file)
        }
    }

    fun openInputStreamForUri(context: Context, uri: Uri): InputStream? {
        val uriString = uri.toString()
        return try {
            if (uriString.startsWith("res:")) {
                val resId = uriString.substringAfter("res:").toIntOrNull()
                if (resId != null && resId != 0) {
                    context.resources.openRawResource(resId)
                } else null
            } else if (uri.scheme == "android.resource") {
                val resId = uri.lastPathSegment?.toIntOrNull()
                if (resId != null && resId != 0) {
                    context.resources.openRawResource(resId)
                } else {
                    context.contentResolver.openInputStream(uri)
                }
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun rotateBitmapIfNeeded(context: Context, uri: Uri, bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null
        return try {
            openInputStreamForUri(context, uri)?.use { input ->
                val exif = ExifInterface(input)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    else -> return bitmap
                }
                val rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotated != bitmap && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
                rotated
            } ?: bitmap
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun rawCopyToFile(context: Context, uri: Uri, file: File): File? {
        return try {
            val inputStream: InputStream? = openInputStreamForUri(context, uri)
            val outputStream = FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes temporary files in cache.
     */
    fun clearCache(context: Context) {
        val cacheDir = context.cacheDir
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("temp_image_")) {
                file.delete()
            }
        }
    }
}

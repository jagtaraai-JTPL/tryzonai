package com.jagtarapvtltd.tryzonai.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object ImageExportHelper {

    private val sharedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun getInputStreamForUrl(context: Context, url: String): java.io.InputStream? {
        val fullUrl = UrlUtils.getFullUrl(url)
        return try {
            if (fullUrl.startsWith("res:") || fullUrl.startsWith("android.resource://") || fullUrl.startsWith("file://")) {
                FileHelper.openInputStreamForUri(context, android.net.Uri.parse(fullUrl))
            } else {
                val request = Request.Builder().url(fullUrl).build()
                val response = sharedOkHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body.byteStream()
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun downloadImageToGallery(context: Context, url: String) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = getInputStreamForUrl(context, url)
                if (inputStream != null) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "TryZon_Result_${System.currentTimeMillis()}.jpg")
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TryZon")
                    }
                    
                    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            inputStream.use { input ->
                                input.copyTo(output)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Image saved to Gallery (Pictures/TryZon)", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to create media file", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to fetch image", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    suspend fun shareImage(context: Context, url: String) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream = getInputStreamForUrl(context, url)
                if (inputStream != null) {
                    val file = File(context.cacheDir, "shared_tryon_result.jpg")
                    inputStream.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "Check out my new look styled by TryZon AI! ✨\n\nDownload the app to try your own outfits:\nhttps://play.google.com/store/apps/details?id=com.jagtarapvtltd.tryzonai")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    withContext(Dispatchers.Main) {
                        val chooser = Intent.createChooser(shareIntent, "Share Look via")
                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to fetch image for sharing", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to share image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

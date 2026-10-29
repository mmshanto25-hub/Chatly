package com.example.chat.core.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL

object CloudinaryUploader {
    suspend fun uploadToCloudinary(
        context: Context,
        imageUri: Uri,
        cloudName: String,
        uploadPreset: String,
        onProgress: ((Int) -> Unit)? = null
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val uriString = imageUri.toString()
                val mimeType = contentResolver.getType(imageUri) ?: when {
                    uriString.endsWith(".m4a", true) -> "audio/mp4"
                    uriString.endsWith(".3gp", true) -> "audio/3gpp"
                    uriString.endsWith(".mp3", true) -> "audio/mpeg"
                    uriString.endsWith(".mp4", true) -> "video/mp4"
                    uriString.endsWith(".jpg", true) || uriString.endsWith(".png", true) -> "image/jpeg"
                    else -> "application/octet-stream"
                }

                val extension = when {
                    mimeType.startsWith("image/") -> "jpg"
                    mimeType.startsWith("video/") -> "mp4"
                    mimeType.startsWith("audio/") -> "m4a"
                    else -> "bin"
                }
                val fileName = "upload_${System.currentTimeMillis()}.$extension"

                val inputStream = contentResolver.openInputStream(imageUri) ?: return@withContext null
                val bytes = inputStream.readBytes()
                inputStream.close()
                val totalBytes = bytes.size.toLong()

                val boundary = "Boundary-${System.currentTimeMillis()}"
                val url = URL("https://api.cloudinary.com/v1_1/$cloudName/auto/upload")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                val outputStream = connection.outputStream
                val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)

                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n")
                writer.append("$uploadPreset\r\n")

                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n")
                writer.append("Content-Type: $mimeType\r\n\r\n")
                writer.flush()

                val bufferSize = 4096
                var totalWritten = 0L
                val bis = bytes.inputStream()
                val bos = outputStream
                val buffer = ByteArray(bufferSize)
                var read: Int
                while (bis.read(buffer).also { read = it } != -1) {
                    bos.write(buffer, 0, read)
                    totalWritten += read
                    if (totalBytes > 0 && onProgress != null) {
                        val percent: Int = ((totalWritten * 100) / totalBytes).toInt()
                        onProgress(percent.coerceIn(0, 100))
                    }
                }
                bos.flush()
                bis.close()

                writer.append("\r\n--$boundary--\r\n")
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val urlRegex = """"secure_url"\s*:\s*"([^"]+)"""".toRegex()
                    val matchResult = urlRegex.find(response)
                    matchResult?.groupValues?.get(1)?.replace("\\/", "/")
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

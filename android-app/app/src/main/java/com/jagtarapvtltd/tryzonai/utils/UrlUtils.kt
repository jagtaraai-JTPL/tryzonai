package com.jagtarapvtltd.tryzonai.utils

object UrlUtils {
    private const val DOMAIN = "https://api.tryzonai.com"

    fun getFullUrl(path: String?): String {
        if (path == null) return ""
        var formattedPath = path
        if (formattedPath.contains("images.unsplash.com") && !formattedPath.contains("w=")) {
            formattedPath = "$formattedPath?w=400&q=80&fm=webp"
        }
        if (formattedPath.startsWith("http") || formattedPath.startsWith("android.resource://") || formattedPath.startsWith("file://") || formattedPath.startsWith("res:")) return formattedPath
        val normalizedPath = if (formattedPath.startsWith("/")) formattedPath else "/$formattedPath"
        return "$DOMAIN$normalizedPath"
    }

    fun getCoilModel(path: String?): Any? {
        if (path != null && path.startsWith("res:")) {
            return path.removePrefix("res:").toIntOrNull()
        }
        val url = getFullUrl(path)
        if (url.startsWith("android.resource://")) {
            return android.net.Uri.parse(url)
        }
        return url
    }
}
